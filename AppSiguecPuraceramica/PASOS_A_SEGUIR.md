# AppConteodeInventarioPura — Pasos a seguir

App Android para la **toma de inventario** contra **Profit Plus 2K12**. El teléfono
se conecta **directo al SQL Server por el WiFi** de la empresa y **cuenta sin WiFi**.
Detalle técnico en `README.md`; decisiones en `CONTEXTO.md`.

---

## Paso 1. Base de datos (SQL Server Management Studio, en el servidor de Profit)

Ejecutar en este orden (carpeta `sql/`):

1. **`01_crear_base.sql`** — crea la base propia `SIGUEC_PURACERAMICA`.
2. **`02_tablas.sql`** — crea las tablas (usuarios, permisos, bitácora, aperturas,
   envíos) y el primer usuario **ADMIN** con clave **`cambiar123`**.
3. **`03_permisos_usuario_sql.sql`** — ANTES de ejecutarlo:
   - cambie `CAMBIAR_ESTA_CLAVE` por una clave segura (se escribirá en los teléfonos);
   - cambie `USE PURACERAMICA;` por el **nombre real de la base de la empresa**
     (en sus scripts aparece `SAPURA_A`).
   Crea el usuario de SQL `siguec_puraceramica` con lo mínimo: su base, solo lectura en
   `saArticulo`, `saInventarioFisico`, `saAlmacen`, `saArtUnidad` y `saStockAlmacen`, e
   INSERT en `saResInventario` y `saResInventarioReng` (el resultado de lo contado), y
   EXECUTE en los procedimientos de Profit `pInsertarResInventario` y
   `pInsertarRenglonesResInventario` (así queda la pista de auditoría de Profit), en `psObtenerProximoNumero`
   (correlativo del resultado; si no existe, ejecute antes `06_procedimiento_proximo_numero.sql`)
   y en la función `ConsultarUltimoCosto` (costo unitario de cada renglón; si su base
   no la tiene, ejecute antes `05_funcion_ultimo_costo.sql`).
4. **Registrar la empresa** (use el nombre real de la base):
   ```sql
   INSERT INTO SIGUEC_PURACERAMICA.dbo.empresas (co_empresa, nombre)
   VALUES ('SAPURA_A', 'Pura Cerámica');
   ```
5. **Darle la empresa al ADMIN** (permisos de todo):
   ```sql
   INSERT INTO SIGUEC_PURACERAMICA.dbo.ambientes
     (usuario_id, co_empresa, almacen, puede_consultar_articulos, puede_sincronizar_articulos,
      puede_contar_inventario, puede_cerrar_inventario)
   SELECT id, 'SAPURA_A', NULL, 1, 1, 1, 1 FROM SIGUEC_PURACERAMICA.dbo.usuarios WHERE usuario = 'ADMIN';
   ```
6. (Opcional) `04_consultas_verificacion.sql` — solo consulta; sirve para revisar
   los nombres de las tablas de Profit.

> Si ya había ejecutado los scripts antes, vuelva a ejecutar `02_tablas.sql` y
> `03_permisos_usuario_sql.sql`: solo agregan lo que falta.

## Paso 2. Red

1. En el servidor SQL: **SQL Server Configuration Manager → Protocolos → TCP/IP = Habilitado**,
   puerto **1433** (o anote la instancia, ej. `SQLEXPRESS`, y deje corriendo
   **SQL Server Browser**). Reinicie el servicio de SQL Server.
2. **Firewall de Windows**: permitir entrada TCP **1433** solo desde la red WiFi de la
   empresa (por ejemplo `192.168.1.0/24`). **Nunca** abrir el 1433 a internet.
3. SQL Server en modo de autenticación **mixto** (SQL Server y Windows).
4. Anote la **IP fija** del servidor (ej. `192.168.1.10`).

## Paso 3. Compilar la App (Android Studio)

1. Instale **Android Studio** (versión reciente) y abra la carpeta **`android/`**.
2. Espere a que Gradle descargue todo (necesita internet la primera vez).
3. Conecte el teléfono (o colector) con **Depuración USB** y toque **Run ▶**.
   Para repartirla: **Build → Generate Signed App Bundle / APK → APK**.
4. Requisitos del teléfono: Android 8 o superior y **Google Play Services** actualizado
   (el escáner de códigos de barra es el de Google y no pide permiso de cámara).

> Nota: en la sesión de desarrollo el módulo de conexión (`core/red`) se compiló y
> pasó sus 17 pruebas, pero las pantallas no se pudieron compilar allí (sin acceso a
> Google Maven). Si Android Studio marca algún error al compilar, envíeme el mensaje.

## Paso 4. Configurar el teléfono

1. Abra la App → botón **Configuración** (arriba a la derecha o abajo en la entrada).
2. Escriba: **Servidor** (IP del SQL Server), **Puerto** `1433` (o la **Instancia**),
   **Usuario** `siguec_puraceramica`, la **Clave** del Paso 1.3 y **Base de la App**
   `SIGUEC_PURACERAMICA`.
3. **Probar conexión** → debe decir "Conexión correcta".
4. **Moneda del resultado de inventario** → **Buscar monedas en Profit** (lista `saMoneda`)
   → toque la moneda (ej. Bolívares). Se hace una sola vez. Luego **Guardar**.
   Al enviar, la tasa se toma del **cambio** de esa moneda en Profit en ese momento.
5. Entre con **ADMIN / cambiar123** y en **Usuarios y permisos → ADMIN → Cambiar clave**
   póngale una clave nueva.
6. Cree los usuarios de los que cuentan (**Usuarios y permisos → +**) y deles la empresa
   con sus permisos:
   - *Consultar artículos*, *Contar*: los que cuentan.
   - *Sincronizar artículos*, *Cerrar (enviar a Profit)*: el encargado.

## Paso 5. Uso en una toma de inventario

1. **En Profit**: cree el inventario físico del almacén (queda sin procesar).
2. **Con WiFi** en la App:
   - **Artículos → ↻ Sincronizar**: baja los artículos de Profit al teléfono
     (sin los servicios).
   - **Inventarios → Abrir inventario**: elija el inventario de Profit, revise sus datos
     y toque **Abrir** (queda guardado con su usuario y la hora).
3. **Sin WiFi**: **Contar** → **Escanear código de barras** (lee las etiquetas Code 39 de
   la fuente C39HrP48DhTt y los EAN de fábrica; busca en `co_art`, `modelo` y `ref`) →
   cantidad → **Anotar**. *Modo rápido*: cada escaneo anota 1.
   Cada conteo queda a nombre de quien cuenta; se puede borrar un conteo propio.
4. **Artículos**: consulta código, descripción y stock contado del inventario elegido.
5. **Cerrar** (con WiFi): Inventarios → **Enviar a Profit**. Se crea en Profit un
   **resultado de inventario** (`saResInventario` / `saResInventarioReng`, con el **correlativo
   de Profit** de `psObtenerProximoNumero`)
   con el usuario y la sucursal del registro del inventario y el **último costo** de cada
   artículo (`ConsultarUltimoCosto`). Lo normal es **un teléfono por almacén / inventario
   abierto**; si cuentan varios, cada uno envía su propio resultado. Luego, en Profit, se
   procesa el inventario físico.

## Por confirmar en la primera prueba

- Que Profit **sume varios resultados** del mismo inventario al procesarlo (cada teléfono
  envía el suyo). Pruebe primero con dos teléfonos y pocos artículos.
- La **unidad** de cada renglón sale de `saArtUnidad` (`uni_principal = 1`): revise en Profit que
  el resultado se vea bien antes de procesarlo.

## Si algo falla

| Mensaje | Qué revisar |
|---|---|
| "No se pudo conectar con el servidor..." | WiFi de la empresa, IP/puerto en Configuración, TCP/IP habilitado, firewall. |
| "El usuario o la clave de SQL Server no son correctos" | Paso 1.3 y Configuración; autenticación mixta. |
| "No se pudo abrir la base ..." | Nombre de la base de la App o de la empresa (tabla `empresas`). |
| "El usuario de SQL Server no tiene permiso..." | Volver a ejecutar `03_permisos_usuario_sql.sql` con la base correcta. |
| "No hay ningún artículo con el código..." | Sincronizar artículos; revisar que el código esté en co_art, modelo o ref. |
| "Falta la moneda del resultado" / "La moneda X no existe en Profit" | Paso 4.4: Configuración → Buscar monedas en Profit → elegirla → Guardar. |
| "Estos artículos no tienen unidad en Profit" | Asignarles unidad en Profit (saArtUnidad). |
| "No existe el procedimiento dbo.psObtenerProximoNumero" | Ejecutar `sql/06_procedimiento_proximo_numero.sql` y luego `03_permisos_usuario_sql.sql`. |
| "No existe la función dbo.ConsultarUltimoCosto" | Ejecutar `sql/05_funcion_ultimo_costo.sql` y luego `03_permisos_usuario_sql.sql`. |
| "El inventario ... no tiene almacén" | El inventario físico de Profit debe tener almacén para enviar el resultado. |
| La etiqueta no lee | Tamaño/nitidez de la impresión; el código debe ir entre `*` y con margen blanco. |
