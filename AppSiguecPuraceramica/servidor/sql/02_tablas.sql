/* =====================================================================
   02_tablas.sql
   Tablas de la base SIGUEC_PURACERAMICA.

   Usuarios y accesos (misma lógica de AppSighecMovil):
       empresas            Las bases de Profit 2K12 que la App puede usar.
       usuarios            Usuarios de la App (clave con hash PBKDF2, nunca en texto).
       ambientes           Qué empresa puede usar cada usuario y con qué permisos.
       sesiones            Tokens entregados a los teléfonos (solo su SHA-256).
       bitacora            Lo que se hace desde la App.

   Toma de inventario:
       articulos           COPIA de los artículos de Profit (saArticulo) para contar.
                           Se llena con la acción "Sincronizar artículos".
       articulo_codigos    Todos los códigos que se pueden escanear de cada artículo
                           (co_art, modelo, referencia, códigos de barra).
       sincronizaciones    Cuándo y quién sincronizó, y cuántos artículos trajo.
       inventarios         Un inventario PREPARADO (abierto) por almacén.
       inventario_stock    El stock de Profit de cada artículo en ese almacén, tomado
                           al preparar el inventario (la "foto" contra la que se compara).
       inventario_conteos  Cada conteo individual: quién contó, qué artículo y cuánto.
                           El stock contado de un artículo es la SUMA de sus conteos.

   NUNCA se escribe en Profit: la oficina compara y hace el ajuste en Profit.
   Se puede ejecutar varias veces: solo crea lo que falta.
   ===================================================================== */

USE SIGUEC_PURACERAMICA;
GO

/* ---------- empresas ----------
   co_empresa = NOMBRE DE LA BASE de Profit 2K12 (ej. PURACERAMICA).        */
IF OBJECT_ID('dbo.empresas') IS NULL
CREATE TABLE dbo.empresas (
    co_empresa      varchar(30)   NOT NULL CONSTRAINT pk_empresas PRIMARY KEY,
    nombre          varchar(100)  NOT NULL,                  -- Nombre que ve el usuario
    activa          bit           NOT NULL CONSTRAINT df_empresas_activa DEFAULT 1,
    fecha_creacion  datetime      NOT NULL CONSTRAINT df_empresas_fecha DEFAULT GETDATE()
);
GO

/* ---------- usuarios ---------- */
IF OBJECT_ID('dbo.usuarios') IS NULL
CREATE TABLE dbo.usuarios (
    id                 int IDENTITY(1,1) NOT NULL CONSTRAINT pk_usuarios PRIMARY KEY,
    usuario            varchar(30)   NOT NULL,               -- Código para entrar (en mayúsculas)
    nombre             varchar(100)  NOT NULL,
    clave_hash         varchar(200)  NOT NULL,               -- pbkdf2_sha256$rep$sal$hash
    usuario_profit     varchar(10)   NULL,                   -- Usuario de Profit (referencia)
    es_administrador   bit           NOT NULL CONSTRAINT df_usuarios_admin DEFAULT 0,
    activo             bit           NOT NULL CONSTRAINT df_usuarios_activo DEFAULT 1,
    intentos_fallidos  int           NOT NULL CONSTRAINT df_usuarios_intentos DEFAULT 0,
    bloqueado_hasta    datetime      NULL,                   -- Bloqueo temporal por claves erradas
    fecha_creacion     datetime      NOT NULL CONSTRAINT df_usuarios_fecha DEFAULT GETDATE(),
    ultimo_acceso      datetime      NULL,
    CONSTRAINT uq_usuarios_usuario UNIQUE (usuario)
);
GO

/* ---------- ambientes ----------
   Un renglón por usuario y empresa. Los permisos se revisan en la API en CADA
   petición (no basta con esconder el botón en el teléfono).                */
IF OBJECT_ID('dbo.ambientes') IS NULL
CREATE TABLE dbo.ambientes (
    id                          int IDENTITY(1,1) NOT NULL CONSTRAINT pk_ambientes PRIMARY KEY,
    usuario_id                  int          NOT NULL CONSTRAINT fk_ambientes_usuario REFERENCES dbo.usuarios (id),
    co_empresa                  varchar(30)  NOT NULL CONSTRAINT fk_ambientes_empresa REFERENCES dbo.empresas (co_empresa),
    almacen                     varchar(6)   NULL,           -- Almacén por defecto al preparar
    puede_consultar_articulos   bit NOT NULL CONSTRAINT df_amb_consultar   DEFAULT 1,  -- Código, descripción y contado
    puede_sincronizar_articulos bit NOT NULL CONSTRAINT df_amb_sincronizar DEFAULT 0,  -- Bajar artículos de Profit
    puede_preparar_inventario   bit NOT NULL CONSTRAINT df_amb_preparar    DEFAULT 0,  -- Abrir/cerrar y comparar
    puede_contar_inventario     bit NOT NULL CONSTRAINT df_amb_contar      DEFAULT 1,  -- Escanear y anotar
    activo                      bit NOT NULL CONSTRAINT df_amb_activo      DEFAULT 1,
    CONSTRAINT uq_ambientes UNIQUE (usuario_id, co_empresa)
);
GO

/* ---------- sesiones ---------- */
IF OBJECT_ID('dbo.sesiones') IS NULL
CREATE TABLE dbo.sesiones (
    id           bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_sesiones PRIMARY KEY,
    token_hash   char(64)      NOT NULL,                     -- SHA-256 del token (no el token)
    usuario_id   int           NOT NULL CONSTRAINT fk_sesiones_usuario REFERENCES dbo.usuarios (id),
    creada       datetime      NOT NULL CONSTRAINT df_sesiones_creada DEFAULT GETDATE(),
    expira       datetime      NOT NULL,
    ultimo_uso   datetime      NULL,
    dispositivo  varchar(100)  NULL,                         -- Modelo del teléfono
    ip           varchar(45)   NULL,
    revocada     bit           NOT NULL CONSTRAINT df_sesiones_revocada DEFAULT 0,
    CONSTRAINT uq_sesiones_token UNIQUE (token_hash)
);
GO

/* ---------- bitacora ---------- */
IF OBJECT_ID('dbo.bitacora') IS NULL
CREATE TABLE dbo.bitacora (
    id          bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_bitacora PRIMARY KEY,
    fecha       datetime       NOT NULL CONSTRAINT df_bitacora_fecha DEFAULT GETDATE(),
    usuario_id  int            NULL,
    usuario     varchar(30)    NULL,                         -- Lo escrito (aunque el usuario no exista)
    co_empresa  varchar(30)    NULL,
    accion      varchar(30)    NOT NULL,                     -- ENTRADA, SINCRONIZAR, INVENTARIO_ABRIR...
    detalle     nvarchar(max)  NULL,
    ip          varchar(45)    NULL
);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_bitacora_fecha')
    CREATE INDEX ix_bitacora_fecha ON dbo.bitacora (fecha);
GO

/* ---------- articulos (copia de saArticulo) ---------- */
IF OBJECT_ID('dbo.articulos') IS NULL
CREATE TABLE dbo.articulos (
    co_empresa        varchar(30)   NOT NULL,
    co_art            varchar(30)   NOT NULL,
    art_des           varchar(150)  NOT NULL,
    anulado           bit           NOT NULL CONSTRAINT df_articulos_anulado DEFAULT 0,
    en_profit         bit           NOT NULL CONSTRAINT df_articulos_en_profit DEFAULT 1, -- 0 = ya no está en Profit
    fecha_sync        datetime      NOT NULL CONSTRAINT df_articulos_fecha DEFAULT GETDATE(),
    CONSTRAINT pk_articulos PRIMARY KEY (co_empresa, co_art)
);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_articulos_descripcion')
    CREATE INDEX ix_articulos_descripcion ON dbo.articulos (co_empresa, art_des);
GO

/* ---------- articulo_codigos ----------
   Todo lo que se puede escanear: co_art, modelo, ref y códigos de barra.
   origen = de dónde salió (co_art, modelo, ref, barra). Un mismo código puede
   estar en dos artículos: al escanear gana la prioridad más baja.          */
IF OBJECT_ID('dbo.articulo_codigos') IS NULL
CREATE TABLE dbo.articulo_codigos (
    co_empresa  varchar(30)   NOT NULL,
    codigo      varchar(60)   NOT NULL,
    co_art      varchar(30)   NOT NULL,
    origen      varchar(30)   NOT NULL,
    prioridad   int           NOT NULL,                      -- 0 = co_art, 1.. = las demás columnas
    CONSTRAINT pk_articulo_codigos PRIMARY KEY (co_empresa, codigo, co_art)
);
GO

/* ---------- sincronizaciones ---------- */
IF OBJECT_ID('dbo.sincronizaciones') IS NULL
CREATE TABLE dbo.sincronizaciones (
    id            int IDENTITY(1,1) NOT NULL CONSTRAINT pk_sincronizaciones PRIMARY KEY,
    co_empresa    varchar(30)   NOT NULL,
    fecha         datetime      NOT NULL CONSTRAINT df_sinc_fecha DEFAULT GETDATE(),
    usuario       varchar(30)   NOT NULL,
    leidos        int           NOT NULL,                    -- Artículos leídos de Profit
    nuevos        int           NOT NULL,
    actualizados  int           NOT NULL,
    retirados     int           NOT NULL,                    -- Estaban en la copia y ya no en Profit
    codigos       int           NOT NULL                     -- Códigos para escanear
);
GO

/* ---------- inventarios ----------
   Se PREPARA uno por almacén: queda abierto (A) para contar y luego se
   cierra (C). Solo puede haber UN inventario abierto por almacén.          */
IF OBJECT_ID('dbo.inventarios') IS NULL
CREATE TABLE dbo.inventarios (
    id              int IDENTITY(1,1) NOT NULL CONSTRAINT pk_inventarios PRIMARY KEY,
    co_empresa      varchar(30)   NOT NULL CONSTRAINT fk_inventarios_empresa REFERENCES dbo.empresas (co_empresa),
    co_alma         varchar(6)    NOT NULL,                  -- Almacén de Profit que se cuenta
    des_alma        varchar(100)  NULL,
    descripcion     varchar(100)  NOT NULL,
    estado          char(1)       NOT NULL CONSTRAINT df_inventarios_estado DEFAULT 'A',  -- A abierto, C cerrado
    creado_por      varchar(30)   NOT NULL,
    fecha_creacion  datetime      NOT NULL CONSTRAINT df_inventarios_fecha DEFAULT GETDATE(),
    cerrado_por     varchar(30)   NULL,
    fecha_cierre    datetime      NULL
);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_inventarios_empresa')
    CREATE INDEX ix_inventarios_empresa ON dbo.inventarios (co_empresa, estado);
GO

/* ---------- inventario_stock (foto del stock de Profit al preparar) ---------- */
IF OBJECT_ID('dbo.inventario_stock') IS NULL
CREATE TABLE dbo.inventario_stock (
    inventario_id  int             NOT NULL CONSTRAINT fk_stock_inventario REFERENCES dbo.inventarios (id),
    co_art         varchar(30)     NOT NULL,
    stock          decimal(18, 5)  NOT NULL,
    CONSTRAINT pk_inventario_stock PRIMARY KEY (inventario_id, co_art)
);
GO

/* ---------- inventario_conteos (conteos individuales) ---------- */
IF OBJECT_ID('dbo.inventario_conteos') IS NULL
CREATE TABLE dbo.inventario_conteos (
    id             bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_inventario_conteos PRIMARY KEY,
    inventario_id  int             NOT NULL CONSTRAINT fk_conteos_inventario REFERENCES dbo.inventarios (id),
    co_art         varchar(30)     NOT NULL,
    codigo_leido   varchar(60)     NOT NULL,                 -- Lo que se escaneó o escribió
    cantidad       decimal(18, 5)  NOT NULL,
    usuario        varchar(30)     NOT NULL,                 -- Quién contó
    fecha          datetime        NOT NULL CONSTRAINT df_conteos_fecha DEFAULT GETDATE()
);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_conteos_inventario')
    CREATE INDEX ix_conteos_inventario ON dbo.inventario_conteos (inventario_id, co_art);
GO

PRINT 'Tablas de SIGUEC_PURACERAMICA listas.';
GO
