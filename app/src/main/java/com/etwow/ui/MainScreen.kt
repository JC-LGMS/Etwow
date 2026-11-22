package com.etwow.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etwow.BuildConfig
import com.etwow.R
import com.etwow.data.Connection
import com.etwow.data.ScooterBus
import com.etwow.data.Settings
import com.etwow.protocol.SpeedModes
import com.etwow.service.ScooterService

private enum class Page { Main, Settings, Info }

@Composable
fun MainScreen() {
    var page by remember { mutableStateOf(Page.Main) }
    var showChangelog by remember { mutableStateOf(false) }

    // Le retour système ramène à la page principale au lieu de quitter.
    BackHandler(enabled = page != Page.Main) { page = Page.Main }

    when (page) {
        Page.Main -> MainPage(
            onSettings = { page = Page.Settings },
            onInfo = { page = Page.Info },
            onVersion = { showChangelog = true },
        )

        Page.Settings -> SettingsPage(onBack = { page = Page.Main })
        Page.Info -> InfoPage(onBack = { page = Page.Main })
    }

    if (showChangelog) {
        ChangelogDialog(onDismiss = { showChangelog = false })
    }
}

@Composable
private fun MainPage(onSettings: () -> Unit, onInfo: () -> Unit, onVersion: () -> Unit) {
    val context = LocalContext.current
    val state by ScooterBus.state.collectAsState()
    val settings = remember { Settings(context) }
    var autoLight by remember { mutableStateOf(settings.autoLight) }
    val connected = state.connection == Connection.Connected
    val canLock = connected && state.speedKmh == 0

    Screen {
        Header(version = "v${BuildConfig.VERSION_NAME}", onVersion = onVersion)

        CardBox {
            Text(
                buildString {
                    append(
                        when (state.connection) {
                            Connection.Connected -> "Connecté · ${state.deviceName ?: "trottinette"}"
                            Connection.Connecting -> "Connexion…"
                            Connection.Idle -> "Inactif"
                            Connection.Offline -> "Hors ligne"
                        },
                    )
                    if (state.simulator) append(" · simulateur")
                },
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            state.pending?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { ScooterService.send(context, ScooterService.ACTION_RECONNECT) }) {
                    Text(if (connected) "Reconnecter" else "Connecter")
                }
                OutlinedButton(onClick = { ScooterService.send(context, ScooterService.ACTION_STOP) }) {
                    Text("Déconnecter")
                }
            }
        }

        CardBox {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Text(
                        state.speedKmh?.toString() ?: "—",
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("km/h", modifier = Modifier.padding(bottom = 12.dp))
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Metric("Batterie", state.batteryPercent?.let { "$it %" } ?: "—")
                    Metric("Depuis l'allumage", state.tripKm?.let { "$it km" } ?: "—")
                    Metric("Odomètre", state.odoKm?.let { "$it km" } ?: "—")
                }
            }
        }

        CardBox {
            val locked = state.locked == true
            val lightOn = state.lightOn == true
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CommandToggle(
                    "Verrou",
                    active = locked,
                    enabled = if (locked) connected else canLock,
                    modifier = Modifier.weight(1f),
                ) {
                    ScooterService.send(
                        context,
                        if (locked) ScooterService.ACTION_UNLOCK else ScooterService.ACTION_LOCK,
                    )
                }
                CommandToggle(
                    "Phare",
                    active = lightOn,
                    enabled = connected,
                    modifier = Modifier.weight(1f),
                ) {
                    ScooterService.send(
                        context,
                        if (lightOn) ScooterService.ACTION_LIGHT_OFF else ScooterService.ACTION_LIGHT_ON,
                    )
                }
            }
            if (connected && state.speedKmh != 0) {
                Text(
                    "Verrouillage impossible en roulant.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Phare automatique")
                    Text(
                        state.solarSummary ?: "—",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
                Switch(
                    checked = autoLight,
                    onCheckedChange = {
                        autoLight = it
                        settings.autoLight = it
                        ScooterService.send(context, ScooterService.ACTION_START)
                    },
                )
            }

            Text("Limite de vitesse : ${state.mode?.let { SpeedModes.label(it) } ?: "—"}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpeedModes.all.sortedBy { SpeedModes.kmh(it) }.forEach { mode ->
                    val selected = state.mode == mode
                    val colors = if (selected) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    }
                    OutlinedButton(
                        onClick = { ScooterService.send(context, ScooterService.ACTION_SET_MODE, mode) },
                        enabled = connected,
                        colors = colors,
                    ) { Text(SpeedModes.kmh(mode)?.toString() ?: "?") }
                }
            }
        }

        NavRow("Réglages", onSettings)
        NavRow("Informations techniques", onInfo)
    }
}

@Composable
private fun SettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    var autoConnect by remember { mutableStateOf(settings.autoConnect) }
    var simulator by remember { mutableStateOf(settings.simulator) }

    Screen {
        SubHeader("Réglages", onBack)
        CardBox {
            ToggleRow("Connexion automatique", autoConnect) {
                autoConnect = it
                settings.autoConnect = it
                if (it) ScooterService.send(context, ScooterService.ACTION_START)
            }
            ToggleRow("Mode simulateur", simulator) {
                simulator = it
                settings.simulator = it
                ScooterService.send(context, ScooterService.ACTION_RECONNECT)
            }
        }
        Text(
            "Développé par Jean-Cyril Poiraud avec OpenCode et Deepseek v4.1 Flash 1017",
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun InfoPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val state by ScooterBus.state.collectAsState()

    Screen {
        SubHeader("Informations techniques", onBack)
        CardBox {
            InfoLine("Statut", state.statusByte?.let { "0x%02X".format(it) } ?: "—")
            InfoLine("Mode", state.mode?.toString() ?: "—")
            InfoLine("Verrou", state.locked?.toString() ?: "—")
            InfoLine("Phare", state.lightOn?.toString() ?: "—")
            InfoLine("Adresse", state.deviceAddress ?: "—")
        }
        CardBox {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Trames (${state.logs.size})", fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { copyLogs(context, state.logs) }) { Text("Copier") }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
            ) {
                Text(
                    if (state.logs.isEmpty()) "Aucune trame." else state.logs.joinToString("\n"),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun Screen(content: @Composable () -> Unit) {
    // Sans Surface au-dessus, LocalContentColor resterait noir : les titres hors carte seraient illisibles.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun Header(version: String, onVersion: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_launcher),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Text("Etwow", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            version,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 15.sp,
            modifier = Modifier
                .clickable(onClick = onVersion)
                .padding(8.dp),
        )
    }
}

@Composable
private fun SubHeader(title: String, onBack: () -> Unit) {
    Column {
        Text(
            "← Retour",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(vertical = 6.dp),
        )
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/** Carte sans titre, pour les blocs qui n'en ont pas besoin. */
@Composable
private fun CardBox(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

/** Action à deux états : fond plein quand l'état est actif, contour sinon. */
@Composable
private fun CommandToggle(
    label: String,
    active: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (active) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label) }
    }
}

@Composable
private fun NavRow(title: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, fontSize = 16.sp)
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.End) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ChangelogDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        title = { Text("Journal des modifications") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CHANGELOG.forEach { entry ->
                    Text("v${entry.version} — ${entry.date}", fontWeight = FontWeight.SemiBold)
                    entry.items.forEach { item -> Text("• $item", fontSize = 13.sp) }
                }
            }
        },
    )
}

private fun copyLogs(context: Context, logs: List<String>) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Etwow", logs.joinToString("\n")))
}
