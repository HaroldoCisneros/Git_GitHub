# Configuración de PyInstaller: genera dos ejecutables (un solo archivo cada uno, sin consola)
#   EtiquetasZebra.exe   programa de etiquetas   icono: logoapp.bmp
#   ConfigEtiquetas.exe  editor de config.xml    icono: logoconfig.bmp
#
#   py -m PyInstaller --noconfirm --clean EtiquetasZebra.spec

import os

from PIL import Image

os.makedirs("build", exist_ok=True)


def icono(bmp):
    """Convierte la imagen .bmp en .ico (varios tamaños). Sin imagen, sin icono."""
    if not os.path.exists(bmp):
        print(f"AVISO: no se encontró {bmp}; el ejecutable queda sin icono.")
        return None
    ico = os.path.join("build", os.path.splitext(os.path.basename(bmp))[0] + ".ico")
    img = Image.open(bmp).convert("RGBA")
    lado = max(img.size)
    cuadrada = Image.new("RGBA", (lado, lado), (255, 255, 255, 0))
    cuadrada.paste(img, ((lado - img.width) // 2, (lado - img.height) // 2))
    cuadrada.save(ico, sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    return ico


COMUNES = ["pymssql._mssql", "decimal", "uuid"]
EXCLUIR = ["pytest", "numpy"]


def ejecutable(script, nombre, bmp, ocultos):
    a = Analysis([script], hiddenimports=ocultos + COMUNES, excludes=EXCLUIR)
    return EXE(PYZ(a.pure), a.scripts, a.binaries, a.datas,
               name=nombre, icon=icono(bmp), console=False, upx=False)


ejecutable("app.py", "EtiquetasZebra", "logoapp.bmp", ["configurar", "vista_previa", "winprint"])
ejecutable("configurar.py", "ConfigEtiquetas", "logoconfig.bmp", ["winprint"])
