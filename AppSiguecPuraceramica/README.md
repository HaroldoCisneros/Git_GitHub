# AppConteodeInventarioPura (AppSiguecPuraceramica)

**Empiece por [PASOS_A_SEGUIR.md](PASOS_A_SEGUIR.md).**

App Android **Conteo de Inventario Puraceramica** (Kotlin, Jetpack Compose) **solo para el conteo del inventario**
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
| **Abrir inventario** | Consulta en Profit los inventarios físicos creados y sin procesar (`saInventarioFisico.procesado = 0`, con el nombre del almacén de `saAlmacen`). Se elige uno, se ven sus datos y se toca **Abrir**: queda en el teléfono con el **usuario y la hora** (también en `SIGUEC_PURACERAMICA.dbo.aperturas` y la bitácora). | `contar_inventario` |
| **Enviar (cerrar)** | Graba lo contado como **resultado de inventario** en Profit: `saResInventario` (encabezado) + `saResInventarioReng` (un renglón por artículo con la suma de los conteos), con el **usuario y la sucursal del registro del inventario** (`saInventarioFisico.co_us_in` / `co_sucu_in`, guardados al abrirlo). Queda auditoría en `envios` / `envio_conteos`. Profit lo procesa y cierra el inventario. | `cerrar_inventario` |

Todo lo demás es **sin WiFi**, en la base del teléfono:

- **Artículos**: consulta de código, descripción y stock contado (si ya se contó en la toma elegida).
- **Contar**: se elige el inventario abierto (asociado a su almacén) y se **escanea** el código de barras; muestra el
  artículo y lo ya contado; se escribe la cantidad y "Anotar". **Modo rápido**: cada
  escaneo anota 1. Cada conteo es **individual** (queda a nombre de quien cuenta);
  el stock contado es la suma. Se puede borrar un conteo propio mientras no se haya enviado.

Si se corta el WiFi justo al enviar, se vuelve a tocar "Enviar": el teléfono usa la
misma **clave de envío** y SQL Server no lo recibe dos veces.

## Configuración (botón en la entrada y en el menú)

Servidor (IP o nombre), puerto (1433) o instancia, usuario y clave de SQL Server
(los crea `sql/03`), base de la App, **base de datos de Profit** (la empresa, ej. `SAPURA_A`) y **moneda del resultado de inventario** (se elige una vez de `saMoneda`
de Profit con "Buscar monedas en Profit"; la tasa es su `cambio` al enviar).
"Probar conexión" verifica las dos bases sin guardar; "Guardar" verifica y guarda. La
primera vez registra la empresa en la App y se la da a los administradores (no hay que
hacerlo a mano en SQL). La clave de SQL guardada
**nunca se muestra** (solo se puede escribir una nueva).

**Protegida**: una vez configurada, Configuración solo se abre con **huella** (o clave) de un
usuario con permiso **"Puede cambiar la Configuración"** (se da en la ficha del usuario; pueden
ser varios) o de un administrador. Todos los datos, incluidas las **bases de datos**, se
pueden cambiar más adelante; si se cambia la base de Profit se cierra la sesión y se entra
con la empresa nueva.

## Entrada con huella

Después de entrar con la clave, la App ofrece **activar la huella** en ese teléfono. La
clave queda **cifrada con una llave del Android Keystore que solo se abre con la huella**
(`core/ui/huella/Huella.kt`); al entrar con huella se descifra y se entra igual que con la
clave (el servidor revisa clave, bloqueo y permisos como siempre). Si se agrega una huella
al teléfono o cambia la clave, hay que volver a activarla. Android no distingue de quién es
la huella: cada usuario la activa solo en su propio teléfono.

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

**Toma física (`saInventarioFisico`)**: `co_invfisico char(20)`, `des_invfisico`,
`co_alma` (puede venir vacío = todos los almacenes), `inicio`, `art_cero` (los no
contados quedan en cero al cerrarlo) y `procesado` (0 iniciado, 1 cerrado). Todo su SQL
está en UN solo archivo: `android/core/red/.../TomaFisica.kt`.

**Resultado de inventario** (lo que graba "Enviar a Profit"):

| Campo | Valor |
|---|---|
| `saResInventario.num_resinv` | **Correlativo de Profit**: `dbo.psObtenerProximoNumero` (último `num_resinv` + 1, con sus ceros a la izquierda), en la misma transacción del envío. Prefijo opcional en `profit.prefijo_resultado` (vacío = solo números) |
| `des_resinv` | `App <usuario> <teléfono>` |
| `fecha`, `fe_us_in`, `fe_us_mo` | Hora del servidor |
| `co_invfisico`, `co_alma` | Los del inventario abierto (si el inventario no tiene almacén, no se puede enviar) |
| `co_mone`, `tasa` | La moneda elegida en **Configuración** del teléfono (lista de `saMoneda`) y su `cambio` en Profit al momento de enviar |
| `co_us_in/mo`, `co_sucu_in/mo` | Usuario y sucursal del **registro del inventario** en `saInventarioFisico` |
| `saResInventarioReng.reng_num` | 1, 2, 3... (un renglón por artículo) |
| `co_uni` | Unidad principal del artículo: `saArtUnidad.uni_principal = 1` |
| `total_art` | Stock contado (suma de los conteos del teléfono) |
| `total_art_teo` | Existencia actual en el almacén (`saStockAlmacen`, tipo `ACT`; 0 si no se puede leer) |
| `cost_unit` | `dbo.ConsultarUltimoCosto(saArticulo.rowguid)`: último costo de entrada (sin REPO ni PROV) |
| `sco_uni`, `stotal_art_teo`, `stotal_art` | NULL / 0 / 0 |

El **encabezado** se graba con el procedimiento de Profit `pInsertarResInventario` y los
**renglones** con `pInsertarRenglonesResInventario`: así todo queda en la **pista de
auditoría de Profit** (con el teléfono como máquina), igual que si se cargara en Profit.

Uso normal: **un teléfono por almacén / inventario abierto**. Si varios teléfonos
cuentan el mismo inventario, cada uno envía **su propio resultado**.

## Instalación

1. **SQL Server Management Studio**: `sql/01_crear_base.sql`, `sql/02_tablas.sql`
   (crea el usuario **ADMIN / cambiar123**: cámbiele la clave al entrar),
   `sql/03_permisos_usuario_sql.sql` (**crea el usuario de SQL `siguec_puraceramica`**; cambie
   la clave), y para revisar los nombres `sql/04_consultas_verificacion.sql`.
2. **Android Studio**: abrir `android/` y compilar. En el teléfono: Configuración →
   datos del SQL Server y base de Profit (ej. `SAPURA_A`) → Probar conexión → moneda →
   Guardar (registra la empresa en la App para el ADMIN). Entrar con ADMIN y crear los
   usuarios (Usuarios y permisos).

## Uso típico
1. En Profit se abre la toma física del almacén.
2. Con WiFi: **Sincronizar artículos** y **Inventarios → Abrir inventario** (elegir el de Profit → Abrir).
3. Sin WiFi: cada persona entra con su usuario y **cuenta** escaneando.
4. Con WiFi: **Inventarios → Enviar a Profit**. En Profit se revisa y se cierra la toma.

## Estructura (modular: un archivo por tema)

```
sql/                    01..04 (base de la App, permisos y verificación)
android/
  app/                  MainActivity (navegación) y SiguecApp
  core/red/             Conexión DIRECTA (JDBC jTDS, Kotlin puro): BaseDatos, ConfigConexion,
                        Seguridad (PBKDF2), RepositorioAcceso, RepositorioAdmin, Sincronizacion
                        (saArticulo), RepositorioInventarioProfit, TomaFisica (saInventarioFisico y saResInventario), Logica
  core/local/           Base del teléfono (SQLite): ArticulosLocales, InventariosLocales
  core/sesion/          Configuración, sesión, usuarios recordados (entrar sin WiFi)
  core/ui/              Tema (rojos del logo), logos de Puracerámica (res/drawable-nodpi),
                        barra, estados y escáner (Google Code Scanner)
  feature/login         Entrada, Configuración de la conexión, elegir empresa
  feature/inicio        Menú
  feature/articulos     Consultar y sincronizar
  feature/inventario    Traer tomas de Profit y enviar
  feature/conteo        Contar escaneando
  feature/admin         Usuarios, permisos y bitácora
```

## Pruebas
`cd android && gradlew :core:red:test`: 18 pruebas (conexión, nombres de Profit,
sincronización, totales del envío, claves compatibles con AppSighecMovil y el ADMIN
del script SQL). Las pantallas y la base del teléfono se compilan en Android Studio.
