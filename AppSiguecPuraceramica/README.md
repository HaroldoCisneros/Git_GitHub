# AppSiguecPuraceramica

App Android (Kotlin, Jetpack Compose) **solo para el conteo del inventario**
(toma física) contra **Profit Plus 2K12**. Modular y comentada, con la misma
estructura y la misma lógica de usuarios y accesos de **AppSighecMovil**.

```
   Teléfono (App Kotlin)                          Oficina
 ┌──────────────────────────────┐   WiFi de la    ┌───────────────────────────────────┐
 │ Base del teléfono (SQLite):  │   empresa       │ SQL Server                        │
 │  artículos, tomas, conteos   │ ──────────────► │  PURACERAMICA (Profit 2K12)       │
 │ Se cuenta SIN WiFi           │  JDBC (jTDS)    │   saArticulo: solo lectura        │
 │                              │  directo, 1433  │   toma física: recibe lo contado  │
 └──────────────────────────────┘                 │  SIGUEC_PURACERAMICA (App)        │
                                                  │   usuarios, permisos, bitácora    │
                                                  └───────────────────────────────────┘
```

## Cómo trabaja

El teléfono se conecta **directo al SQL Server por el WiFi** (sin programa
intermedio) y **solo** para:

| Momento | Qué hace | Permiso |
|---|---|---|
| **Entrar** | Verifica usuario y clave en `SIGUEC_PURACERAMICA` (misma lógica de AppSighecMovil: PBKDF2, 5 claves erradas = 15 min bloqueado). **Sin WiFi** entra con la clave de la última vez que ese usuario entró con WiFi en ese teléfono. | — |
| **Sincronizar artículos** | Baja `saArticulo` (código, descripción, anulado, modelo, ref; sin los servicios `tipo = 'S'`) al teléfono, con los códigos para escanear. | `sincronizar_articulos` |
| **Traer inventarios** | Trae las **tomas físicas abiertas en Profit** al teléfono. | `contar_inventario` |
| **Enviar (cerrar)** | Manda lo contado a la **toma física de Profit** (y deja auditoría en `envios` / `envio_conteos`). Profit se encarga de cerrarla. | `cerrar_inventario` |

Todo lo demás es **sin WiFi**, en la base del teléfono:

- **Artículos**: consulta de código, descripción y stock contado (si ya se contó en la toma elegida).
- **Contar**: se elige la toma abierta y se **escanea** el código de barras; muestra el
  artículo y lo ya contado; se escribe la cantidad y "Anotar". **Modo rápido**: cada
  escaneo anota 1. Cada conteo es **individual** (queda a nombre de quien cuenta);
  el stock contado es la suma. Se puede borrar un conteo propio mientras no se haya enviado.

Si se corta el WiFi justo al enviar, se vuelve a tocar "Enviar": el teléfono usa la
misma **clave de envío** y SQL Server no lo recibe dos veces.

## Configuración (botón en la entrada y en el menú)

Servidor (IP o nombre), puerto (1433) o instancia, usuario y clave de SQL Server, y
base de la App. "Probar conexión" verifica sin guardar. La clave de SQL guardada
**nunca se muestra** (solo se puede escribir una nueva).

**Seguridad de la conexión directa** (decisión tomada): la clave del usuario de SQL
queda guardada en cada teléfono. Por eso:
- Usuario de SQL exclusivo `siguec_puraceramica` con lo mínimo (sql/03): su base, `SELECT`
  en `saArticulo` y lo justo de la toma física. Nunca `sa` ni `profit`.
- El puerto 1433 solo accesible desde el WiFi de la empresa (firewall), nunca desde internet.
- `allowBackup=false`: los datos no se copian a la nube.

## Profit 2K12

`saArticulo` confirmado con el script de la base real: `co_art char(30)`,
`art_des varchar(120)`, `tipo char(1)` (V venta, C consumo, **S servicio**,
F fabricación, M materia prima, N envase, E empaque), `anulado bit`,
`modelo varchar(20)` y `ref varchar(20)` ("otra codificación para el artículo").
**No tiene columna de código de barras**: el código de barras puede venir en
`co_art`, `modelo` o `ref`, y se busca en los tres (si coincide en varios, gana
co_art, luego modelo, luego ref; sin distinguir mayúsculas).

**Etiquetas con la fuente C39HrP48DhTt** = Code 39: el escáner la lee (es el primer
formato configurado). Se quitan los asteriscos de inicio y fin (`*CER-001*`).
Code 39 solo codifica MAYÚSCULAS, números, espacio y `- . $ / + %`: un código con
otros caracteres (ñ, _, #, minúsculas) no sale bien en la etiqueta.

Si algún nombre es distinto o hay otra columna/tabla con los códigos de barra, no
se cambia la App: se agrega un renglón en `SIGUEC_PURACERAMICA.dbo.parametros`:

| clave | defecto |
|---|---|
| `profit.tabla_articulos` | `saArticulo` |
| `profit.columnas_codigo` | `modelo,ref` (ej. `modelo,ref,campo1`) |
| `profit.tipos_excluidos` | `S` |
| `profit.tabla_codigos_barra` / `profit.col_codigo_barra` / `profit.col_codigo_barra_articulo` | vacío / `cod_barra` / `co_art` |

**PENDIENTE: tablas de la toma física de Profit 2K12.** Todo su SQL va en UN solo
archivo (`android/core/red/.../TomaFisica.kt`): leer las tomas abiertas y grabar
lo contado. Mientras tanto, "Traer de Profit" y "Enviar" avisan que falta configurarlas.

## Instalación

1. **SQL Server Management Studio**: `sql/01_crear_base.sql`, `sql/02_tablas.sql`
   (crea el usuario **ADMIN / cambiar123**: cámbiele la clave al entrar),
   `sql/03_permisos_usuario_sql.sql` (cambie la clave), y para revisar los nombres
   `sql/04_consultas_verificacion.sql`.
2. Registrar la empresa: `INSERT INTO SIGUEC_PURACERAMICA.dbo.empresas (co_empresa, nombre) VALUES ('PURACERAMICA', 'Pura Cerámica')`.
3. **Android Studio**: abrir `android/` y compilar. En el teléfono: Configuración →
   datos del SQL Server → Probar conexión → Guardar. Entrar con ADMIN y crear los
   usuarios (Usuarios y permisos).

## Uso típico
1. En Profit se abre la toma física del almacén.
2. Con WiFi: **Sincronizar artículos** y **Inventarios → Traer de Profit**.
3. Sin WiFi: cada persona entra con su usuario y **cuenta** escaneando.
4. Con WiFi: **Inventarios → Enviar a Profit**. En Profit se revisa y se cierra la toma.

## Estructura (modular: un archivo por tema)

```
sql/                    01..04 (base de la App, permisos y verificación)
android/
  app/                  MainActivity (navegación) y SiguecApp
  core/red/             Conexión DIRECTA (JDBC jTDS, Kotlin puro): BaseDatos, ConfigConexion,
                        Seguridad (PBKDF2), RepositorioAcceso, RepositorioAdmin, Sincronizacion
                        (saArticulo), RepositorioInventarioProfit, TomaFisica (PENDIENTE), Logica
  core/local/           Base del teléfono (SQLite): ArticulosLocales, InventariosLocales
  core/sesion/          Configuración, sesión, usuarios recordados (entrar sin WiFi)
  core/ui/              Tema, barra, estados y escáner (Google Code Scanner)
  feature/login         Entrada, Configuración de la conexión, elegir empresa
  feature/inicio        Menú
  feature/articulos     Consultar y sincronizar
  feature/inventario    Traer tomas de Profit y enviar
  feature/conteo        Contar escaneando
  feature/admin         Usuarios, permisos y bitácora
```

## Pruebas
`cd android && gradlew :core:red:test`: 15 pruebas (conexión, nombres de Profit,
sincronización, totales del envío, claves compatibles con AppSighecMovil y el ADMIN
del script SQL). Las pantallas y la base del teléfono se compilan en Android Studio.
