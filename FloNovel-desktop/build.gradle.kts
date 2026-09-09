import java.util.Properties
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

group = "com.moonkata.flonovel"
version = "0.1.0"

repositories {
    mavenCentral()
    google()
}

val localProperties = Properties().apply {
    val file = listOf(
        project.file("local.properties"),
        rootProject.file("local.properties"),
        project.file("../local.properties"),
    ).firstOrNull { it.exists() }
    file?.inputStream()?.use { load(it) }
}

val dropboxAppKey = (localProperties.getProperty("DROPBOX_APP_KEY")
    ?: System.getenv("DROPBOX_APP_KEY")
    ?: (project.findProperty("dropboxAppKey") as? String)
    ?: "").trim()

val flonovelDev = (localProperties.getProperty("FLONOVEL_DEV")
    ?: System.getenv("FLONOVEL_DEV")
    ?: (project.findProperty("flonovelDev") as? String)
    ?: "").trim()

val supabaseUrl = (localProperties.getProperty("SUPABASE_URL")
    ?: System.getenv("SUPABASE_URL")
    ?: (project.findProperty("supabaseUrl") as? String)
    ?: "").trim()

val supabasePublishableKey = (localProperties.getProperty("SUPABASE_PUBLISHABLE_KEY")
    ?: System.getenv("SUPABASE_PUBLISHABLE_KEY")
    ?: (project.findProperty("supabasePublishableKey") as? String)
    ?: "").trim()

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("com.github.albfernandez:juniversalchardet:2.5.0")
    implementation("org.json:json:20240303")
    implementation("javazoom:jlayer:1.0.1")
    implementation("com.tianscar.javasound:javasound-aac:0.9.8")
    testImplementation(kotlin("test"))
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

compose.desktop {
    application {
        mainClass = "com.moonkata.flonovel.desktop.MainKt"
        val extraJvmArgs = mutableListOf<String>()
        if (dropboxAppKey.isNotEmpty()) {
            extraJvmArgs.add("-Dflonovel.dropbox.app_key=$dropboxAppKey")
        }
        if (flonovelDev.isNotEmpty()) {
            extraJvmArgs.add("-Dflonovel.dev=$flonovelDev")
        }
        if (supabaseUrl.isNotEmpty()) {
            extraJvmArgs.add("-Dflonovel.supabase.url=$supabaseUrl")
        }
        if (supabasePublishableKey.isNotEmpty()) {
            extraJvmArgs.add("-Dflonovel.supabase.publishable_key=$supabasePublishableKey")
        }
        // Force 1.0 pixel scaling on Windows to prevent fractional sub-pixel blur and DWM bitmap stretching
        extraJvmArgs.add("-Dsun.java2d.uiScale=1.0")
        if (extraJvmArgs.isNotEmpty()) {
            jvmArgs += extraJvmArgs
        }
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Exe)
            packageName = "flonovel"
            packageVersion = "1.0.0"
            modules("java.net.http", "jdk.httpserver")
            windows {
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }
            macOS {
                iconFile.set(project.file("src/main/resources/icon.icns"))
            }
            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
        }
    }
}

tasks.withType<JavaExec> {
    systemProperty("sun.java2d.uiScale", "1.0")
    if (dropboxAppKey.isNotEmpty()) {
        systemProperty("flonovel.dropbox.app_key", dropboxAppKey)
    }
    if (flonovelDev.isNotEmpty()) {
        systemProperty("flonovel.dev", flonovelDev)
    }
    if (supabaseUrl.isNotEmpty()) {
        systemProperty("flonovel.supabase.url", supabaseUrl)
    }
    if (supabasePublishableKey.isNotEmpty()) {
        systemProperty("flonovel.supabase.publishable_key", supabasePublishableKey)
    }
}

tasks.test {
    useJUnitPlatform()
    if (dropboxAppKey.isNotEmpty()) {
        systemProperty("flonovel.dropbox.app_key", dropboxAppKey)
    }
    if (flonovelDev.isNotEmpty()) {
        systemProperty("flonovel.dev", flonovelDev)
    }
    if (supabaseUrl.isNotEmpty()) {
        systemProperty("flonovel.supabase.url", supabaseUrl)
    }
    if (supabasePublishableKey.isNotEmpty()) {
        systemProperty("flonovel.supabase.publishable_key", supabasePublishableKey)
    }
}
