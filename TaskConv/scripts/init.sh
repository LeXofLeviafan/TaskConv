#!/usr/bin/env bash

cd ..
OUTFILES=( venv/ build/ .pytest_cache/ __pycache__/ )
for FILE in "${OUTFILES[@]}"; do
  [ -e "$FILE" ] && OUTFILES=("${OUTFILES[@]/$FILE}")  # don't remove existing
done

virtualenv venv
source venv/bin/activate
pip install -r scripts/requirements.txt

function clean {
  for ARG in "${BASH_ARGV[@]}"; do  # ignore if --keep was passed
    [ "$ARG" = "--keep" ] && return
  done
  for FILE in "${OUTFILES[@]}"; do
    [ -e "$FILE" ] && rm -r "$FILE"
  done
}
