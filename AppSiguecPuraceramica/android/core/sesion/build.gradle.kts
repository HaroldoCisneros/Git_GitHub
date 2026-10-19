// =====================================================================
// MÓDULO :core:sesion
// La sesión del usuario en el teléfono: dirección del servidor, token,
// usuario y empresa elegida. Guarda lo necesario para no pedir la clave
// cada vez que se abre la App (hasta que el token venza).
// =====================================================================

plugins {
    alias(libs.plugins.android.library) // Librería Android (no es una app)
    alias(libs.plugins.kotlin.android)  // Escrita en Kotlin
}

android {
    // Paquete interno de este módulo
    namespace = "com.siguec.puraceramica.core.sesion"
    // SDK con el que se compila
    compileSdk = 35

    defaultConfig {
        // Android mínimo soportado: 26 = Android 8.0
        minSdk = 26
    }

    // Java 17
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // "api" = las pantallas que usen la sesión también ven los DTO y repositorios de la red
    api(project(":core:red"))
    // Atajos de Kotlin para Android (SharedPreferences.edit { })
    implementation(libs.androidx.core.ktx)
}
