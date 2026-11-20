@echo off
rem Instala las dependencias del programa de etiquetas Zebra
cd /d "%~dp0"
set PY=
where py >nul 2>nul && set PY=py
if not defined PY where python >nul 2>nul && set PY=python
if not defined PY (
  echo No se encontro Python en este equipo.
  echo Instalelo desde https://www.python.org/downloads/ marcando "Add python.exe to PATH".
  pause
  exit /b 1
)
%PY% --version
%PY% -m pip install --upgrade pip
%PY% -m pip install -r requirements.txt || (echo. & echo ERROR al instalar. & pause & exit /b 1)
if not exist config.ini (
  copy config.ini.ejemplo config.ini >nul
  echo Se creo config.ini: abralo y ponga los datos del servidor SQL y de la impresora.
)
echo.
echo Listo. Para abrir el programa use iniciar.bat
pause
