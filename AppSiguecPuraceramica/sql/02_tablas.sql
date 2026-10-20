/* =====================================================================
   02_tablas.sql
   Tablas de la base SIGUEC_PURACERAMICA (la base PROPIA de la App).

   El teléfono se conecta DIRECTO a este SQL Server por el WiFi de la
   empresa (sin programa intermedio). Trabaja sin conexión y solo se
   conecta para: entrar, sincronizar artículos, traer los inventarios
   abiertos en Profit y enviar el conteo a la toma física de Profit.

   Usuarios y accesos (misma lógica de AppSighecMovil):
       empresas         Las bases de Profit 2K12 que la App puede usar.
       usuarios         Usuarios de la App (clave con hash PBKDF2, nunca en texto).
       ambientes        Qué empresa puede usar cada usuario y con qué permisos.
       bitacora         Lo que se hace desde la App.
   Configuración:
       parametros       Nombres de tablas/columnas de Profit 2K12 si son distintos
                        a los de fábrica (la App los lee al conectarse).
   Inventarios abiertos en la App:
       aperturas        Quién abrió cada inventario físico de Profit, en qué teléfono y cuándo.
   Envíos a Profit (auditoría):
       envios           Cada envío de un conteo a la toma física de Profit.
       envio_conteos    Los conteos individuales que se enviaron (quién, qué, cuánto).

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
    usuario_profit     varchar(10)   NULL,                   -- Usuario de Profit (se graba en la toma física)
    es_administrador   bit           NOT NULL CONSTRAINT df_usuarios_admin DEFAULT 0,
    activo             bit           NOT NULL CONSTRAINT df_usuarios_activo DEFAULT 1,
    intentos_fallidos  int           NOT NULL CONSTRAINT df_usuarios_intentos DEFAULT 0,
    bloqueado_hasta    datetime      NULL,                   -- Bloqueo temporal por claves erradas
    fecha_creacion     datetime      NOT NULL CONSTRAINT df_usuarios_fecha DEFAULT GETDATE(),
    ultimo_acceso      datetime      NULL,
    CONSTRAINT uq_usuarios_usuario UNIQUE (usuario)
);
GO

/* ---------- ambientes ---------- */
IF OBJECT_ID('dbo.ambientes') IS NULL
CREATE TABLE dbo.ambientes (
    id                          int IDENTITY(1,1) NOT NULL CONSTRAINT pk_ambientes PRIMARY KEY,
    usuario_id                  int          NOT NULL CONSTRAINT fk_ambientes_usuario REFERENCES dbo.usuarios (id),
    co_empresa                  varchar(30)  NOT NULL CONSTRAINT fk_ambientes_empresa REFERENCES dbo.empresas (co_empresa),
    almacen                     varchar(6)   NULL,           -- Almacén por defecto
    puede_consultar_articulos   bit NOT NULL CONSTRAINT df_amb_consultar   DEFAULT 1,  -- Código, descripción y contado
    puede_sincronizar_articulos bit NOT NULL CONSTRAINT df_amb_sincronizar DEFAULT 0,  -- Bajar artículos de Profit
    puede_contar_inventario     bit NOT NULL CONSTRAINT df_amb_contar      DEFAULT 1,  -- Traer inventarios abiertos y contar
    puede_cerrar_inventario     bit NOT NULL CONSTRAINT df_amb_cerrar      DEFAULT 0,  -- Enviar el conteo a Profit
    activo                      bit NOT NULL CONSTRAINT df_amb_activo      DEFAULT 1,
    CONSTRAINT uq_ambientes UNIQUE (usuario_id, co_empresa)
);
GO

/* ---------- bitacora ---------- */
IF OBJECT_ID('dbo.bitacora') IS NULL
CREATE TABLE dbo.bitacora (
    id           bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_bitacora PRIMARY KEY,
    fecha        datetime       NOT NULL CONSTRAINT df_bitacora_fecha DEFAULT GETDATE(),
    usuario_id   int            NULL,
    usuario      varchar(30)    NULL,                        -- Lo escrito (aunque el usuario no exista)
    co_empresa   varchar(30)    NULL,
    accion       varchar(30)    NOT NULL,                    -- ENTRADA, SINCRONIZAR, ENVIAR_TOMA...
    detalle      nvarchar(max)  NULL,
    dispositivo  varchar(100)   NULL                         -- Modelo del teléfono
);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_bitacora_fecha')
    CREATE INDEX ix_bitacora_fecha ON dbo.bitacora (fecha);
GO

/* ---------- parametros ----------
   Solo si en su Profit 2K12 algún nombre es distinto al de fábrica.
   Ejemplo:  INSERT INTO dbo.parametros VALUES ('profit.columnas_codigo', 'modelo,ref,cod_barra');
   Claves válidas: ver android/core/red/.../ConfigProfit.kt                  */
IF OBJECT_ID('dbo.parametros') IS NULL
CREATE TABLE dbo.parametros (
    clave   varchar(60)   NOT NULL CONSTRAINT pk_parametros PRIMARY KEY,
    valor   varchar(200)  NOT NULL
);
GO

/* ---------- aperturas ----------
   Cada vez que un usuario ABRE en su teléfono un inventario físico de Profit
   (saInventarioFisico con procesado = 0): quién, en qué teléfono y a qué hora.  */
IF OBJECT_ID('dbo.aperturas') IS NULL
CREATE TABLE dbo.aperturas (
    id             int IDENTITY(1,1) NOT NULL CONSTRAINT pk_aperturas PRIMARY KEY,
    co_empresa     varchar(30)    NOT NULL,
    co_invfisico   varchar(20)    NOT NULL,                  -- saInventarioFisico.co_invfisico
    co_alma        varchar(6)     NULL,                      -- Almacén del inventario
    des_invfisico  varchar(60)    NULL,
    usuario        varchar(30)    NOT NULL,                  -- Quién lo abrió
    dispositivo    varchar(100)   NULL,
    fecha          datetime       NOT NULL CONSTRAINT df_aperturas_fecha DEFAULT GETDATE()
);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_aperturas_inventario')
    CREATE INDEX ix_aperturas_inventario ON dbo.aperturas (co_empresa, co_invfisico);
GO

/* ---------- envios (auditoría de lo enviado a la toma física de Profit) ---------- */
IF OBJECT_ID('dbo.envios') IS NULL
CREATE TABLE dbo.envios (
    id            int IDENTITY(1,1) NOT NULL CONSTRAINT pk_envios PRIMARY KEY,
    clave_envio   varchar(36)    NOT NULL,                   -- La genera el teléfono: si se reintenta
                                                             -- un envío que sí llegó, no se duplica
    co_empresa    varchar(30)    NOT NULL,
    toma          varchar(30)    NOT NULL,                   -- saInventarioFisico.co_invfisico
    co_alma       varchar(6)     NULL,
    usuario       varchar(30)    NOT NULL,                   -- Quién envió
    dispositivo   varchar(100)   NULL,
    fecha         datetime       NOT NULL CONSTRAINT df_envios_fecha DEFAULT GETDATE(),
    articulos     int            NOT NULL,                   -- Artículos distintos enviados
    conteos       int            NOT NULL,                   -- Conteos individuales enviados
    CONSTRAINT uq_envios_clave UNIQUE (clave_envio)
);
GO
IF OBJECT_ID('dbo.envio_conteos') IS NULL
CREATE TABLE dbo.envio_conteos (
    id            bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_envio_conteos PRIMARY KEY,
    envio_id      int             NOT NULL CONSTRAINT fk_envio_conteos REFERENCES dbo.envios (id),
    co_art        varchar(30)     NOT NULL,
    codigo_leido  varchar(60)     NOT NULL,
    cantidad      decimal(18, 5)  NOT NULL,
    usuario       varchar(30)     NOT NULL,                  -- Quién contó
    fecha_conteo  datetime        NOT NULL                   -- Hora del teléfono al contar
);
GO

/* ---------- Primer administrador ----------
   Usuario ADMIN, clave "cambiar123". CÁMBIELA al entrar la primera vez
   (Usuarios y permisos -> ADMIN -> Cambiar clave). Solo se crea si no hay usuarios. */
IF NOT EXISTS (SELECT 1 FROM dbo.usuarios)
    INSERT INTO dbo.usuarios (usuario, nombre, clave_hash, es_administrador)
    VALUES ('ADMIN', 'Administrador',
            'pbkdf2_sha256$210000$MDEyMzQ1Njc4OWFiY2RlZg==$5kJNFgwCFsd/NnIOxZFa4Ueun5htQZYH3ptFNF5606U=', 1);
GO

PRINT 'Tablas de SIGUEC_PURACERAMICA listas.';
GO
