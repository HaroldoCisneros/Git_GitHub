"""
Servicios: la lógica de la App (qué se valida, en qué orden y en qué
transacción). Usan los repositorios para el SQL y no saben nada de HTTP.

Reciben funciones para "abrir" las bases (en vez de la conexión misma): así
las pruebas les pasan bases falsas en memoria, sin SQL Server.
"""
