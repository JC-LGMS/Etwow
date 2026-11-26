import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

/*
 * Version automatique (voir AGENTS.md, « Version et journal des modifications »).
 *
 * L'empreinte de app/src/main + ce script est comparée à sourceHash dans version.properties.
 * Une évolution terminée incrémente la mineure (0.1 -> 0.2) et versionCode. Tant qu'un incrément
 * n'est pas commité, il est conservé : le signal est git (version.properties modifié vs HEAD).
 * Sans git, le drapeau `pending` joue le même rôle pour la session en cours.
 */
val versionFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionFile.exists()) versionFile.inputStream().use { load(it) }
}

/** 0 = propre vs HEAD, 1 = modifié, null = git absent ou pas de HEAD. */
fun gitVersionState(): Int? = try {
    // Hors dépôt, `git diff --quiet HEAD` sort avec 1 (comme « modifié ») : vérifier HEAD d'abord.
    val head = ProcessBuilder("git", "rev-parse", "--verify", "HEAD")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    head.inputStream.readBytes()
    if (head.waitFor() != 0) {
        null
    } else {
        val proc = ProcessBuilder("git", "diff", "--quiet", "HEAD", "--", versionFile.name)
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        proc.inputStream.readBytes()
        proc.waitFor()
    }
} catch (e: Exception) {
    null
}

val sourceHash = run {
    val md = MessageDigest.getInstance("SHA-256")
    val base = projectDir
    fileTree(base) {
        include("src/main/**")
        include("build.gradle.kts")
    }.files.sortedBy { it.relativeTo(base).path }.forEach { f ->
        md.update(f.relativeTo(base).path.toByteArray(Charsets.UTF_8))
        md.update(0.toByte())
        md.update(f.readBytes())
        md.update(0.toByte())
    }
    md.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

val storedHash = versionProps.getProperty("sourceHash", "")
var pending = versionProps.getProperty("pending", "0") == "1"
var appVersionCode = versionProps.getProperty("versionCode", "1").toIntOrNull() ?: 1
var appVersionName = versionProps.getProperty("versionName", "0.1")

fun bumpVersion() {
    appVersionCode += 1
    val parts = appVersionName.split(".").map { it.toIntOrNull() ?: 0 }.toMutableList()
    while (parts.size < 2) parts += 0
    parts[1] += 1
    appVersionName = parts.take(2).joinToString(".")
}

if (storedHash.isBlank()) {
    // Premier build : 0.1, pas d'incrément.
    pending = false
} else if (storedHash != sourceHash) {
    when (gitVersionState()) {
        1 -> pending = true            // incrément non commité : on le garde
        0 -> { bumpVersion(); pending = true } // version commitée : nouvelle évolution
        else -> if (pending) {
            // Pas de git : l'incrément de la session est conservé.
        } else {
            bumpVersion()
            pending = true
        }
    }
} else if (gitVersionState() == 0) {
    // Build après commit, sans changement : la prochaine modification ouvrira une version.
    pending = false
}

if (storedHash != sourceHash ||
    versionProps.getProperty("versionName") != appVersionName ||
    versionProps.getProperty("versionCode") != appVersionCode.toString() ||
    versionProps.getProperty("pending", "0") != (if (pending) "1" else "0")
) {
    versionFile.writeText(
        "versionCode=$appVersionCode\n" +
            "versionName=$appVersionName\n" +
            "sourceHash=$sourceHash\n" +
            "pending=${if (pending) 1 else 0}\n",
        Charsets.UTF_8
    )
    logger.lifecycle("Etwow : version $appVersionName ($appVersionCode)${if (pending) " — incrément en attente" else ""}")
}

val buildStamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.etwow"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.etwow"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("Etwow-$appVersionName-release-$buildStamp.apk")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
}
