# Contexto del proyecto AppSiguecPuraceramica

Resumen de las decisiones tomadas, para continuar el trabajo en otra sesión.
El detalle está en el código y en `README.md`.

## Qué es
App Android en Kotlin (Jetpack Compose) SOLO para el conteo del inventario,
contra **Profit Plus 2K12**. Misma estructura modular de AppSighecMovil
(core/feature, catálogo de versiones, colores) y misma base técnica del servidor
(FastAPI + pyodbc, config.xml, repositorios con todo el SQL).

## Reglas acordadas
- Teléfono → HTTPS → API Python en la oficina → SQL Server. Nunca directo a SQL Server.
- **Usuarios y accesos con la lógica de AppSighecMovil** (archivos copiados casi igual:
  seguridad.py, autenticacion.py, administracion.py, usuarios.py, sesiones.py), en una
  base aparte `SIGUEC_PURACERAMICA`. PBKDF2 210 000, token con SHA-256, 5 claves erradas =
  15 min bloqueado, permisos revisados en el servidor en cada petición.
- Permisos por ambiente: consultar_articulos, sincronizar_articulos, preparar_inventario,
  contar_inventario (+ es_administrador para usuarios y bitácora).
- **Nunca se escribe en Profit.** Usuario SQL `siguec_puraceramica`: solo SELECT en
  saArticulo, saAlmacen y saStockAlmacen.
- **Profit 2K12**: tablas sa* (saArticulo, saAlmacen, saStockAlmacen con tipo ACT/COM/LLE/DES).
  Nombres configurables en `<profit>` del config.xml (validados: solo letras, números y _).
- **Sincronizar** = copiar saArticulo a `articulos` + `articulo_codigos` (co_art prioridad 0,
  columnas de `<columnas_codigo>`, tabla de barras opcional). Una transacción con tabla
  temporal #sinc; los que ya no están en Profit quedan `en_profit = 0` (no se borran).
- **Consulta** de artículos: solo código, descripción y stock contado (si lo hay) del
  inventario elegido. Conteo "a ciegas": no se muestra el stock de Profit al que cuenta.
- **Preparar inventario** por almacén: guarda la foto del stock (tipo ACT) en
  `inventario_stock`. Un solo inventario abierto por almacén (UPDLOCK + HOLDLOCK).
- **Contar**: se pide el inventario abierto; se escanea (Google Code Scanner, sin permiso
  de cámara); cada conteo es individual (usuario, código leído, cantidad); stock contado =
  suma. Se acepta 0 ("no hay") y negativos (corregir). Modo rápido: cada escaneo = 1.
  Cada quien borra lo suyo; quien prepara puede borrar cualquiera (queda en bitácora).
- **Diferencias** = contado - foto del stock; incluye artículos con stock que nadie contó.
- Cantidades viajan como TEXTO; la App muestra formato venezolano (1.234,5).

## Regla del proyecto: modular y comentado
Un archivo por repositorio, servicio, ruta y pantalla. Comentarios en español.

## Pruebas
- Servidor: `cd servidor && python -m pytest` → 36 pruebas OK (bases falsas).
- App: `:core:red:test` → 9 pruebas OK (compilado aparte con Gradle; en esta sesión no
  había acceso a Google Maven, así que las pantallas NO se compilaron: hacerlo en Android Studio).

## Pendientes
- Confirmar con la base real (sql/04): columnas de 2K12 y dónde están los códigos de barra.
- Posible: contar sin conexión (guardar en el teléfono y enviar después).
- Posible: exportar las diferencias a Excel para el ajuste en Profit.
