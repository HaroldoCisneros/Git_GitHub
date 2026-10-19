// =====================================================================
// MÓDULO :core:sesion
// La sesión del usuario en el teléfono: datos de conexión al SQL Server,
// usuario y empresa elegida. Permite entrar SIN WiFi con la clave de la
// última vez que ese usuario entró en el teléfono.
// =====================================================================

plugins {
    alias(libs.plugins.android.library) // Librería Android (no es una app)
    alias(libs.plugins.kotlin.android)  // Escrita en Kotlin
    alias(libs.plugins.kotlin.serialization) // Usuarios recordados <-> JSON
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
    // "api" = las pantallas que usen la sesión también ven la red (SQL Server) y la base local
    api(project(":core:red"))
    api(project(":core:local"))
    // Atajos de Kotlin para Android (SharedPreferences.edit { })
    implementation(libs.androidx.core.ktx)
}
