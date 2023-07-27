#!/usr/bin/env bash

source init.sh
pyinstaller --specpath build/ --distpath . --onefile TaskConv.py
deactivate
clean
