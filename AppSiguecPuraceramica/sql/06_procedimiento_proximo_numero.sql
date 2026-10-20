/* =====================================================================
   06_procedimiento_proximo_numero.sql
   Procedimiento de la empresa que da el PRÓXIMO NÚMERO de un documento
   (último número + 1, respetando prefijo y ceros a la izquierda).

   La App lo usa para el correlativo del resultado de inventario:
       EXEC dbo.psObtenerProximoNumero @sTabla = 'saResInventario',
            @sCampo = 'num_resinv', @sPrefijo = '', @sProximo = @p OUTPUT
   dentro de la misma transacción en que graba el resultado (lee con
   UPDLOCK + HOLDLOCK: dos envíos a la vez no reciben el mismo número).

   Normalmente YA EXISTE en la base de Profit. Este script es de
   referencia: solo lo crea si no existe. Después ejecute otra vez
   03_permisos_usuario_sql.sql (GRANT EXECUTE).
   ===================================================================== */

USE SAPURA_A;   -- Cambie por la base de la empresa
GO

IF OBJECT_ID('dbo.psObtenerProximoNumero') IS NOT NULL
BEGIN
    PRINT 'El procedimiento dbo.psObtenerProximoNumero ya existe: no se hizo nada.';
    SET NOEXEC ON;
END
GO

CREATE PROCEDURE dbo.psObtenerProximoNumero
    @sTabla    SYSNAME,                    -- tabla del documento (ej. 'saFacturaVenta')
    @sCampo    SYSNAME,                    -- campo del número (ej. 'doc_num')
    @sPrefijo  VARCHAR(50) = NULL,         -- prefijo fijo opcional (ej. 'FAC')
    @sProximo  VARCHAR(50) = NULL OUTPUT   -- devuelve el próximo número
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @sql      NVARCHAR(MAX),
            @obj      NVARCHAR(300),
            @col      NVARCHAR(300),
            @like     VARCHAR(120),
            @sResult  VARCHAR(50),
            @iLen     INT,
            @iLenPre  INT,
            @iIni     INT,
            @iDig     INT,
            @sNum     VARCHAR(40);

    -- Prefijo sin espacios a la derecha
    SET @sPrefijo = RTRIM(ISNULL(@sPrefijo, ''));
    SET @iLenPre  = LEN(@sPrefijo);
    SET @iIni     = @iLenPre + 1;          -- posición donde empiezan los dígitos

    -- Validar que la tabla y el campo existan (evita inyección SQL)
    IF OBJECT_ID(@sTabla, 'U') IS NULL
    BEGIN
        RAISERROR('La tabla %s no existe.', 16, 1, @sTabla);
        RETURN;
    END
    IF COL_LENGTH(@sTabla, @sCampo) IS NULL
    BEGIN
        RAISERROR('El campo %s no existe en la tabla %s.', 16, 1, @sCampo, @sTabla);
        RETURN;
    END

    -- Nombres protegidos con corchetes
    SET @obj = QUOTENAME(ISNULL(PARSENAME(@sTabla, 2), 'dbo')) + N'.' + QUOTENAME(PARSENAME(@sTabla, 1));
    SET @col = QUOTENAME(@sCampo);

    -- Patrón: prefijo (con comodines escapados) seguido de un dígito
    SET @like = REPLACE(REPLACE(REPLACE(@sPrefijo, '[', '[[]'), '%', '[%]'), '_', '[_]') + '[0-9]%';

    -- Después del prefijo solo dígitos, y entre ellos los de mayor longitud
    SET @sql = N'
        SELECT @r = MAX(' + @col + N')
        FROM ' + @obj + N' WITH (UPDLOCK, HOLDLOCK)
        WHERE ' + @col + N' LIKE @like
          AND PATINDEX(''%[^0-9]%'', SUBSTRING(' + @col + N', @ini, 40) + '' '')
              > LEN(SUBSTRING(' + @col + N', @ini, 40))
          AND LEN(' + @col + N') = (
                SELECT MAX(LEN(' + @col + N'))
                FROM ' + @obj + N'
                WHERE ' + @col + N' LIKE @like
                  AND PATINDEX(''%[^0-9]%'', SUBSTRING(' + @col + N', @ini, 40) + '' '')
                      > LEN(SUBSTRING(' + @col + N', @ini, 40)));

        SELECT @l = ISNULL(MAX(LEN(' + @col + N')), 6) FROM ' + @obj + N';';

    EXEC sp_executesql @sql,
         N'@like VARCHAR(120), @ini INT, @r VARCHAR(50) OUTPUT, @l INT OUTPUT',
         @like, @iIni, @sResult OUTPUT, @iLen OUTPUT;

    IF @sResult IS NULL
    BEGIN
        -- No hay documentos con ese prefijo: empieza en 1 con la longitud de la tabla (o 6)
        SET @iDig = @iLen - @iLenPre;
        IF @iDig < 1 SET @iDig = 1;
        SET @sProximo = @sPrefijo + RIGHT(REPLICATE('0', @iDig) + '1', @iDig);
    END
    ELSE
    BEGIN
        -- Último número + 1, respetando los ceros a la izquierda
        SET @sResult = RTRIM(@sResult);
        SET @sNum    = SUBSTRING(@sResult, @iIni, 40);
        SET @iDig    = LEN(@sNum);
        SET @sNum    = CAST(CAST(@sNum AS BIGINT) + 1 AS VARCHAR(40));
        IF LEN(@sNum) < @iDig
            SET @sNum = RIGHT(REPLICATE('0', @iDig) + @sNum, @iDig);
        SET @sProximo = @sPrefijo + @sNum;
    END

    SELECT @sProximo AS ProximoNumero, @sResult AS UltimoNumero;
END
GO
SET NOEXEC OFF;
GO
