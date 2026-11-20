@echo off
rem Abre la ventana de etiquetas Zebra
cd /d "%~dp0"
where py >nul 2>nul && (start "" pyw app.py & exit /b)
where pythonw >nul 2>nul && (start "" pythonw app.py & exit /b)
python app.py
pause
