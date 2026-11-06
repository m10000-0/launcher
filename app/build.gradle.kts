plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.com.familia.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.familia.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Assinatura fixa opcional (para o GitHub Actions ou para builds de release).
    // IMPORTANTE: depois de instalado como "dono do dispositivo", toda atualização
    // precisa ser assinada com a MESMA chave. Guarde sua keystore com cuidado.
    val keystorePath = System.getenv("LAUNCHER_KEYSTORE")
    signingConfigs {
        if (keystorePath != null) {
            create("fixa") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("LAUNCHER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LAUNCHER_KEY_ALIAS")
                keyPassword = System.getenv("LAUNCHER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) signingConfig = signingConfigs.getByName("fixa")
        }
        debug {
            if (keystorePath != null) signingConfig = signingConfigs.getByName("fixa")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
