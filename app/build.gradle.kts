plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cl.efficientchile.boletas"
    compileSdk = 34

    defaultConfig {
        // applicationId distinto al del inventario: asi las dos apps conviven
        // en el mismo telefono, cada una con su icono, sin pisarse.
        applicationId = "cl.efficientchile.boletas"
        minSdk = 31          // Android 12, igual que la otra app
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    /* La MISMA llave de firma que la app de inventario. Es una llave de
       depuracion, sus credenciales son publicas por convencion, y compartirla
       entre las dos apps no tiene ningun problema: lo unico que importa es
       que sea FIJA, para que cada version se instale sobre la anterior sin el
       "paquete no valido". */
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("llave/depuracion.p12")
            storePassword = "android"
            keyAlias = "inventario"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Material 3 marca como experimental media biblioteca (TopAppBar,
        // Surface con onClick). Se declara una vez aca en vez de @OptIn en
        // cada archivo, que es lo que hace que el build se caiga por olvido.
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}") }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // Explicita, aunque foundation ya la arrastra: la pantalla de gastos usa
    // AnimatedVisibility y depender de que llegue de rebote es como se rompe
    // un build al subir una version.
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.core:core-ktx:1.13.1")

    // Camara.
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // Lectura de texto de la foto. Version "bundled": el modelo va dentro del
    // APK y funciona sin señal, que es justo cuando se registran boletas.
    implementation("com.google.mlkit:text-recognition:16.0.1")

    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // El Excel se escribe a mano con java.util.zip (parte de Android): no hay
    // libreria de xlsx que compile sin sorpresas en Android, y la estructura
    // del archivo ya se valido abriendola con un lector de Excel real.
}
