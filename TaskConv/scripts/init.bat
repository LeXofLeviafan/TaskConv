@echo off
cd ..
set OUTFILES=
for %%F in (venv\ build\ __pycache__\) do call :check %%F

if exist venv (
  call venv\Scripts\activate
  if /I %0 == "%~dpnx0" pause
  exit /B
)
py -3 -m venv venv
call venv\Scripts\activate
pip install -r scripts\requirements.txt
if /I %0 == "%~dpnx0" pause
exit /B

:check
if exist "%1" exit /B
set "OUTFILES=%OUTFILES% %1"
exit /B

:clean
cd scripts
for %%A in (%*) do (
  if "%%A"=="--keep" exit /B
)

for %%F in (%OUTFILES%) do (
  if exist ..\%%F (
    del /S /Q ..\%%F >NUL
    rmdir /S /Q ..\%%F
  )
)
exit /B
