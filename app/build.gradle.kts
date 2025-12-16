plugins {
    alias(libs.plugins.room)
    alias(libs.plugins.android.application)
    alias(libs.plugins.secrets.gradle.plugin)
}

android {
    namespace = "com.botoni.avaliacaodepreco"

    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.botoni.avaliacaodepreco"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }
}

secrets {
    propertiesFileName = "secrets.properties"
    defaultPropertiesFileName = "local.defaults.properties"
}

dependencies {
    implementation(libs.room.runtime)
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    annotationProcessor(libs.room.compiler)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}