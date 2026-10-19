# AppSiguecPuraceramica

App Android (Kotlin, Jetpack Compose) **solo para el conteo del inventario**
(toma física) contra **Profit Plus 2K12**. Modular y comentada, con la misma
estructura y la misma lógica de usuarios y accesos de **AppSighecMovil**.

```
 Teléfono (App Kotlin)           Oficina (red local)
 ┌─────────────────────┐ HTTPS  ┌─────────────────────────┐        ┌───────────────────────────────┐
 │ android/            │ ─────► │ servidor/  (API Python) │ ─────► │ SQL Server                    │
 │ escanear + contar   │ JSON   │ usuario SQL + permisos  │ pyodbc │  PURACERAMICA (Profit 2K12)   │
 └─────────────────────┘        └─────────────────────────┘        │    solo LECTURA               │
                                                                    │  SIGUEC_PURACERAMICA (App)    │
                                                                    └───────────────────────────────┘
```

El teléfono **nunca** se conecta directo a SQL Server: habla con una API en la
oficina (FastAPI + pyodbc), igual que AppSighecMovil (ver allá "¿Cuál es la mejor
manera de conectar el teléfono con Profit?" y "Tailscale o IIS"; aplica igual).

## Qué hace

| Opción (menú) | Quién (permiso del ambiente) | Qué hace |
|---|---|---|
| **Sincronizar artículos** (botón en Artículos) | `sincronizar_articulos` | Baja `saArticulo` de Profit a la tabla `articulos` de la App (código, descripción, anulado) y arma los **códigos para escanear** (co_art, modelo, ref y, si se configura, la tabla de códigos de barra). Nuevos, actualizados y retirados; lo contado no se pierde. |
| **Artículos** (consultar) | `consultar_articulos` | Muestra **solo código, descripción y stock contado** (si ya se contó en el inventario elegido). Busca por código, descripción o código de barra; también escaneando. |
| **Inventarios por almacén** | `preparar_inventario` | **Preparar (abrir)** el inventario de un almacén de Profit: el servidor toma la **foto del stock** de cada artículo en ese almacén (`saStockAlmacen`, tipo `ACT`). Un solo inventario abierto por almacén. Ver **diferencias** (contado vs. stock) y **cerrar**. |
| **Contar** | `contar_inventario` | Pide el **inventario abierto** (relacionado a un almacén) y se cuenta **escaneando** el código de barras: muestra el artículo y lo ya contado, se escribe la cantidad y "Anotar". **Modo rápido**: cada escaneo anota 1 y la cámara se abre otra vez. Cada conteo es **individual** (queda a nombre de quien cuenta); el stock contado es la suma. |
| Usuarios y permisos, Bitácora | administrador | Igual que AppSighecMovil. |

**Nunca se escribe en Profit.** La oficina revisa las diferencias y hace el
ajuste de inventario en Profit.

## Profit 2K12: qué cambia respecto a 2K8

| 2K8 | 2K12 | Uso en la App |
|---|---|---|
| `ART` | `saArticulo` | Sincronizar (`co_art`, `art_des`, `anulado`, `modelo`, `ref`) |
| `ALMACEN` | `saAlmacen` | Elegir el almacén al preparar (`co_alma`, `des_alma`) |
| `ST_ALMAC` (stock_act por columna) | `saStockAlmacen` (un renglón por artículo, almacén y **tipo**: `ACT` actual, `COM` comprometido, `LLE` por llegar, `DES` por despachar) | Foto del stock: se suma `stock` donde `tipo = 'ACT'` |

Los nombres de tablas y columnas están en la sección **`<profit>` del
`config.xml`**: si en su base alguno es distinto se cambia ahí, sin tocar código.
Antes de empezar ejecute `servidor/sql/04_consultas_verificacion.sql` (solo lee)
para confirmar los nombres, los tipos de stock y si hay una tabla de códigos de barra.

## Base aparte: `SIGUEC_PURACERAMICA`

| Tabla | Para qué |
|---|---|
| `empresas`, `usuarios`, `ambientes`, `sesiones`, `bitacora` | Usuarios y accesos (misma lógica de AppSighecMovil: PBKDF2, token con SHA-256, bloqueo por claves erradas, permisos por empresa revisados en el servidor en cada petición). |
| `articulos` | Copia de los artículos de Profit (la llena "Sincronizar"). `en_profit = 0` = ya no está en Profit (no se borra porque puede tener conteos). |
| `articulo_codigos` | Todo lo que se puede escanear de cada artículo, con prioridad (co_art primero). |
| `sincronizaciones` | Cuándo, quién y cuántos artículos trajo cada sincronización. |
| `inventarios` | Un inventario preparado por almacén (A abierto / C cerrado). |
| `inventario_stock` | Foto del stock de Profit del almacén al preparar. |
| `inventario_conteos` | Conteos individuales: usuario, artículo, código leído, cantidad y hora. |

Se cuenta contra la copia (no contra Profit) para que escanear sea instantáneo y
no le pese a Profit mientras varias personas cuentan a la vez.

## Instalación

### 1. Base de datos (SQL Server Management Studio)
1. `servidor/sql/01_crear_base.sql` — crea `SIGUEC_PURACERAMICA`.
2. `servidor/sql/02_tablas.sql` — crea las tablas.
3. `servidor/sql/03_permisos_usuario_sql.sql` — usuario de SQL `siguec_puraceramica`:
   lectura/escritura en su base y **solo SELECT** en `saArticulo`, `saAlmacen`,
   `saStockAlmacen` de la empresa (cambie la clave y el nombre de la base).
4. `servidor/sql/04_consultas_verificacion.sql` — confirma los nombres de Profit 2K12.

### 2. Servidor (PC de la oficina, Windows, Python 3.12)
```
cd servidor
py -3.12 -m pip install -r requirements.txt
copy config.ejemplo.xml config.xml      (y coloque servidor, usuario y clave de SQL)
py -3.12 herramientas/usuarios.py empresa PURACERAMICA "Pura Cerámica"
py -3.12 herramientas/usuarios.py crear HCISNEROS "Haroldo Cisneros" --admin
py -3.12 herramientas/usuarios.py ambiente HCISNEROS PURACERAMICA --almacen 01 --sincronizar --preparar
py -3.12 main.py
```
Los demás usuarios se crean desde la App (Usuarios y permisos). Publicación con
Tailscale (`tailscale serve --bg 8000`) o IIS (`servidor/iis/web.config`), igual
que AppSighecMovil. Arranque automático: Programador de tareas.

### 3. App (Android Studio)
Abrir la carpeta `android/`, compilar y escribir en la App la dirección del
servidor (`https://<equipo>.ts.net`). La versión *debug* acepta `http://IP:8000`
para probar en la red local.

## Uso típico de una toma de inventario
1. La oficina **sincroniza** los artículos (Artículos → botón ↻).
2. Prepara el **inventario del almacén** (Inventarios por almacén → Preparar).
3. Cada persona entra a **Contar**, elige el inventario abierto y escanea.
4. La oficina revisa las **diferencias** (sobrantes, faltantes, no contados),
   corrige con los contadores lo necesario y **cierra** el inventario.
5. El ajuste se hace en Profit.

## La API (`/api/v1`)

| Ruta | |
|---|---|
| `POST/GET/DELETE /sesion` | Entrar, revisar token, salir |
| `GET /empresas/{e}/articulos?texto=&inventario_id=&solo_contados=` | Código, descripción y contado |
| `GET /empresas/{e}/articulos/por-codigo?codigo=&inventario_id=` | Lo escaneado → artículo |
| `POST /empresas/{e}/articulos/sincronizar` · `GET .../sincronizacion` | Sincronizar con Profit · la última |
| `GET /empresas/{e}/almacenes` | Almacenes de Profit y su inventario abierto |
| `GET/POST /empresas/{e}/inventarios` | Lista · preparar uno |
| `GET /empresas/{e}/inventarios/{id}` · `POST .../cerrar` · `GET .../comparacion` | Detalle · cerrar · diferencias |
| `GET/POST /empresas/{e}/inventarios/{id}/conteos` · `DELETE .../conteos/{c}` | Mis conteos · anotar · borrar |
| `/admin/...` | Usuarios, permisos, empresas y bitácora |

Errores siempre como `{"detalle": "mensaje"}`: 401 sesión, 403 permiso,
404 no existe, 409 conflicto (inventario cerrado o almacén con uno abierto),
422 dato no válido, 503 sin SQL Server.

## Estructura (modular: un archivo por tema)

```
servidor/
  main.py, config.ejemplo.xml, requirements.txt
  api/configuracion.py        config.xml (incluye <profit> con los nombres de 2K12)
  api/base_datos.py           pyodbc, transacciones, grabación en bloque
  api/repositorios/           TODO el SQL: profit.py (2K12, solo lectura), articulos.py,
                              inventarios.py, usuarios.py, ambientes.py, sesiones.py, bitacora.py
  api/servicios/              sincronizacion.py, articulos.py, inventarios.py, conteos.py,
                              autenticacion.py, administracion.py
  api/rutas/                  una ruta por tema
  sql/                        01..04
  tests/                      python -m pytest (bases falsas en memoria)
android/
  app/                        MainActivity (navegación) y SiguecApp
  core/red/                   API (Retrofit), DTO, repositorios, Logica.kt (+ pruebas)
  core/sesion/                Token, empresa elegida y último inventario
  core/ui/                    Tema, barra, estados y escáner (Google Code Scanner)
  feature/login, inicio, articulos, inventario, conteo, admin
```

## Pruebas
- Servidor: `cd servidor && python -m pytest` (36 pruebas con bases falsas).
- App: `cd android && gradlew :core:red:test` (cliente HTTP y lógica).
- Las pantallas se compilan en Android Studio.

## Por confirmar con la base real
- Nombres de columnas de `saArticulo` / `saAlmacen` / `saStockAlmacen` y los
  valores de `tipo` (sql/04). Si `saStockAlmacen` no tiene `tipo`, deje
  `<col_stock_tipo/>` vacío.
- Dónde están los **códigos de barra** en su 2K12: si es una columna de
  `saArticulo`, agréguela en `<columnas_codigo>`; si es una tabla aparte, en
  `<tabla_codigos_barra>`.
