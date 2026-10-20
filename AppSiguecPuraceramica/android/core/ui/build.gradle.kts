// =====================================================================
// MÓDULO :core:ui
// Elementos visuales compartidos por todas las pantallas: tema, colores
// y logos de Puracerámica, barra superior y componentes comunes.
// =====================================================================

plugins {
    alias(libs.plugins.android.library) // Librería Android
    alias(libs.plugins.kotlin.android)  // Escrita en Kotlin
    alias(libs.plugins.kotlin.compose)  // Usa Jetpack Compose
}

android {
    // Paquete interno de este módulo
    namespace = "com.siguec.puraceramica.core.ui"
    // SDK con el que se compila
    compileSdk = 35

    defaultConfig {
        // Android mínimo soportado (8.0)
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

    // Activamos Compose en este módulo
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Fija versiones compatibles de Compose; "api" = los módulos que usen :core:ui también lo reciben
    api(platform(libs.compose.bom))
    // Núcleo de Compose
    api(libs.compose.ui)
    // Componentes Material 3 (botones, campos de texto...)
    api(libs.compose.material3)
    // Iconos extra (persona, candado, inventario...)
    api(libs.compose.material.icons)
    // Vistas previas en Android Studio
    api(libs.compose.ui.tooling.preview)
    // Escáner de códigos de barras con la cámara (Google Code Scanner): EscanerCodigo.kt
    implementation(libs.play.services.code.scanner)
}
