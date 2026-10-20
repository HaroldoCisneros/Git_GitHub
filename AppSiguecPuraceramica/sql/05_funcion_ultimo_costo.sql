/* =====================================================================
   05_funcion_ultimo_costo.sql
   Función de la empresa que usa la App para el COSTO UNITARIO de cada
   renglón del resultado de inventario (saResInventarioReng.cost_unit):
   último costo de entrada del artículo (sin REPO ni PROV), por su rowguid.

   Normalmente YA EXISTE en la base de Profit. Este script es solo de
   referencia: ejecútelo únicamente si la función no existe
   (SELECT OBJECT_ID('dbo.ConsultarUltimoCosto') devuelve NULL).
   Después ejecute otra vez 03_permisos_usuario_sql.sql (GRANT EXECUTE).
   ===================================================================== */

USE SAPURA_A;   -- Cambie por la base de la empresa
GO

IF OBJECT_ID('dbo.ConsultarUltimoCosto') IS NOT NULL
BEGIN
    PRINT 'La función dbo.ConsultarUltimoCosto ya existe: no se hizo nada.';
    SET NOEXEC ON;
END
GO

SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
-- =============================================
-- Description: Devuelve el último costo de entrada de un artículo
--              (excluye REPO y PROV)
-- =============================================
CREATE FUNCTION [dbo].[ConsultarUltimoCosto]
(
	@gRowguid_Articulo UNIQUEIDENTIFIER
)
RETURNS DECIMAL(18,5)
AS
BEGIN
	DECLARE @Result DECIMAL(18,5)

	SELECT TOP 1 @Result = E.costo
	FROM saCostoHistoricoEntrada AS E
	WHERE E.cod_articulo_rowguid = @gRowguid_Articulo
	  AND E.tipo_doc NOT IN ('REPO', 'PROV')
	ORDER BY E.fecha_emision DESC

	RETURN ISNULL(@Result, 0)
END
GO
SET NOEXEC OFF;
GO
