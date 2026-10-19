// =====================================================================
// MÓDULO :feature:inicio
// Menú principal con las opciones de la App. No conoce a los demás
// módulos: solo avisa qué opción se tocó; :app decide a dónde ir.
// =====================================================================

plugins {
    alias(libs.plugins.android.library) // Librería Android
    alias(libs.plugins.kotlin.android)  // Escrita en Kotlin
    alias(libs.plugins.kotlin.compose)  // Usa Jetpack Compose
}

android {
    // Paquete interno de este módulo
    namespace = "com.siguec.puraceramica.feature.inicio"
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

    // Activamos Compose
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Tema visual y componentes de Compose
    implementation(project(":core:ui"))
}
