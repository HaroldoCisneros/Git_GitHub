/* =====================================================================
   03_permisos_usuario_sql.sql
   Usuario de SQL Server que usa la App en los teléfonos (el que se
   escribe en Configuración -> Conexión). Con lo MÍNIMO necesario.
   Nunca "sa" ni "profit": su clave queda guardada en los teléfonos.

     Paso 1. Login y lectura/escritura en la base propia SIGUEC_PURACERAMICA.
     Paso 2. En la empresa de Profit 2K12:
             - SOLO LECTURA de artículos, almacenes, unidades, stock e
               inventarios físicos.
             - INSERT en saResInventario y saResInventarioReng (el resultado
               de lo contado). La App NUNCA modifica ni borra datos de Profit.

   Recomendado: que el puerto 1433 solo se pueda alcanzar desde la red WiFi
   de la empresa (firewall de Windows), nunca desde internet.
   ===================================================================== */

/* ---------- Paso 1 ---------- */
USE master;
GO
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'siguec_puraceramica')
    CREATE LOGIN siguec_puraceramica WITH PASSWORD = 'CAMBIAR_ESTA_CLAVE', CHECK_POLICY = ON;
GO

USE SIGUEC_PURACERAMICA;
GO
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'siguec_puraceramica')
    CREATE USER siguec_puraceramica FOR LOGIN siguec_puraceramica;
EXEC sp_addrolemember 'db_datareader', 'siguec_puraceramica';
EXEC sp_addrolemember 'db_datawriter', 'siguec_puraceramica';
GO

/* ---------- Paso 2: EMPRESA de Profit 2K12 (repetir cambiando SAPURA_A) ---------- */
USE SAPURA_A;
GO
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'siguec_puraceramica')
    CREATE USER siguec_puraceramica FOR LOGIN siguec_puraceramica;
GRANT SELECT ON dbo.saArticulo TO siguec_puraceramica;   -- Sincronizar artículos
-- Si los códigos de barra están en una tabla aparte (parámetro profit.tabla_codigos_barra):
-- GRANT SELECT ON dbo.NOMBRE_TABLA_CODIGOS TO siguec_puraceramica;

GRANT SELECT ON dbo.saInventarioFisico TO siguec_puraceramica;  -- Inventarios sin procesar (abrir)
GRANT SELECT ON dbo.saAlmacen          TO siguec_puraceramica;  -- Nombre del almacén
-- Enviar lo contado como resultado de inventario:
GRANT SELECT          ON dbo.saArtUnidad          TO siguec_puraceramica;  -- Unidad de cada artículo
GRANT SELECT          ON dbo.saStockAlmacen       TO siguec_puraceramica;  -- Existencia teórica
GRANT SELECT, INSERT  ON dbo.saResInventario      TO siguec_puraceramica;  -- Encabezado del resultado
GRANT INSERT          ON dbo.saResInventarioReng  TO siguec_puraceramica;  -- Renglones del resultado
-- Procedimientos de Profit (Softech) que graban los renglones y la pista de auditoría
GRANT EXECUTE ON dbo.pInsertarRenglonesResInventario TO siguec_puraceramica;
GRANT EXECUTE ON dbo.pInsertarPista                  TO siguec_puraceramica;
-- Costo unitario de cada renglón (función de la empresa, ver sql/05_funcion_ultimo_costo.sql)
IF OBJECT_ID('dbo.ConsultarUltimoCosto') IS NOT NULL
    GRANT EXECUTE ON dbo.ConsultarUltimoCosto TO siguec_puraceramica;
GO
