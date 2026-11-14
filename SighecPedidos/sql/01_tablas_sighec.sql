/* =====================================================================
   SighecPedidos - Objetos propios en la base de datos de Profit
   Ejecutar en la base de datos de la empresa (requiere SQL Server 2012+
   por HASHBYTES('SHA2_256')).
   Las tablas de Profit NO se modifican: solo se crean tablas Sighec*.
   ===================================================================== */

/* ---------- Usuarios de la aplicación ---------- */
IF OBJECT_ID('dbo.SighecUsuarios') IS NULL
CREATE TABLE dbo.SighecUsuarios (
    usuario         VARCHAR(30)    NOT NULL PRIMARY KEY,
    nombre          VARCHAR(100)   NOT NULL,
    co_ven          VARCHAR(6)     NULL,          -- vendedor de Profit asociado
    clave_sal       VARBINARY(16)  NOT NULL,
    clave_hash      VARBINARY(32)  NOT NULL,      -- SHA2_256(sal + clave NVARCHAR)
    activo          BIT            NOT NULL DEFAULT 1,
    fecha_creacion  DATETIME       NOT NULL DEFAULT GETDATE(),
    ultimo_acceso   DATETIME       NULL
);
GO

/* Crea o actualiza un usuario (la clave nunca se guarda en texto plano) */
IF OBJECT_ID('dbo.SighecGuardarUsuario') IS NOT NULL
    DROP PROCEDURE dbo.SighecGuardarUsuario;
GO
CREATE PROCEDURE dbo.SighecGuardarUsuario
    @usuario VARCHAR(30),
    @nombre  VARCHAR(100),
    @clave   NVARCHAR(100),
    @co_ven  VARCHAR(6) = NULL,
    @activo  BIT = 1
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @sal  VARBINARY(16) = CRYPT_GEN_RANDOM(16);
    DECLARE @hash VARBINARY(32) = HASHBYTES('SHA2_256', @sal + CAST(@clave AS VARBINARY(200)));

    IF EXISTS (SELECT 1 FROM dbo.SighecUsuarios WHERE usuario = @usuario)
        UPDATE dbo.SighecUsuarios
           SET nombre = @nombre, co_ven = @co_ven, activo = @activo,
               clave_sal = @sal, clave_hash = @hash
         WHERE usuario = @usuario;
    ELSE
        INSERT INTO dbo.SighecUsuarios (usuario, nombre, co_ven, clave_sal, clave_hash, activo)
        VALUES (@usuario, @nombre, @co_ven, @sal, @hash, @activo);
END
GO

/* ---------- Pedidos tomados en la tablet ----------
   Se guardan aquí primero (estado PENDIENTE). Pasarlos a saPedidoVenta
   de Profit será un paso posterior y controlado. */
IF OBJECT_ID('dbo.SighecPedido') IS NULL
CREATE TABLE dbo.SighecPedido (
    id              INT IDENTITY(1,1) PRIMARY KEY,
    co_cli          VARCHAR(16)    NOT NULL,
    cli_des         VARCHAR(120)   NULL,
    usuario         VARCHAR(30)    NOT NULL,
    co_ven          VARCHAR(6)     NULL,
    fecha           DATETIME       NOT NULL DEFAULT GETDATE(),
    subtotal        DECIMAL(18,2)  NOT NULL,
    iva             DECIMAL(18,2)  NOT NULL,
    total           DECIMAL(18,2)  NOT NULL,
    observacion     VARCHAR(500)   NULL,
    estado          VARCHAR(15)    NOT NULL DEFAULT 'PENDIENTE',
    doc_num_profit  VARCHAR(20)    NULL
);
GO

IF OBJECT_ID('dbo.SighecPedidoReng') IS NULL
CREATE TABLE dbo.SighecPedidoReng (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    id_pedido   INT            NOT NULL REFERENCES dbo.SighecPedido(id),
    reng_num    INT            NOT NULL,
    co_art      VARCHAR(30)    NOT NULL,
    art_des     VARCHAR(120)   NULL,
    cantidad    DECIMAL(18,3)  NOT NULL,
    precio      DECIMAL(18,2)  NOT NULL,
    subtotal    DECIMAL(18,2)  NOT NULL
);
GO

/* ---------- Ejemplo: usuario inicial ----------
EXEC dbo.SighecGuardarUsuario @usuario = 'admin', @nombre = 'Administrador', @clave = N'CambiarEsto123';
*/

/* ---------- Ejemplo: login SQL que usará la tablet ----------
   (ejecutar como administrador; ajustar nombre de BD y clave)
CREATE LOGIN sighec_app WITH PASSWORD = 'ClaveSegura!2026';
USE [NOMBRE_BD_PROFIT];
CREATE USER sighec_app FOR LOGIN sighec_app;
ALTER ROLE db_datareader ADD MEMBER sighec_app;
GRANT INSERT, UPDATE ON dbo.SighecPedido      TO sighec_app;
GRANT INSERT         ON dbo.SighecPedidoReng  TO sighec_app;
GRANT UPDATE         ON dbo.SighecUsuarios    TO sighec_app;  -- ultimo_acceso
*/
