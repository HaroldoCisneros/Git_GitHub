// =====================================================================
// MÓDULO :feature:articulos
// Artículos de la copia sincronizada con Profit 2K12 (saArticulo):
//   - Consultar: SOLO código, descripción y stock contado (si lo hay).
//   - Sincronizar: bajar los artículos de Profit a la base de la App.
// =====================================================================

plugins {
    alias(libs.plugins.android.library) // Librería Android
    alias(libs.plugins.kotlin.android)  // Escrita en Kotlin
    alias(libs.plugins.kotlin.compose)  // Usa Jetpack Compose
}

android {
    // Paquete interno de este módulo
    namespace = "com.siguec.puraceramica.feature.articulos"
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
    // Repositorios, DTO y formatos (la red viene con la sesión)
    implementation(project(":core:sesion"))
    // Tema visual, componentes y escáner de códigos
    implementation(project(":core:ui"))
    // ViewModel para Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Observar el estado respetando el ciclo de vida
    implementation(libs.androidx.lifecycle.runtime.compose)
}
