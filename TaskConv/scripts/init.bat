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
scripts\winxp\unzip scripts\winxp\setuptools-39.2.0
(cd setuptools-39.2.0 & python setup.py install & cd ..)
rmdir /S /Q setuptools-39.2.0
easy_install scripts\winxp\PyYAML-3.12-cp33-cp33m-win32.whl
easy_install scripts\winxp\colorama-0.4.6-py2.py3-none-any.whl
easy_install scripts\winxp\py-1.4.34-py2.py3-none-any.whl
easy_install scripts\winxp\pytest-3.2.5-py2.py3-none-any.whl
easy_install scripts\winxp\pyfakefs-3.4.3-py2.py3-none-any.whl
easy_install scripts\winxp\future-0.18.2-py3-none-any.whl
@rem echo "This requires MSVC installed, with working vcvarsall.bat"
scripts\winxp\unzip scripts\winxp\pywin32-221
(cd pywin32-221 & python setup3.py install & cd ..)
rmdir /S /Q pywin32-221
easy_install scripts\winxp\pypiwin32-221-py3-none-any.whl
easy_install scripts\winxp\PyInstaller-3.2.1-py3-none-any.whl
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
