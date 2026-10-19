// =====================================================================
// build.gradle.kts (raíz del proyecto)
// Declara los plugins que usarán los módulos. Las versiones vienen del
// catálogo gradle/libs.versions.toml ("libs.plugins.xxx").
// "apply false" = se declaran aquí pero cada módulo activa los que necesita.
// =====================================================================

plugins {
    alias(libs.plugins.android.application) apply false  // Para el módulo :app
    alias(libs.plugins.android.library) apply false      // Módulos Android reutilizables
    alias(libs.plugins.kotlin.android) apply false       // Kotlin en módulos Android
    alias(libs.plugins.kotlin.jvm) apply false           // Kotlin puro (sin Android): :core:red
    alias(libs.plugins.kotlin.compose) apply false       // Compilador de Jetpack Compose
    alias(libs.plugins.kotlin.serialization) apply false // Convierte clases <-> JSON
}
