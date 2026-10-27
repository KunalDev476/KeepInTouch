plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.pragmaapps.keepintouch"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.pragmaapps.keepintouch"
        minSdk = 34
        targetSdk = 36
        versionCode = 9
        versionName = "1.3.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Shrinking only (no custom proguard-rules.pro, so nothing debug-only gets stripped
            // by name) - AGP disables actual optimization/obfuscation on a debuggable build type
            // regardless, so this is purely dead-code removal. Without it classes.dex alone was
            // 42MB+ from material-icons-extended's full icon catalog going in unshrunk even though
            // only a handful of icons are ever used - dropped the whole APK from ~70MB to ~18MB.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // minifyDebugAndroidTestWithR8 shrinks the test APK too and hits the same
            // compile-time-only annotation classes (see proguard-rules.pro) - same file, just
            // wired to the test-APK R8 pass instead of proguardFiles (the app's own R8 pass).
            testProguardFile("proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    sourceSets {
        getByName("androidTest") {
            // Exported Room schema JSON, so MigrationTestHelper can load prior versions.
            assets.srcDirs("$projectDir/schemas")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // ProcessLifecycleOwner - tracks whether the whole app (not just this Activity) is in the
    // foreground, so a same-process interruption (a permission dialog, a config change) never
    // reads as "backgrounded" the way Activity.onStop alone can - see ForegroundResetGate.
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Encryption at rest: SQLCipher's SupportSQLiteOpenHelper.Factory plugs straight into Room.
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)
    // Passphrase itself is encrypted with a key held in the Android Keystore (see PassphraseManager)
    // via plain java.security/javax.crypto - no androidx.security:security-crypto dependency needed.

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Firebase BoM keeps every Firebase artifact on mutually compatible versions - never specify
    // a version directly on a Firebase library below this line.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    // MigrationTestHelper needs InstrumentationRegistry, so it belongs in androidTest, not test.
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
