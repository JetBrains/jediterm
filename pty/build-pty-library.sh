#!/bin/sh

set -e # Any command which returns non-zero exit code will cause this shell script to exit immediately
set -x # Activate debugging to show execution details: all commands will be printed before execution

JEDITERM="$(cd "`dirname "$0"`/.."; pwd)"

"$JEDITERM/gradlew" :pty:jar
"$JEDITERM/gradlew" :pty:sourcesJar

VERSION=`cat $JEDITERM/VERSION`

rm "$JEDITERM/build/jediterm-pty-"*".jar"
cp "$JEDITERM/.gradleBuild/pty/libs/jediterm-pty-$VERSION"*".jar" "$JEDITERM/build"
