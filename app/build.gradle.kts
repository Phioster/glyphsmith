import io.gitlab.arturbosch.detekt.Detekt

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.gitlab.arturbosch.detekt")
}

android {
    namespace = "org.phioster.glyphsmith"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.phioster.glyphsmith"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    signingConfigs {
        // Committed debug keystore so every debug build (local + CI) signs with the same key —
        // lets the app update in place instead of forcing an uninstall on each new APK.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        // The real signing key, supplied by the environment and never committed. Release builds
        // used to be signed with the debug key above, which is in a public repository with a
        // published password — anyone could have signed an update that installed over a real one.
        //
        // Registered unconditionally but only *used* when the environment carries the key, so a
        // local `assembleRelease` still works (debug-signed, and not shippable) without anyone
        // having to hold the secret.
        create("release") {
            val keystore = System.getenv("RELEASE_KEYSTORE_PATH")
            if (keystore != null && file(keystore).exists()) {
                storeFile = file(keystore)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "glyphsmith"
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 on: most of the 12 MB was Compose and serialisation code nothing reaches.
            // kotlinx.serialization generates its serialisers at compile time, so it needs only a
            // few keep rules — those are in proguard-rules.pro with the reason for each.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (System.getenv("RELEASE_KEYSTORE_PATH") != null) {
                signingConfigs.getByName("release")
            } else {
                // Debug-signed, so a local release build is testable but obviously not publishable.
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        // The engine under test is plain Kotlin; anything that does touch an Android stub
        // should get a default rather than the usual "not mocked" exception. Still true for
        // every test that does not ask for Robolectric — most of them, and deliberately so:
        // a plain JVM test runs in milliseconds and needs no Android at all.
        unitTests.isReturnDefaultValues = true
        // Robolectric needs the merged resources and the manifest to stand up a real Context.
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        // Report-only gate: Lint runs in CI and uploads its report, but never blocks a build.
        abortOnError = false
        checkReleaseBuilds = false
        warningsAsErrors = false
        htmlReport = true
        sarifReport = true
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    ignoreFailures = true
    parallel = true
}

// A green build is not evidence that the tests ran. Gradle prints nothing at all when they
// pass, so a suite that silently stopped being executed — a runner that failed to load, a
// source set that stopped being compiled — looks exactly like a suite that passed. This prints
// the totals per class, so the log says which tests ran and how many.
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) = Unit
        override fun beforeTest(test: TestDescriptor) = Unit
        override fun afterTest(test: TestDescriptor, result: TestResult) = Unit
        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            if (suite.parent == null) {
                logger.lifecycle(
                    "tests: ${result.testCount} run, ${result.successfulTestCount} passed, " +
                        "${result.failedTestCount} failed, ${result.skippedTestCount} skipped",
                )
            } else if (suite.className != null) {
                logger.lifecycle("  ${result.testCount} ${suite.className!!.substringAfterLast('.')}")
            }
        }
    })
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        sarif.required.set(true)
        txt.required.set(true)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // CameraX, minus camera-view: no camera image is ever shown, only the rendered grid,
    // so a PreviewView would be a surface drawn and immediately covered.
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")

    // Phone photos arrive rotated; the decoder reads their EXIF orientation.
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Presets are serialized to a JSON file in the app's private storage.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("junit:junit:4.13.2")

    // Robolectric, for the code that genuinely cannot be tested without Android: PresetStore
    // writes a real file to a real filesDir, and Settings a real SharedPreferences. Everything
    // that can be tested without it still is — see PresetStoreTest for where the line falls.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}
