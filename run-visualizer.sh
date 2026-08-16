#!/usr/bin/env bash
set -e
mkdir -p out
javac -d out src/main/java/chipsat/*.java
java -cp out chipsat.NetworkVisualizer
