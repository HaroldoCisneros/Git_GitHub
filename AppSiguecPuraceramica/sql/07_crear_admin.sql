/* =====================================================================
   07_crear_admin.sql
   Crea (o reinicia) el primer usuario de la App:

       Usuario: ADMIN      Clave: cambiar123

   - Si ADMIN no existe, lo crea como administrador.
   - Si ya existe, le pone otra vez la clave cambiar123, lo activa, lo
     desbloquea y lo deja como administrador (sirve si se olvidó la clave).
   - Le da TODAS las empresas registradas en la App con todos los permisos.

   Úselo cuando no hay ningún usuario para entrar la primera vez.
   (02_tablas.sql ya crea ADMIN, pero solo si la tabla de usuarios está vacía.)

   La primera entrada de ADMIN en un teléfono necesita el WiFi de la empresa;
   después entra sin conexión. CÁMBIELE LA CLAVE al entrar:
   Usuarios y permisos -> ADMIN -> Cambiar clave.
   ===================================================================== */

USE SIGUEC_PURACERAMICA;
GO

-- Hash PBKDF2 de "cambiar123" (mismo formato de AppSighecMovil; nunca se guarda la clave)
DECLARE @hash varchar(200) =
    'pbkdf2_sha256$210000$MDEyMzQ1Njc4OWFiY2RlZg==$5kJNFgwCFsd/NnIOxZFa4Ueun5htQZYH3ptFNF5606U=';

IF NOT EXISTS (SELECT 1 FROM dbo.usuarios WHERE usuario = 'ADMIN')
    INSERT INTO dbo.usuarios (usuario, nombre, clave_hash, es_administrador, activo)
    VALUES ('ADMIN', 'Administrador', @hash, 1, 1);
ELSE
    UPDATE dbo.usuarios
       SET clave_hash = @hash, es_administrador = 1, activo = 1,
           intentos_fallidos = 0, bloqueado_hasta = NULL
     WHERE usuario = 'ADMIN';

-- Todas las empresas de la App, con todos los permisos (las que todavía no tenga)
INSERT INTO dbo.ambientes (usuario_id, co_empresa, puede_consultar_articulos, puede_sincronizar_articulos,
                           puede_contar_inventario, puede_cerrar_inventario)
SELECT u.id, e.co_empresa, 1, 1, 1, 1
  FROM dbo.usuarios u
  CROSS JOIN dbo.empresas e
 WHERE u.usuario = 'ADMIN'
   AND NOT EXISTS (SELECT 1 FROM dbo.ambientes a WHERE a.usuario_id = u.id AND a.co_empresa = e.co_empresa);
GO

SELECT u.usuario, u.nombre, u.es_administrador, u.activo,
       (SELECT COUNT(*) FROM dbo.ambientes a WHERE a.usuario_id = u.id) AS empresas
  FROM dbo.usuarios u WHERE u.usuario = 'ADMIN';
PRINT 'Listo: entre a la App con ADMIN / cambiar123 (la primera vez con WiFi) y cambie la clave.';
GO
