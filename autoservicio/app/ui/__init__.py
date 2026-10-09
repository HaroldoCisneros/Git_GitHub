"""
Interfaz gráfica táctil (PySide6 / Qt).

    estilos.py            Hoja de estilos: letras y botones grandes para usar con el dedo.
    teclado.py            Teclado virtual en pantalla (alfanumérico y numérico).
    dialogos.py           Ventanas emergentes táctiles: pedir un dato, mensajes y confirmaciones.
    ventana_kiosco.py     Ventana base a pantalla completa que solo se cierra con contraseña.
    ventana_login.py      Pantalla de inicio de sesión del usuario.
    ventana_principal.py  Pantalla de escaneo de artículos (la factura).

Regla de la aplicación: cada vez que el usuario tenga que escribir algo, se
le muestra el teclado en pantalla (la aplicación es 100 % táctil).
"""
