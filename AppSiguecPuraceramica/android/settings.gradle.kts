// =====================================================================
// settings.gradle.kts
// Configuración general de Gradle (la herramienta que compila).
// Aquí se dice DÓNDE buscar las librerías y QUÉ módulos tiene el proyecto.
// =====================================================================

// De dónde se descargan los "plugins" de Gradle
pluginManagement {
    repositories {
        google()             // Plugins de Android
        mavenCentral()       // Repositorio público principal de Java/Kotlin
        gradlePluginPortal() // Portal oficial de plugins de Gradle
    }
}

// De dónde se descargan las librerías que usa la app
dependencyResolutionManagement {
    // Todos los módulos usan solo los repositorios definidos aquí
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()       // Librerías de Android (Compose, Lifecycle...)
        mavenCentral() // Retrofit, OkHttp, kotlinx.serialization...
    }
}

// Nombre del proyecto (se ve en Android Studio)
rootProject.name = "AppSiguecPuraceramica"

// ---------- Módulos del proyecto ----------
// Cada "include" es una carpeta con su propio build.gradle.kts.
// ":core:red" = carpeta core/red.

// Aplicación final: une los módulos y arranca la app
include(":app")

// Módulos "core" (núcleo): piezas compartidas
include(":core:red")    // Comunicación con la API del servidor (Kotlin puro, sin Android)
include(":core:sesion") // Sesión del usuario: servidor, token y empresa elegida
include(":core:ui")     // Tema, colores y componentes visuales compartidos

// Módulos "feature" (funciones): una carpeta por opción de la app
include(":feature:login")      // Entrar (servidor, usuario, clave) y elegir la empresa
include(":feature:inicio")     // Menú principal con las opciones
include(":feature:articulos")  // Consultar artículos (código, descripción, contado) y sincronizar con Profit
include(":feature:inventario") // Preparar inventarios por almacén, diferencias y cierre
include(":feature:conteo")     // Contar: escanear códigos de barra en el inventario abierto
include(":feature:admin")      // Usuarios, permisos y bitácora (administradores)
