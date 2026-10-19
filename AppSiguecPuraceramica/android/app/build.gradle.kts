// =====================================================================
// MÓDULO :app
// La aplicación final. Casi no tiene código propio: arranca la app, crea
// la sesión y conecta (navega) entre los módulos de funciones.
// =====================================================================

plugins {
    alias(libs.plugins.android.application) // Esto es una aplicación Android
    alias(libs.plugins.kotlin.android)      // Escrita en Kotlin
    alias(libs.plugins.kotlin.compose)      // Con pantallas en Jetpack Compose
}

android {
    // Paquete interno del código
    namespace = "com.siguec.puraceramica.app"
    // Versión del SDK de Android con la que se compila
    compileSdk = 35

    defaultConfig {
        // Identificador único de la app en el teléfono y en Google Play
        applicationId = "com.siguec.puraceramica"
        // Android mínimo soportado: 26 = Android 8.0
        minSdk = 26
        // Versión de Android para la que está pensada la app
        targetSdk = 35
        // Número interno de versión (se sube en cada publicación)
        versionCode = 1
        // Versión que ve el usuario
        versionName = "1.0"
    }

    buildTypes {
        // "debug" = la que se instala desde Android Studio para probar.
        // Permite "http://" para probar con el servidor en la red local
        // (ej. http://192.168.1.10:8000) sin certificado.
        debug {
            manifestPlaceholders["permitirHttp"] = "true"
        }
        // "release" = la que se entrega a los usuarios: SOLO "https://"
        // (la clave y los datos viajan cifrados por internet).
        release {
            isMinifyEnabled = false
            manifestPlaceholders["permitirHttp"] = "false"
        }
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
    // ---------- Módulos propios ----------
    implementation(project(":core:sesion"))        // Sesión (y la red, que viene con ella)
    implementation(project(":core:ui"))            // Tema visual
    implementation(project(":feature:login"))      // Entrada y elección de empresa
    implementation(project(":feature:inicio"))     // Menú principal
    implementation(project(":feature:articulos"))  // Consultar y sincronizar artículos
    implementation(project(":feature:inventario")) // Inventarios por almacén
    implementation(project(":feature:conteo"))     // Contar escaneando
    implementation(project(":feature:admin"))      // Usuarios, permisos y bitácora

    // ---------- Librerías ----------
    implementation(libs.androidx.core.ktx)              // Atajos de Kotlin para Android
    implementation(libs.androidx.lifecycle.runtime.ktx) // Ciclo de vida
    implementation(libs.androidx.lifecycle.runtime.compose) // Observar estado en Compose
    implementation(libs.androidx.activity.compose)      // Activity que muestra Compose
    implementation(libs.androidx.navigation.compose)    // Navegación entre pantallas
    debugImplementation(libs.compose.ui.tooling)        // Depuración visual (solo pruebas)
}
