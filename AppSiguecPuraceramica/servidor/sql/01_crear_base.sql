/* =====================================================================
   01_crear_base.sql
   Crea la base SIGUEC_PURACERAMICA: la base PROPIA de AppSiguecPuraceramica.

   Por qué una base aparte (y no tablas dentro de la base de Profit 2K12):
     - Los usuarios de la App, la copia de los artículos y los conteos no
       se pierden si se restaura un respaldo de la empresa.
     - Las actualizaciones de Profit no tocan estas tablas.
     - El usuario de SQL de la API escribe SOLO aquí; en Profit solo lee.

   Se ejecuta UNA vez, en SQL Server Management Studio, conectado al mismo
   servidor de Profit. Compatible con SQL Server 2008 R2 en adelante.
   ===================================================================== */

IF DB_ID('SIGUEC_PURACERAMICA') IS NULL
BEGIN
    CREATE DATABASE SIGUEC_PURACERAMICA;
    PRINT 'Base SIGUEC_PURACERAMICA creada.';
END
ELSE
    PRINT 'La base SIGUEC_PURACERAMICA ya existe: no se hizo nada.';
GO
