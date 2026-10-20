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
   (PBKDF2 mismo formato, bloqueo, permisos por empresa, bitácora). La entrada es SIN
   conexión (corregido a pedido del usuario): con el hash guardado en el teléfono; solo la
   primera vez de cada usuario en ese teléfono va al servidor. Bloqueo local 5 claves/15 min.
   Los permisos se refrescan al conectarse para sincronizar/abrir/enviar (actualizarSesion).
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

10. **Enviar** = resultado de inventario de Profit (`saResInventario` + `saResInventarioReng`),
    con co_us_in / co_sucu_in del REGISTRO del inventario (pedido del usuario; se guardan al
    abrir, en el teléfono y en aperturas). num_resinv = correlativo de Profit con
    dbo.psObtenerProximoNumero (sql/06), dentro de la transacción del envío. Cada teléfono
    = un resultado (uso normal: UN teléfono por almacén/inventario abierto; el usuario aceptó
    dejar así el caso de varios). co_uni = unidad principal de saArtUnidad
    (uni_principal = 1, CONFIRMADO con el script de la tabla); total_art_teo de
    saStockAlmacen; cost_unit = dbo.ConsultarUltimoCosto(saArticulo.rowguid) (función de la
    empresa, sql/05; parámetro profit.funcion_costo).

11. **Imagen**: nombre visible "Conteo de Inventario Puraceramica" (pedido del usuario). Logos de
    Puracerámica: el completo en la entrada, el horizontal arriba del menú, el símbolo en el
    ícono de la App; barras en el rojo del logo (core/ui: Marca.kt y Tema.kt).

12. **Moneda** (pedido del usuario): se elige una vez en Configuración del teléfono (lista de
    saMoneda de Profit: co_mone, mone_des, cambio) y la tasa del resultado es saMoneda.cambio
    al enviar. Se quitaron profit.co_mone / profit.tasa.

13. **Base de Profit en Configuración** (pedido del usuario): se escribe la base de la empresa
    (ej. SAPURA_A). Probar/Guardar revisan las dos bases y registran la empresa en la App con
    los administradores (empresas + ambientes) si falta; al entrar se usa esa empresa.
    El usuario de SQL `siguec_puraceramica` lo crea sql/03 (el mensaje de error lo dice).

14. **Huella y Configuración protegida** (pedido del usuario, opción "Ambos"):
    - Entrada con huella para todos: la clave se cifra con una llave del Android Keystore
      (BiometricPrompt + CryptoObject, BIOMETRIC_STRONG, invalidada al cambiar las huellas);
      con huella se descifra y se entra normal. androidx.biometric 1.1.0; MainActivity es
      FragmentActivity.
    - Configuración ya configurada: pide huella o clave de un usuario con
      usuarios.puede_configurar = 1 (nuevo, sql/02) o administrador; varios usuarios.
      Sin servidor, con el hash recordado en el teléfono.
    - Bases de datos cambiables más adelante; cambiar la base de Profit cierra la sesión.

15. **Escáner**: la etiqueta real (foto del usuario) es Code 39 `*3065600601201*`. Con 10
    formatos activos el escáner daba números falsos (ITF/Codabar leen pedazos): ahora solo
    Code 39, EAN-13 y Code 128, con autozoom (EscanerCodigo.kt).

16. **Enviar sin WiFi cerraba la App** (reporte del usuario): BaseDatos.usar solo atrapaba
    SQLException; ahora atrapa cualquier falla (red, driver) y devuelve SIN_CONEXION, con una
    prueba rápida de socket (4 s) antes del driver. Red de seguridad en InventariosViewModel.enviar.

17. **Prueba de conexión antes de enviar** (pedido del usuario): en Inventarios, "Enviar a
    Profit" está apagado hasta que "Probar conexión" (SELECT 1 en la base de la empresa) sale
    bien; después de cada envío vuelve a "sin probar".

## POR CONFIRMAR con la base real
- Que Profit acepte VARIOS resultados por inventario y los sume al procesarlo (si no, hay que
  enviar desde un solo teléfono o juntar en un resultado).
- `saStockAlmacen`: columnas co_alma, co_art, tipo ('ACT'), stock.
- Encabezado con pInsertarResInventario y renglones con pInsertarRenglonesResInventario
  (procedimientos de Softech, con pista de auditoría).
- Compilar las pantallas en Android Studio (en la sesión no había acceso a Google Maven;
  :core:red sí se compiló y probó: 19 pruebas OK).
