// =====================================================================
// MÓDULO :core:red
// Conexión DIRECTA al SQL Server de Profit por el WiFi de la empresa
// (driver JDBC jTDS): usuarios y permisos, sincronizar artículos, traer las
// tomas físicas abiertas y enviar lo contado.
// Es Kotlin PURO (no usa nada de Android): se prueba en cualquier computadora
// con "gradlew :core:red:test", sin teléfono ni emulador.
// =====================================================================

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)           // Kotlin puro
    alias(libs.plugins.kotlin.serialization) // Sesión <-> JSON (se guarda en el teléfono)
}

// Java 17, igual que los módulos Android
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    // "api" = quien use este módulo también ve estas librerías
    api(libs.kotlinx.coroutines.core)        // Funciones "suspend" (la base va en otro hilo)
    api(libs.kotlinx.serialization.json)     // JSON
    implementation(libs.jtds)                // Driver de SQL Server (funciona en Android)

    // Pruebas
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
