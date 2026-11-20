# Configuración de PyInstaller para generar EtiquetasZebra.exe (un solo archivo, sin consola).
#   py -m PyInstaller --noconfirm --clean EtiquetasZebra.spec

a = Analysis(
    ["app.py"],
    pathex=[],
    hiddenimports=["configurar", "vista_previa", "winprint", "pymssql._mssql", "decimal", "uuid"],
    excludes=["pytest", "numpy"],
)
pyz = PYZ(a.pure)
exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    name="EtiquetasZebra",
    console=False,
    upx=False,
)
