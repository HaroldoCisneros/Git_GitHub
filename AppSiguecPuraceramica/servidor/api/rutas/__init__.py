"""
Rutas de la API, un archivo por tema. Todas cuelgan de /api/v1.

    salud.py           GET    /api/v1/salud
    autenticacion.py   POST   /api/v1/sesion          Entrar
                       GET    /api/v1/sesion          ¿Sigue vigente el token guardado?
                       DELETE /api/v1/sesion          Salir
    articulos.py       GET    /api/v1/empresas/{empresa}/articulos?texto=&inventario_id=&solo_contados=
                       GET    /api/v1/empresas/{empresa}/articulos/por-codigo?codigo=&inventario_id=
                       POST   /api/v1/empresas/{empresa}/articulos/sincronizar   (baja saArticulo de Profit)
                       GET    /api/v1/empresas/{empresa}/articulos/sincronizacion (la última)
    inventarios.py     GET    /api/v1/empresas/{empresa}/almacenes
                       GET    /api/v1/empresas/{empresa}/inventarios              (POST: preparar uno)
                       GET    /api/v1/empresas/{empresa}/inventarios/{id}
                       POST   /api/v1/empresas/{empresa}/inventarios/{id}/cerrar
                       GET    /api/v1/empresas/{empresa}/inventarios/{id}/comparacion
    conteos.py         GET    /api/v1/empresas/{empresa}/inventarios/{id}/conteos?de_todos=
                       POST   /api/v1/empresas/{empresa}/inventarios/{id}/conteos  (anotar lo escaneado)
                       DELETE /api/v1/empresas/{empresa}/inventarios/{id}/conteos/{conteo}
    administracion.py  GET    /api/v1/admin/usuarios                              (POST: crear)
                       GET    /api/v1/admin/usuarios/{id}                         (PATCH: cambiar)
                       POST   /api/v1/admin/usuarios/{id}/clave | /desbloquear
                       PUT    /api/v1/admin/usuarios/{id}/ambientes/{empresa}     (DELETE: quitar)
                       GET    /api/v1/admin/empresas
                       GET    /api/v1/admin/bitacora?usuario=&co_empresa=&accion=&desde=&hasta=
"""
