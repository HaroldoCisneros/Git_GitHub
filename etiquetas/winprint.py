"""
Impresión RAW en impresoras instaladas en Windows (USB, compartidas, etc.).

Usa directamente la API de Windows (winspool.drv) con ctypes, sin pywin32.
"""

import ctypes
import sys
from ctypes import wintypes

PRINTER_ENUM_LOCAL = 0x2
PRINTER_ENUM_CONNECTIONS = 0x4


class _DOC_INFO_1(ctypes.Structure):
    _fields_ = [("pDocName", wintypes.LPWSTR),
                ("pOutputFile", wintypes.LPWSTR),
                ("pDatatype", wintypes.LPWSTR)]


class _PRINTER_INFO_4(ctypes.Structure):
    _fields_ = [("pPrinterName", wintypes.LPWSTR),
                ("pServerName", wintypes.LPWSTR),
                ("Attributes", wintypes.DWORD)]


def disponible() -> bool:
    return sys.platform == "win32"


def _api():
    w = ctypes.WinDLL("winspool.drv", use_last_error=True)
    H, PH, DW, PDW = wintypes.HANDLE, ctypes.POINTER(wintypes.HANDLE), wintypes.DWORD, ctypes.POINTER(wintypes.DWORD)

    w.OpenPrinterW.argtypes = [wintypes.LPWSTR, PH, ctypes.c_void_p]
    w.OpenPrinterW.restype = wintypes.BOOL
    w.ClosePrinter.argtypes = [H]
    w.ClosePrinter.restype = wintypes.BOOL
    w.StartDocPrinterW.argtypes = [H, DW, ctypes.POINTER(_DOC_INFO_1)]
    w.StartDocPrinterW.restype = DW
    w.EndDocPrinter.argtypes = [H]
    w.EndDocPrinter.restype = wintypes.BOOL
    w.StartPagePrinter.argtypes = [H]
    w.StartPagePrinter.restype = wintypes.BOOL
    w.EndPagePrinter.argtypes = [H]
    w.EndPagePrinter.restype = wintypes.BOOL
    w.WritePrinter.argtypes = [H, ctypes.c_void_p, DW, PDW]
    w.WritePrinter.restype = wintypes.BOOL
    w.EnumPrintersW.argtypes = [DW, wintypes.LPWSTR, DW, ctypes.c_void_p, DW, PDW, PDW]
    w.EnumPrintersW.restype = wintypes.BOOL
    w.GetDefaultPrinterW.argtypes = [wintypes.LPWSTR, PDW]
    w.GetDefaultPrinterW.restype = wintypes.BOOL
    return w


def _fallo(que: str):
    err = ctypes.get_last_error()
    return OSError(err, f"{que}: {ctypes.FormatError(err).strip()}")


def impresoras() -> list[str]:
    """Nombres de las impresoras instaladas (locales y conectadas)."""
    if not disponible():
        return []
    w = _api()
    flags = PRINTER_ENUM_LOCAL | PRINTER_ENUM_CONNECTIONS
    necesario, cantidad = wintypes.DWORD(0), wintypes.DWORD(0)
    w.EnumPrintersW(flags, None, 4, None, 0, ctypes.byref(necesario), ctypes.byref(cantidad))
    if necesario.value == 0:
        return []
    buffer = ctypes.create_string_buffer(necesario.value)
    if not w.EnumPrintersW(flags, None, 4, buffer, necesario, ctypes.byref(necesario),
                           ctypes.byref(cantidad)):
        raise _fallo("No se pudo listar las impresoras")
    info = ctypes.cast(buffer, ctypes.POINTER(_PRINTER_INFO_4))
    return [info[i].pPrinterName for i in range(cantidad.value)]


def predeterminada() -> str:
    if not disponible():
        return ""
    w = _api()
    tam = wintypes.DWORD(0)
    w.GetDefaultPrinterW(None, ctypes.byref(tam))
    if tam.value == 0:
        return ""
    nombre = ctypes.create_unicode_buffer(tam.value)
    return nombre.value if w.GetDefaultPrinterW(nombre, ctypes.byref(tam)) else ""


def sugerida(lista: list[str]) -> str:
    """La impresora Zebra de la lista (si hay), si no la predeterminada."""
    for nombre in lista:
        if any(x in nombre.lower() for x in ("zebra", "zdesigner", "zpl")):
            return nombre
    return predeterminada() or (lista[0] if lista else "")


def imprimir_raw(datos: bytes, impresora: str, documento: str = "Etiquetas") -> None:
    """Envía los bytes tal cual (ZPL) a la impresora, sin pasar por el controlador gráfico."""
    if not disponible():
        raise OSError("La impresión en impresoras de Windows solo funciona en Windows.")
    w = _api()
    h = wintypes.HANDLE()
    if not w.OpenPrinterW(impresora, ctypes.byref(h), None):
        raise _fallo(f"No se encontró la impresora «{impresora}»")
    try:
        doc = _DOC_INFO_1(documento, None, "RAW")
        if not w.StartDocPrinterW(h, 1, ctypes.byref(doc)):
            raise _fallo("No se pudo iniciar el trabajo de impresión")
        try:
            if not w.StartPagePrinter(h):
                raise _fallo("No se pudo iniciar la página")
            escritos = wintypes.DWORD(0)
            ok = w.WritePrinter(h, datos, len(datos), ctypes.byref(escritos))
            w.EndPagePrinter(h)
            if not ok or escritos.value != len(datos):
                raise _fallo("No se pudo enviar los datos a la impresora")
        finally:
            w.EndDocPrinter(h)
    finally:
        w.ClosePrinter(h)


if __name__ == "__main__":
    # Diagnóstico:  py winprint.py
    lista = impresoras()
    print("Impresoras instaladas:")
    for n in lista:
        print("  -", n)
    print("Predeterminada:", predeterminada() or "(ninguna)")
    print("Sugerida para etiquetas:", sugerida(lista) or "(ninguna)")
