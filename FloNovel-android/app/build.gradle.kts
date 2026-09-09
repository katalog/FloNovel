import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// The release keystore is never committed to this (public) repo — it's only passed in via an
// environment variable (CI) or via local.properties (local release builds). If RELEASE_KEYSTORE_PATH
// is missing (a fork's local build, CI without this secret set, etc.), it silently falls back to
// debug signing so assembleRelease always just works.
val releaseKeystorePath = (System.getenv("RELEASE_KEYSTORE_PATH")
    ?: localProperties.getProperty("RELEASE_KEYSTORE_PATH"))?.takeIf { it.isNotBlank() }
val releaseKeystorePassword = (System.getenv("RELEASE_KEYSTORE_PASSWORD")
    ?: localProperties.getProperty("RELEASE_KEYSTORE_PASSWORD"))?.trim()
val releaseKeyAlias = (System.getenv("RELEASE_KEY_ALIAS")
    ?: localProperties.getProperty("RELEASE_KEY_ALIAS"))?.trim()
val releaseKeyPassword = (System.getenv("RELEASE_KEY_PASSWORD")
    ?: localProperties.getProperty("RELEASE_KEY_PASSWORD"))?.trim()

// The Supabase URL/publishable key are also injected here instead of as a source literal — the
// value itself isn't secret (RLS is the real defense, see SupabaseConfig.kt), but this avoids it
// sitting permanently in the public repo's history (SYNC_MULTIUSER_PLAN.md stage 3). For local dev,
// put these two keys in local.properties (gitignored); CI passes them as env vars (see release.yml).
// If neither is set, the build still succeeds with empty strings — matching this project's existing
// principle, only the VSCode sync feature is silently disabled at runtime (the
// ReadingPositionSyncClient call fails and runCatching swallows it — judged, like release signing,
// as an "optional feature with no reason to block the build itself").
// The .trim() matters — pasting a GitHub Actions secret into the web UI can easily leave a trailing
// newline/space, and if that survives on the end of the URL string it produces a request path like
// "https://...supabase.co /rest/v1/..." with a stray space, which PostgREST rejects with
// "PGRST125: invalid path specified in request url" (hit this for real — trimEnd('/') alone doesn't
// catch a trailing space). The local local.properties value can suffer the same copy-paste mistake,
// so it's trimmed the same way.
val supabaseUrl = (System.getenv("SUPABASE_URL") ?: localProperties.getProperty("SUPABASE_URL") ?: "").trim()
val supabasePublishableKey = (System.getenv("SUPABASE_PUBLISHABLE_KEY")
    ?: localProperties.getProperty("SUPABASE_PUBLISHABLE_KEY") ?: "").trim()

// Dropbox app key (public client, PKCE — there is no client secret to protect). Shared with the
// Desktop app: one Dropbox app, isolated per build by SECRET_FILE_NAME rather than by key, so this
// is the same value that sits in the Desktop's local.properties. Injected rather than hardcoded so
// the key never lands in git.
val dropboxAppKey = (System.getenv("DROPBOX_APP_KEY")
    ?: localProperties.getProperty("DROPBOX_APP_KEY") ?: "").trim()

// release.yml passes these two values from the tag (v1.1 → "1.1") and the CI run number — if
// missing (a local build), it falls back to the old fixed values. Without this, no matter which tag
// a release is cut from, the installed APK's actual displayed version would always stay at this
// fallback, so a tool like Obtainium's "latest tag" would disagree with the actual installed version.
val releaseVersionName = System.getenv("RELEASE_VERSION_NAME")
val releaseVersionCode = System.getenv("RELEASE_VERSION_CODE")?.toIntOrNull()

android {
    namespace = "com.moonkata.flonovel.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.moonkata.flonovel.android"
        minSdk = 24
        targetSdk = 36
        versionCode = releaseVersionCode ?: 1
        versionName = releaseVersionName ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$supabasePublishableKey\"")
        // Name of the shared-secret file inside the Dropbox app folder. The debug
        // buildType overrides it so a dev build talks to a different Supabase
        // partition — the server forbids offset regression, so a stray test value
        // written to the real partition could never be undone. (docs G21)
        buildConfigField("String", "SECRET_FILE_NAME", "\"secret.json\"")
        buildConfigField("String", "DROPBOX_APP_KEY", "\"$dropboxAppKey\"")
        // The OAuth redirect lands back in the app through this scheme, and the manifest needs it as
        // a literal (it cannot read BuildConfig), so it is injected as a placeholder from the same
        // key. Dropbox accepts "db-<app key>://1/connect" without registering it in the app console.
        // A blank key would leave the scheme as the bare "db-", which is worth not finding out about
        // from a manifest-merger failure in CI. The runtime guard is DropboxConfig.isConfigured; this
        // placeholder only has to keep the manifest well-formed.
        manifestPlaceholders["dropboxRedirectScheme"] =
            if (dropboxAppKey.isBlank()) "db-unconfigured" else "db-$dropboxAppKey"
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Lets the in-development app sit alongside the released one on the
            // same phone. Without it the installs collide and the signature
            // mismatch blocks it outright. (docs G21)
            applicationIdSuffix = ".dev"
            buildConfigField("String", "SECRET_FILE_NAME", "\"secret-dev.json\"")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (releaseKeystorePath != null) "release" else "debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // The sync clients log through android.util.Log, which throws "not mocked" in a JVM
            // unit test. Returning defaults lets the protocol contract be enforced without a device.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Room (local DB)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    // DataStore (reader settings storage)
    implementation(libs.androidx.datastore.preferences)

    // Automatic encoding detection (EUC-KR/CP949, etc.)
    implementation(libs.juniversalchardet)

    // SAF folder/file browsing
    implementation(libs.androidx.documentfile)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    // The reference implementation needed to make org.json actually work in JVM unit tests —
    // android.jar's org.json is a stub that throws when called from a unit test (this real
    // implementation only takes priority on the test classpath).
    testImplementation(libs.json.java)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.mockwebserver)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
