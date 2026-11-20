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
if not exist config.xml (
  echo Abriendo la configuracion...
  %PY% configurar.py
)
echo.
echo Listo. Para abrir el programa use iniciar.bat
pause
