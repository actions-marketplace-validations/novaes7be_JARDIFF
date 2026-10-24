#!/bin/sh
# Gera samples/v1.jar e samples/v2.jar a partir dos fontes.
set -e
cd "$(dirname "$0")"
for v in v1 v2; do
  rm -rf out/$v && mkdir -p out/$v
  javac -g --release 17 -d out/$v $(find $v/src -name '*.java')
  jar cf $v.jar -C out/$v .
done
echo "ok: samples/v1.jar samples/v2.jar"
