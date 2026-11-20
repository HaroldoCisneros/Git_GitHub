@echo off
rem Genera los ejecutables en la carpeta dist:
rem   EtiquetasZebra.exe   (icono logoapp.bmp)
rem   ConfigEtiquetas.exe  (icono logoconfig.bmp)
cd /d "%~dp0"
set PY=
where py >nul 2>nul && set PY=py
if not defined PY where python >nul 2>nul && set PY=python
if not defined PY (
  echo No se encontro Python. Instalelo desde https://www.python.org/downloads/
  pause
  exit /b 1
)
if not exist logoapp.bmp echo AVISO: falta logoapp.bmp - EtiquetasZebra.exe quedara sin icono.
if not exist logoconfig.bmp echo AVISO: falta logoconfig.bmp - ConfigEtiquetas.exe quedara sin icono.

echo.
echo Instalando lo necesario...
%PY% -m pip install -r requirements.txt pyinstaller || goto error

echo.
echo Generando ejecutables (tarda un par de minutos)...
%PY% -m PyInstaller --noconfirm --clean EtiquetasZebra.spec || goto error

rem Imagenes que usan las ventanas como icono y logo de la etiqueta
for %%f in (logoapp.bmp logoconfig.bmp) do if exist %%f copy /y %%f dist\ >nul
if exist config.xml if not exist dist\config.xml copy /y config.xml dist\ >nul

echo.
echo Listo. En la carpeta dist:
dir /b dist
echo.
echo Copie el contenido de dist a la carpeta donde quiera usarlo.
echo config.xml y el logo de la etiqueta van junto a los .exe.
pause
exit /b 0

:error
echo.
echo ERROR al generar los ejecutables.
pause
exit /b 1
