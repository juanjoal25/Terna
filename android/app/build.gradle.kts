plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.ternagames.terna"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.ternagames.terna"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        // Para apuntar a los emuladores locales de Firebase: ./gradlew -PuseEmulators=true (ver docs/FIREBASE_SETUP.md §15).
        buildConfigField("boolean", "USE_EMULATORS", providers.gradleProperty("useEmulators").getOrElse("false"))
        buildConfigField("int", "EMULATOR_PORT_OFFSET", providers.gradleProperty("emulatorPortOffset").getOrElse("0"))
    }

    // Cada entorno usa su propio proyecto de Firebase y su google-services.json:
    //   app/src/dev/google-services.json   ← terna-dev-718c7
    //   app/src/prod/google-services.json  ← terna-prod (aún no existe)
    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            resValue("string", "app_name", "TERNA Dev")
        }
        create("prod") {
            dimension = "env"
            resValue("string", "app_name", "TERNA!")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Mientras no exista el proyecto terna-prod (y su google-services.json), las variantes prod
// se desactivan para que "Build APK(s)" no falle. Se activan solas al agregar el archivo.
androidComponents {
    beforeVariants { variant ->
        val isProd = variant.productFlavors.any { it.second == "prod" }
        if (isProd && !file("src/prod/google-services.json").exists()) {
            variant.enable = false
        }
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:home"))
    implementation(project(":feature:room"))
    implementation(project(":feature:game"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
