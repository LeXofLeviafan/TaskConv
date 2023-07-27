@call init
pyinstaller --specpath build/ --distpath . --onefile TaskConv.py
call deactivate
call :clean %*
if /I %0 == "%~dpnx0" pause
exit /B

:clean
scripts\init.bat %*
exit /B
