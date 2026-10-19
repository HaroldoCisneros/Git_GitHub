// =====================================================================
// MÓDULO :core:local
// Base de datos DEL TELÉFONO (SQLite): artículos sincronizados, inventarios
// traídos de Profit y los conteos. Con esto se cuenta SIN WiFi; solo se
// conecta al SQL Server para sincronizar, traer inventarios y enviar.
// =====================================================================

plugins {
    alias(libs.plugins.android.library) // Librería Android (SQLite viene con Android)
    alias(libs.plugins.kotlin.android)  // Escrita en Kotlin
}

android {
    // Paquete interno de este módulo
    namespace = "com.siguec.puraceramica.core.local"
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
    // Modelos (ArticuloDto, InventarioDto, ConteoDto...) y corrutinas
    api(project(":core:red"))
}
