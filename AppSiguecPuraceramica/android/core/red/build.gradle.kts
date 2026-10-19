// =====================================================================
// MÓDULO :core:red
// Comunicación con la API del servidor (servidor/ en este repositorio).
// Es Kotlin PURO (no usa nada de Android): se puede probar en cualquier
// computadora con "gradlew :core:red:test", sin teléfono ni emulador.
// =====================================================================

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)           // Kotlin puro
    alias(libs.plugins.kotlin.serialization) // Clases <-> JSON
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
    api(libs.kotlinx.coroutines.core)        // Funciones "suspend"
    api(libs.kotlinx.serialization.json)     // JSON
    implementation(libs.retrofit)            // Llamadas HTTP como funciones
    implementation(libs.retrofit.kotlinx.serialization) // Retrofit + JSON de kotlinx
    // Cliente HTTP. "api" (no "implementation") porque ClienteSiguec muestra tipos de
    // OkHttp hacia afuera (HttpUrl, OkHttpClient) y :core:sesion los usa.
    api(libs.okhttp)

    // Pruebas: un servidor falso local que responde lo que le digamos
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
