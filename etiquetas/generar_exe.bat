@echo off
rem Genera dist\EtiquetasZebra.exe con PyInstaller
cd /d "%~dp0"
set PY=
where py >nul 2>nul && set PY=py
if not defined PY where python >nul 2>nul && set PY=python
if not defined PY (
  echo No se encontro Python. Instalelo desde https://www.python.org/downloads/
  pause
  exit /b 1
)
%PY% -m pip install -r requirements.txt pyinstaller || goto error
%PY% -m PyInstaller --noconfirm --clean EtiquetasZebra.spec || goto error
echo.
echo Listo: dist\EtiquetasZebra.exe
echo Copie el .exe a la carpeta donde quiera usarlo; config.xml y el logo van junto al .exe.
pause
exit /b 0
:error
echo.
echo ERROR al generar el ejecutable.
pause
exit /b 1
