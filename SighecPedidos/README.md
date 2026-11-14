# SighecPedidos

App Android (Kotlin + Jetpack Compose) para **toma de pedidos** conectada al
SQL Server de **Profit Plus** en la red local. Diseñada para la tablet
**Oteeto TAB19 PRO** (10.1", 800×1280) en **modo horizontal**.

## Funciones (v0.1)

| Pantalla | Qué hace |
|---|---|
| Configuración | IP/puerto/instancia del SQL Server, base de datos, login SQL, tipo de precio, almacén, % IVA y URL de imágenes. Botón **Probar conexión**. |
| Login | Usuario y contraseña validados contra la tabla `SighecUsuarios`. Después del primer ingreso ofrece **activar la huella**. |
| Menú | Toma de Pedidos (activa), Consulta de Pedidos, Clientes, Artículos (próximamente), Configuración. |
| Toma de Pedidos | Pantalla dividida: **izquierda** el pedido (cliente, renglones, +/−, totales con IVA, guardar). **Derecha**: pestañas con las **líneas de artículos** arriba y **botones con imagen** de los artículos de la línea elegida. |

## Puesta en marcha

### 1. Base de datos
Ejecute `sql/01_tablas_sighec.sql` en la base de datos de la empresa en Profit
(SQL Server 2012 o superior). Crea solo tablas propias `Sighec*`; **no toca las tablas de Profit**.

Cree el primer usuario de la app:
```sql
EXEC dbo.SighecGuardarUsuario @usuario = 'admin', @nombre = 'Administrador', @clave = N'SuClave';
```
Al final del script hay un ejemplo comentado para crear el login SQL `sighec_app` que usará la tablet.

En el servidor: habilite **TCP/IP** en *SQL Server Configuration Manager*, la
autenticación mixta (SQL Server) y abra el puerto 1433 en el firewall (o UDP 1434
si usa instancia con nombre, p. ej. `SQLEXPRESS`).

### 2. Compilar e instalar
1. Abra la carpeta `SighecPedidos` en **Android Studio** (Ladybug o más reciente).
2. Active en la tablet *Opciones de desarrollador → Depuración USB* y conéctela.
3. Pulse **Run ▶**.

### 3. Primer uso en la tablet
1. Se abre **Configuración**: ingrese IP del servidor, base de datos y login SQL → *Probar conexión* → *Guardar*.
2. Ingrese con el usuario creado en el paso 1 y acepte **Activar huella** si la tablet tiene lector.

## Imágenes de artículos
Coloque las fotos en una carpeta publicada por HTTP en la red (IIS, por ejemplo),
nombradas con el código del artículo, y configure la URL con `{co_art}`:
`http://192.168.1.10/imagenes/{co_art}.jpg`. Si no hay imagen se muestra un ícono.

## Pedidos guardados
Se guardan en `SighecPedido` y `SighecPedidoReng` con estado `PENDIENTE`.
El paso a `saPedidoVenta` de Profit queda para una siguiente etapa.

## Estructura
```
app/src/main/java/com/sighec/pedidos/
├── MainActivity.kt          navegación
├── SighecApp.kt             dependencias
├── data/                    conexión SQL (jTDS), consultas Profit, repositorios
│   └── ProfitQueries.kt     ← ajuste aquí las consultas a su versión de Profit
├── seguridad/               hash de claves y huella (BiometricPrompt + Keystore)
└── ui/                      config, login, menu, pedido (Compose)
sql/01_tablas_sighec.sql     tablas y procedimiento de usuarios
```

## Notas técnicas
- Driver: **jTDS 1.3.1** (el driver oficial de Microsoft no funciona bien en Android).
- Las claves de usuario se guardan como `SHA2_256(sal + clave)`; nunca en texto plano.
- Con huella, la contraseña queda cifrada con una llave del Android Keystore que solo se libera con la huella; si se registra una huella nueva, hay que volver a ingresar con contraseña.
- Las consultas usan el esquema de Profit Plus 2K12 (`saArticulo`, `saLineaArticulo`, `saArtPrecio`, `saStockAlmacen`, `saCliente`).
