# Contexto del proyecto AppSiguecPuraceramica

Resumen de las decisiones tomadas, para continuar el trabajo en otra sesión.

## Qué es
App Android en Kotlin (Compose) SOLO para el conteo del inventario contra
**Profit Plus 2K12**. Estructura modular y comentada como AppSighecMovil.

## Decisiones (en orden)
1. Primera versión con API Python (FastAPI) — **descartada** por pedido del usuario.
2. **DECIDIDO: conexión DIRECTA al SQL Server por el WiFi** (JDBC jTDS 1.3.1, puerto 1433),
   sin programa intermedio. El usuario eligió esto sabiendo que la clave del usuario de SQL
   queda en los teléfonos (mitigación: usuario SQL mínimo, 1433 solo en la LAN, allowBackup=false,
   la clave nunca se muestra en Configuración).
3. **Trabajo sin conexión**: el teléfono guarda artículos, tomas y conteos en SQLite (:core:local).
   Solo se conecta para: entrar, sincronizar artículos, traer las tomas abiertas en Profit y
   enviar lo contado a la toma física de Profit (Profit la cierra).
4. Botón **Configuración** (entrada y menú) para los datos de conexión (pedido del usuario).
5. Usuarios y accesos en base aparte `SIGUEC_PURACERAMICA` con la lógica de AppSighecMovil
   (PBKDF2 mismo formato, bloqueo, permisos por empresa, bitácora). Sin WiFi se entra con el
   hash guardado de la última entrada con WiFi de ese usuario en ese teléfono.
   Permisos: consultar_articulos, sincronizar_articulos, contar_inventario, cerrar_inventario.
6. `saArticulo` confirmado con el script de la base real (ver README). Sin columna de código
   de barras: CONFIRMADO por el usuario que el código de barras viene en co_art, modelo o ref
   (se busca en los tres). Etiquetas con fuente C39HrP48DhTt = Code 39 (se quitan los "*").
   Se excluyen servicios (tipo 'S').
   Nombres configurables en `SIGUEC_PURACERAMICA.dbo.parametros` (claves profit.*).
7. Envío idempotente: clave de envío (UUID) generada en el teléfono, UNIQUE en `envios`.
8. Cantidades como texto y sumas con BigDecimal (formato venezolano en pantalla).

9. **Abrir inventario** (pedido del usuario): se consultan en Profit `saInventarioFisico`
   con procesado = 0 (+ des_alma de saAlmacen); se elige uno, se muestran sus datos y "Abrir"
   lo guarda con usuario y hora (teléfono + SIGUEC_PURACERAMICA.dbo.aperturas + bitácora).
   Desde ahí se cuenta escaneando, asociado a ese inventario y su almacén. Si ya estaba
   abierto en ese teléfono, se sigue con el mismo. Implementado en `TomaFisica2K12`.

## PENDIENTE
- **Renglones de la toma física de Profit 2K12** (el usuario los va a pasar): implementar
  `TomaFisica2K12.enviar()` en `android/core/red/.../TomaFisica.kt`. Definir cómo se
  acumulan los envíos de varios teléfonos en el mismo inventario. Actualizar GRANT en sql/03.
- Compilar las pantallas en Android Studio (en la sesión no había acceso a Google Maven;
  :core:red sí se compiló y probó: 16 pruebas OK).
