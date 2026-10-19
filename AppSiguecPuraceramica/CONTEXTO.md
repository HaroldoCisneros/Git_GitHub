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
   de barras: se escanean co_art, modelo, ref. Se excluyen servicios (tipo 'S').
   Nombres configurables en `SIGUEC_PURACERAMICA.dbo.parametros` (claves profit.*).
7. Envío idempotente: clave de envío (UUID) generada en el teléfono, UNIQUE en `envios`.
8. Cantidades como texto y sumas con BigDecimal (formato venezolano en pantalla).

## PENDIENTE
- **Tablas de la toma física de Profit 2K12** (el usuario las va a pasar): implementar
  `TomaFisicaProfit` en `android/core/red/.../TomaFisica.kt` (abiertas, estaAbierta, enviar)
  y cambiar `TomaFisicaProfit.actual`. Definir cómo se acumulan los envíos de varios
  teléfonos en la misma toma (sumar sobre lo existente o renglones por envío).
  Actualizar GRANT en sql/03.
- Confirmar dónde están los códigos de barra (¿campo1..8? ¿tabla aparte?).
- Compilar las pantallas en Android Studio (en la sesión no había acceso a Google Maven;
  :core:red sí se compiló y probó: 15 pruebas OK).
