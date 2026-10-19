/* =====================================================================
   03_permisos_usuario_sql.sql
   Usuario de SQL Server EXCLUSIVO de la API (el de <sqlserver><usuario>
   del config.xml), con lo MÍNIMO necesario. Nunca "sa" ni "profit".

     Paso 1. Crear el login y darle lectura/escritura en SU base
             SIGUEC_PURACERAMICA (usuarios, copia de artículos, conteos).
     Paso 2. SOLO LECTURA de las tablas de Profit 2K12 que usa la App.

   La App NUNCA escribe en Profit. Cambie la clave y el nombre de la base
   de la empresa antes de ejecutarlo; repita el paso 2 por cada empresa.
   Si en el config.xml (<profit>) se cambian los nombres de las tablas,
   cámbielos también aquí.
   ===================================================================== */

/* ---------- Paso 1: login y base propia ---------- */
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

/* ---------- Paso 2: EMPRESA de Profit 2K12, solo lectura (repetir cambiando PURACERAMICA) ---------- */
USE PURACERAMICA;
GO
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'siguec_puraceramica')
    CREATE USER siguec_puraceramica FOR LOGIN siguec_puraceramica;
GRANT SELECT ON dbo.saArticulo      TO siguec_puraceramica;   -- Artículos (sincronizar)
GRANT SELECT ON dbo.saAlmacen       TO siguec_puraceramica;   -- Almacenes (preparar inventario)
GRANT SELECT ON dbo.saStockAlmacen  TO siguec_puraceramica;   -- Stock por almacén (foto al preparar)
-- Si los códigos de barra están en una tabla aparte (<tabla_codigos_barra> del config.xml):
-- GRANT SELECT ON dbo.NOMBRE_TABLA_CODIGOS TO siguec_puraceramica;
GO
