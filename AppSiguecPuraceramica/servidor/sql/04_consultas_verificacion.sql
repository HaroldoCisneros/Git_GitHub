/* =====================================================================
   04_consultas_verificacion.sql
   Consultas para CONFIRMAR, en la base real de Profit 2K12, los nombres de
   tablas y columnas que usa la App (sección <profit> del config.xml).
   Solo leen: se pueden ejecutar sin riesgo.
   ===================================================================== */

USE PURACERAMICA;   -- Cambie por la base de la empresa
GO

-- 1. ¿Existen las tablas?
SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_NAME IN ('saArticulo', 'saAlmacen', 'saStockAlmacen');

-- 2. Columnas de cada una (co_art, art_des, anulado, modelo, ref...)
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME IN ('saArticulo', 'saAlmacen', 'saStockAlmacen')
ORDER BY TABLE_NAME, ORDINAL_POSITION;

-- 3. Tipos de stock que hay (la App usa el "actual": ACT por defecto)
SELECT tipo, COUNT(*) AS renglones FROM dbo.saStockAlmacen GROUP BY tipo;

-- 4. ¿Hay una tabla de códigos de barra? (si aparece, se pone en <tabla_codigos_barra>)
SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
WHERE COLUMN_NAME LIKE '%barra%' OR COLUMN_NAME LIKE '%codeb%' OR TABLE_NAME LIKE '%Barra%';

-- 5. Muestra: stock de un almacén
SELECT TOP 20 s.co_alma, s.co_art, a.art_des, s.tipo, s.stock
FROM dbo.saStockAlmacen s JOIN dbo.saArticulo a ON a.co_art = s.co_art
WHERE s.tipo = 'ACT'
ORDER BY s.co_alma, s.co_art;
GO
