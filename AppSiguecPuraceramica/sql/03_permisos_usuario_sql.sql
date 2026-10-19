/* =====================================================================
   03_permisos_usuario_sql.sql
   Usuario de SQL Server que usa la App en los teléfonos (el que se
   escribe en Configuración -> Conexión). Con lo MÍNIMO necesario.
   Nunca "sa" ni "profit": su clave queda guardada en los teléfonos.

     Paso 1. Login y lectura/escritura en la base propia SIGUEC_PURACERAMICA.
     Paso 2. En la empresa de Profit 2K12:
             - SOLO LECTURA de artículos (sincronizar).
             - Lectura de las tomas físicas abiertas y escritura de lo contado
               (las tablas de toma física se completan cuando se confirmen).

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

/* ---------- Paso 2: EMPRESA de Profit 2K12 (repetir cambiando PURACERAMICA) ---------- */
USE PURACERAMICA;
GO
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'siguec_puraceramica')
    CREATE USER siguec_puraceramica FOR LOGIN siguec_puraceramica;
GRANT SELECT ON dbo.saArticulo TO siguec_puraceramica;   -- Sincronizar artículos
-- Si los códigos de barra están en una tabla aparte (parámetro profit.tabla_codigos_barra):
-- GRANT SELECT ON dbo.NOMBRE_TABLA_CODIGOS TO siguec_puraceramica;

-- PENDIENTE (tablas de toma física de Profit 2K12, por confirmar):
-- GRANT SELECT ON dbo.<encabezado de la toma física> TO siguec_puraceramica;  -- Tomas abiertas
-- GRANT SELECT, INSERT, UPDATE ON dbo.<renglones de la toma física> TO siguec_puraceramica;  -- Enviar lo contado
GO
