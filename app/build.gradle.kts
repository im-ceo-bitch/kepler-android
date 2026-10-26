import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

private fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
private val firebaseProperties: Properties = Properties().also { properties ->
    val propertiesFile = rootProject.file("firebase.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use { input -> properties.load(input) }
    }
}
private val firebaseConfig: (String) -> String = { name ->
    (providers.gradleProperty(name).orNull
        ?: System.getenv(name)
        ?: firebaseProperties.getProperty(name)
        ?: "").trim()
}
private val releaseConfig: (String) -> String = { name ->
    (providers.gradleProperty(name).orNull
        ?: System.getenv(name)
        ?: "").trim()
}

android {
    namespace = "com.iumrah.beta"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.iumrah.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = providers.gradleProperty("PLAY_VERSION_NAME").orElse("2.0.3").get()

        // FCM credentials are supplied as Gradle properties or environment variables.
        // Keeping them optional lets the repository build before Firebase provisioning,
        // while the runtime enables remote push immediately once all four values exist.
        buildConfigField("String", "IUMRAH_FIREBASE_API_KEY", firebaseConfig("IUMRAH_FIREBASE_API_KEY").asBuildConfigString())
        buildConfigField("String", "IUMRAH_FIREBASE_APP_ID", firebaseConfig("IUMRAH_FIREBASE_APP_ID").asBuildConfigString())
        buildConfigField("String", "IUMRAH_FIREBASE_PROJECT_ID", firebaseConfig("IUMRAH_FIREBASE_PROJECT_ID").asBuildConfigString())
        buildConfigField("String", "IUMRAH_FIREBASE_SENDER_ID", firebaseConfig("IUMRAH_FIREBASE_SENDER_ID").asBuildConfigString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("playRelease") {
            val path = releaseConfig("PLAY_KEYSTORE_PATH")
            if (path.isNotBlank()) storeFile = file(path)
            storePassword = releaseConfig("PLAY_STORE_PASSWORD").ifBlank { null }
            keyAlias = releaseConfig("PLAY_KEY_ALIAS").ifBlank { null }
            keyPassword = releaseConfig("PLAY_KEY_PASSWORD").ifBlank { null }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("playRelease")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs {
            // Keep native libraries uncompressed/aligned for modern Play delivery.
            // MapLibre ships native .so files, so 16 KB page-size compatibility is release-critical.
            useLegacyPackaging = false
        }
    }
}

// A Kotlin/Compose project cannot add the Flutter cupertino_icons Dart package
// as a Gradle library. Bootstrap the same real MIT-licensed icon font + Flutter
// codepoints instead; no hand-drawn glyph fallback is permitted.
val bootstrapCupertinoIcons by tasks.registering(Exec::class) {
    group = "visual assets"
    description = "Install official Flutter CupertinoIcons font and exhaustive Kotlin glyph mapping."
    commandLine("python3", rootProject.file("scripts/install-cupertino-assets.py").absolutePath)
    inputs.file(rootProject.file("third_party/cupertino_icons/symbol-map.json"))
    inputs.file(file("src/main/java/com/iumrah/beta/ui/cupertino/CupertinoIcons.kt"))
    outputs.file(file("src/main/res/font/cupertino_icons.ttf"))
    listOf("light", "regular", "medium", "semibold", "bold", "heavy").forEach { weight ->
        outputs.file(file("src/main/res/font/lato_$weight.ttf"))
    }
    outputs.file(file("src/main/java/com/iumrah/beta/ui/cupertino/CupertinoGlyphCodepoints.kt"))
    outputs.file(rootProject.file("third_party/cupertino_icons/glyph-resolution.json"))
}

// Resource merge, Kotlin compile, and all Gradle build variants must wait until
// the typeface and source registry are available (including clean CI checkouts).
tasks.configureEach {
    if (name == "preBuild" ||
        (name.startsWith("compile") && name.endsWith("Kotlin")) ||
        (name.startsWith("merge") && name.endsWith("Resources"))
    ) {
        dependsOn(bootstrapCupertinoIcons)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation(platform("com.squareup.okhttp3:okhttp-bom:5.3.0"))
    implementation("com.squareup.okhttp3:okhttp")

    implementation("io.coil-kt.coil3:coil-compose:3.5.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.5.0")
    implementation("com.google.zxing:core:3.5.3")

    // Remote push parity with iOS APNs. Firebase is initialized manually from
    // BuildConfig so builds stay valid even before the Android Firebase app is provisioned.
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-messaging")

    // Real interactive maps for Ziyarats and airport selection. OpenGL is used for widest device compatibility.
    implementation("org.maplibre.gl:android-sdk-opengl:13.6.1")

    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-ui:1.11.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            output.versionCode.set(providers.gradleProperty("PLAY_VERSION_CODE").map { it.toInt() }.orElse(15))
        }
    }
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.applicationId.set("com.iumrah.beta")
    }
}

val verifyNoGooglePlayBilling by tasks.registering {
    group = "verification"
    description = "Resolve releaseRuntimeClasspath and fail if Google Play Billing is present."
    doLast {
        val configuration = configurations.getByName("releaseRuntimeClasspath")
        val forbidden = configuration.incoming.resolutionResult.allComponents
            .mapNotNull { component ->
                component.moduleVersion?.takeIf { module ->
                    module.group == "com.android.billingclient" ||
                        module.name.contains("billingclient", ignoreCase = true)
                }?.let { module -> "${module.group}:${module.name}:${module.version}" }
            }
            .distinct()
            .sorted()
        check(forbidden.isEmpty()) {
            "Google Play Billing must not be present in releaseRuntimeClasspath: ${forbidden.joinToString()}"
        }
        println("Verified releaseRuntimeClasspath: no com.android.billingclient components.")
    }
}

val validatePlayRelease by tasks.registering {
    doLast {
        val required = listOf("PLAY_KEYSTORE_PATH", "PLAY_STORE_PASSWORD", "PLAY_KEY_ALIAS", "PLAY_KEY_PASSWORD")
        required.forEach { name ->
            check(releaseConfig(name).isNotBlank()) { "Missing release signing value: $name (env or -P$name=...)" }
        }
        check(file(releaseConfig("PLAY_KEYSTORE_PATH")).isFile) { "Upload keystore not found" }
        check(providers.gradleProperty("PLAY_VERSION_CODE").orNull?.toIntOrNull()?.let { it in 15..2100000000 } == true) {
            "Pass -PPLAY_VERSION_CODE explicitly: greater than ALL Console versions (legacy archive: 14)"
        }
    }
}
tasks.configureEach {
    if (name == "preReleaseBuild") {
        dependsOn(validatePlayRelease)
        dependsOn(verifyNoGooglePlayBilling)
    }
}
