@call init
python TaskConv_test.py
call deactivate
call :clean %*
if /I %0 == "%~dpnx0" pause
exit /B

:clean
scripts\init %*
exit /B
