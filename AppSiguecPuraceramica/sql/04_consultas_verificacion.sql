/* =====================================================================
   04_consultas_verificacion.sql
   Consultas para CONFIRMAR, en la base real de Profit 2K12, los nombres de
   tablas y columnas que usa la App (si alguno es distinto, se cambia en
   SIGUEC_PURACERAMICA.dbo.parametros).
   Solo leen: se pueden ejecutar sin riesgo.
   ===================================================================== */

USE SAPURA_A;   -- Cambie por la base de la empresa
GO

-- 1. ¿Existen las tablas?
SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_NAME IN ('saArticulo', 'saAlmacen', 'saStockAlmacen');

-- 2. Columnas de cada una (co_art, art_des, anulado, modelo, ref...)
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME IN ('saArticulo', 'saAlmacen', 'saStockAlmacen')
ORDER BY TABLE_NAME, ORDINAL_POSITION;

-- 3. Tablas de la toma física / inventario físico (para el envío del conteo)
SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_NAME LIKE '%Fisic%' OR TABLE_NAME LIKE '%Toma%' OR TABLE_NAME LIKE '%Ajuste%'
ORDER BY TABLE_NAME;

-- 4. ¿Hay una tabla de códigos de barra? (si aparece, parámetro profit.tabla_codigos_barra)
SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
WHERE COLUMN_NAME LIKE '%barra%' OR COLUMN_NAME LIKE '%codeb%' OR TABLE_NAME LIKE '%Barra%';

-- 5. Muestra: stock de un almacén
SELECT TOP 20 s.co_alma, s.co_art, a.art_des, s.tipo, s.stock
FROM dbo.saStockAlmacen s JOIN dbo.saArticulo a ON a.co_art = s.co_art
WHERE s.tipo = 'ACT'
ORDER BY s.co_alma, s.co_art;
GO
