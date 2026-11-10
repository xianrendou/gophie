#!/bin/bash
set -e
cd "$(dirname "$0")"
if javac -version 2>&1 | grep -q 'javac 1\.8\.'; then
    java_target=(-source 8 -target 8)
else
    java_target=(--release 8)
fi
mkdir -p class build
javac -g -encoding UTF-8 "${java_target[@]}" -d class -sourcepath src src/org/gophie/Gophie.java
jar -J-Djava.io.tmpdir=build cfe build/Gophie.jar org.gophie.Gophie -C class org -C . res
