#!/bin/sh
set -e
DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
GRADLE_JAR="$DIR/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$GRADLE_JAR" ]; then
  mkdir -p "$DIR/gradle/wrapper"
  curl -fsSL "https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar" -o "$GRADLE_JAR"
fi
exec java -Xmx1024m -jar "$GRADLE_JAR" "$@"
