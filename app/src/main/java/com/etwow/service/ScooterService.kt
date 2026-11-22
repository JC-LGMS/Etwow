package com.etwow.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.etwow.MainActivity
import com.etwow.R
import com.etwow.ble.BleScooterTransport
import com.etwow.ble.ScooterTransport
import com.etwow.ble.SimulatedScooterTransport
import com.etwow.data.Connection
import com.etwow.data.ScooterBus
import com.etwow.data.ScooterSnapshot
import com.etwow.data.Settings
import com.etwow.data.TripMeter
import com.etwow.protocol.EtwowProtocol
import com.etwow.protocol.SpeedModes
import com.etwow.protocol.hex
import com.etwow.solar.AutoLight
import com.etwow.solar.SolarTimes
import com.etwow.widget.SpeedLimitWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Service de premier plan : propriétaire unique de la connexion, du phare automatique et des
 * actions du widget. L'UI et le widget ne parlent jamais au BLE directement.
 */
class ScooterService : Service(), ScooterTransport.Listener {

    companion object {
        private const val CHANNEL_ID = "scooter_v2"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.etwow.action.START"
        const val ACTION_STOP = "com.etwow.action.STOP"
        const val ACTION_RECONNECT = "com.etwow.action.RECONNECT"
        const val ACTION_LOCK = "com.etwow.action.LOCK"
        const val ACTION_UNLOCK = "com.etwow.action.UNLOCK"
        const val ACTION_LIGHT_ON = "com.etwow.action.LIGHT_ON"
        const val ACTION_LIGHT_OFF = "com.etwow.action.LIGHT_OFF"
        const val ACTION_SET_MODE = "com.etwow.action.SET_MODE"
        const val ACTION_SET_25 = "com.etwow.action.SET_25"
        const val EXTRA_MODE = "mode"

        fun start(context: Context) = send(context, ACTION_START)

        fun send(context: Context, action: String, mode: Int? = null) {
            val intent = Intent(context, ScooterService::class.java).setAction(action)
            if (mode != null) intent.putExtra(EXTRA_MODE, mode)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private lateinit var settings: Settings
    private var transport: ScooterTransport? = null
    private var pending: Pending? = null
    private var pendingJob: Job? = null
    private var notificationKey = ""
    private var widgetKey = ""
    private var foregroundBlocked = false

    // Override manuel du phare : suspendu jusqu'à la prochaine transition calculée.
    private var lightOverride = false
    private var lightOverrideDesired = false

    // Trip : la trottinette ne le transmet pas ; on cumule les écarts d'odomètre, comme l'app
    // officielle. lastOdoKm repart à zéro à chaque connexion pour ne pas compter le trajet hors ligne.
    private var lastOdoKm: Double? = null

    // Nouveau protocole (trames de 20 octets) : décidé par le nom de l'appareil, ou dès la
    // première trame reçue qui en porte la signature.
    private var newProtocol = false

    private data class Pending(val label: String, val matches: (ScooterSnapshot) -> Boolean)

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        createChannel()
        if (!startForegroundNotification()) {
            // Sans BLUETOOTH_CONNECT/SCAN accordé, un FGS connectedDevice tue le processus :
            // on s'arrête proprement et l'UI invite à autoriser.
            foregroundBlocked = true
            stopSelf()
            return
        }
        ScooterBus.update {
            it.copy(simulator = settings.simulator, autoLight = settings.autoLight)
        }
        scope.launch {
            while (isActive) {
                applyAutoLight()
                delay(60_000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (foregroundBlocked) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_STOP -> {
                stopTransport()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_RECONNECT -> restart()

            ACTION_LOCK -> lock()
            ACTION_UNLOCK -> command(unlockFrame(), "déverrouillage") { it.locked == false }

            ACTION_LIGHT_ON -> manualLight(on = true)
            ACTION_LIGHT_OFF -> manualLight(on = false)

            ACTION_SET_MODE -> {
                val mode = intent.getIntExtra(EXTRA_MODE, -1)
                if (mode in SpeedModes.all) setMode(mode)
            }

            ACTION_SET_25 -> set25()

            else -> if (transport == null) restart()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopTransport()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- liaison -------------------------------------------------------------------------

    private fun restart() {
        stopTransport()
        val simulated = settings.simulator
        ScooterBus.update {
            it.copy(
                simulator = simulated,
                autoLight = settings.autoLight,
                connection = Connection.Connecting,
            )
        }
        ScooterBus.log(if (simulated) "mode simulateur" else "mode Bluetooth")
        transport = if (simulated) {
            SimulatedScooterTransport(this)
        } else {
            BleScooterTransport(applicationContext, this)
        }.also { it.start() }
        render()
    }

    private fun stopTransport() {
        transport?.stop()
        transport = null
        pending = null
        ScooterBus.update { it.copy(pending = null, connection = Connection.Idle) }
        render()
    }

    override fun onConnection(state: Connection, name: String?, address: String?) {
        if (state != Connection.Connected) lastOdoKm = null
        if (state == Connection.Connected && name != null) {
            newProtocol = EtwowProtocol.NEW_PROTOCOL_NAMES.any { name.startsWith(it) }
        }
        ScooterBus.update {
            it.copy(
                connection = state,
                deviceName = name ?: it.deviceName,
                deviceAddress = address ?: it.deviceAddress,
                speedKmh = if (state == Connection.Connected) it.speedKmh else null,
            )
        }
        render()
        if (state == Connection.Connected) applyAutoLight()
    }

    override fun onNotification(bytes: ByteArray) {
        val telemetry = EtwowProtocol.decode(bytes)
        if (telemetry == null) {
            ScooterBus.log("← ${bytes.hex()} (ignorée)")
            return
        }
        // Journal brut : c'est la seule façon de corriger un décodage faux sur la vraie machine.
        ScooterBus.log("← ${bytes.hex()}")
        if (EtwowProtocol.isNewFrame(bytes)) newProtocol = true
        val odo = telemetry.odoKm
        ScooterBus.update { snap ->
            snap.copy(
                speedKmh = telemetry.speedKmh ?: snap.speedKmh,
                batteryPercent = telemetry.batteryPercent ?: snap.batteryPercent,
                odoKm = telemetry.odoKm ?: snap.odoKm,
                tripKm = telemetry.tripKm ?: if (odo != null) updateTrip(odo) else snap.tripKm,
                statusByte = telemetry.status ?: snap.statusByte,
                locked = telemetry.locked ?: snap.locked,
                lightOn = telemetry.lightOn ?: snap.lightOn,
                mode = telemetry.mode ?: snap.mode,
            )
        }
        checkPending()
        render()
        // Une trame de statut arrive à la connexion et à chaque changement : c'est le bon moment
        // pour appliquer le phare automatique sans attendre le tick d'une minute.
        if (telemetry.status != null) applyAutoLight()
    }

    override fun onLog(line: String) {
        ScooterBus.log(line)
        render()
    }

    /** Trip = somme des écarts d'odomètre, persistée ; voir [TripMeter]. */
    private fun updateTrip(odo: Double): Double {
        val previous = lastOdoKm
        lastOdoKm = odo
        val trip = TripMeter.next(settings.savedTripKm, previous, odo)
        if (trip != settings.savedTripKm) settings.savedTripKm = trip
        return trip
    }

    // --- commandes -----------------------------------------------------------------------

    private fun lockFrame(): ByteArray =
        if (newProtocol) EtwowProtocol.lockNew() else EtwowProtocol.lock()

    private fun unlockFrame(): ByteArray =
        if (newProtocol) EtwowProtocol.unlockNew() else EtwowProtocol.unlock()

    private fun lightFrame(on: Boolean): ByteArray = if (newProtocol) {
        if (on) EtwowProtocol.lightOnNew() else EtwowProtocol.lightOffNew()
    } else {
        if (on) EtwowProtocol.lightOn() else EtwowProtocol.lightOff()
    }

    private fun modeFrame(mode: Int): ByteArray =
        if (newProtocol) EtwowProtocol.setModeNew(mode) else EtwowProtocol.setMode(mode)

    private fun lock() {
        val snap = ScooterBus.state.value
        val speed = snap.speedKmh
        if (speed == null || speed > 0) {
            ScooterBus.log("verrouillage refusé : vitesse ${speed ?: "inconnue"}")
            return
        }
        command(lockFrame(), "verrouillage") { it.locked == true }
    }

    private fun manualLight(on: Boolean) {
        if (ScooterBus.state.value.connection != Connection.Connected) return
        lightOverride = true
        lightOverrideDesired = currentDesiredLight() ?: !on
        command(lightFrame(on), "phare") { it.lightOn == on }
    }

    private fun setMode(mode: Int) = command(
        modeFrame(mode),
        "limite ${SpeedModes.kmh(mode)} km/h",
    ) { it.mode == mode }

    /** Le widget ne fait qu'une chose : passer à 25 km/h, même s'il est déjà à 25. */
    private fun set25() {
        val snap = ScooterBus.state.value
        if (snap.connection != Connection.Connected) {
            // Le widget démarre la connexion mais ne met jamais l'ordre en attente.
            ScooterBus.log("widget : hors ligne, connexion demandée")
            if (transport == null) restart()
            return
        }
        if (snap.mode == SpeedModes.L3_25) return
        setMode(SpeedModes.L3_25)
    }

    private fun command(frame: ByteArray, label: String, matches: (ScooterSnapshot) -> Boolean) {
        val link = transport
        if (link == null || ScooterBus.state.value.connection != Connection.Connected) {
            ScooterBus.log("$label impossible : non connecté")
            return
        }
        pending = Pending(label, matches)
        pendingJob?.cancel()
        pendingJob = scope.launch {
            delay(6_000)
            if (pending != null) {
                pending = null
                ScooterBus.log("$label : pas de confirmation")
                ScooterBus.update { it.copy(pending = null) }
                render()
            }
        }
        ScooterBus.update { it.copy(pending = "$label…") }
        link.write(frame)
        render()
    }

    private fun checkPending() {
        val current = pending ?: return
        if (!current.matches(ScooterBus.state.value)) return
        pending = null
        pendingJob?.cancel()
        ScooterBus.log("${current.label} confirmé par notification")
        ScooterBus.update { it.copy(pending = null) }
    }

    // --- phare automatique ---------------------------------------------------------------

    private fun currentDesiredLight(): Boolean? {
        if (!settings.autoLight) return null
        val (lat, lon) = position() ?: return null
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val day = SolarTimes.forDate(now.toLocalDate(), lat, lon, zone)
        return AutoLight.shouldBeOn(now, day)
    }

    private fun applyAutoLight() {
        val (lat, lon) = position() ?: run {
            if (settings.autoLight) ScooterBus.update { it.copy(solarSummary = "position inconnue") }
            return
        }
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val day = SolarTimes.forDate(now.toLocalDate(), lat, lon, zone)
        val format = DateTimeFormatter.ofPattern("HH:mm")
        val summary = when {
            day.polarDay -> "jour polaire · phare éteint"
            day.polarNight -> "nuit polaire · phare allumé"
            else -> "lever ${day.sunrise?.format(format) ?: "—"} · coucher ${day.sunset?.format(format) ?: "—"}"
        }
        ScooterBus.update { it.copy(autoLight = settings.autoLight, solarSummary = summary) }

        if (!settings.autoLight) {
            lightOverride = false
            return
        }
        val desired = AutoLight.shouldBeOn(now, day)
        val snap = ScooterBus.state.value
        if (lightOverride) {
            if (desired == lightOverrideDesired) return
            lightOverride = false
        }
        if (snap.connection != Connection.Connected || snap.lightOn == null) return
        if (snap.lightOn == desired) return
        command(lightFrame(desired), "phare auto") {
            it.lightOn == desired
        }
    }

    private fun position(): Pair<Double, Double>? {
        settings.latitude?.let { lat -> settings.longitude?.let { lon -> return lat to lon } }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return null
        val manager = getSystemService(LocationManager::class.java) ?: return null
        val location = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
            ?: return null
        settings.latitude = location.latitude
        settings.longitude = location.longitude
        return location.latitude to location.longitude
    }

    // --- notification et widget ----------------------------------------------------------

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel("scooter")
        // IMPORTANCE_MIN : pas d'icône dans la barre d'état, pas de son ; la notification du
        // service reste dans le volet (obligatoire), mais elle ne dérange plus.
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_MIN,
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val snap = ScooterBus.state.value
        val text = when (snap.connection) {
            Connection.Connected -> listOfNotNull(
                getString(R.string.notification_connected),
                snap.mode?.let { SpeedModes.label(it) },
                snap.batteryPercent?.let { "$it %" },
            ).joinToString(" · ")

            Connection.Connecting -> getString(R.string.notification_connecting)
            else -> getString(R.string.notification_disconnected)
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    /** false si le système refuse le type connectedDevice faute d'autorisation Bluetooth. */
    private fun startForegroundNotification(): Boolean {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
            true
        } catch (e: SecurityException) {
            ScooterBus.log("service arrêté : autorisation Bluetooth manquante")
            false
        }
    }

    private fun render() {
        val snap = ScooterBus.state.value
        val key = "${snap.connection}|${snap.mode}|${snap.batteryPercent}|${snap.deviceName}"
        if (key != notificationKey) {
            notificationKey = key
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification())
        }
        val widget = "${snap.connection}|${snap.mode}|${snap.pending}"
        if (widget != widgetKey) {
            widgetKey = widget
            SpeedLimitWidget.update(this)
        }
    }
}
