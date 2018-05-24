#!/bin/sh

set -e # Any command which returns non-zero exit code will cause this shell script to exit immediately
set -x # Activate debugging to show execution details: all commands will be printed before execution

IDEA_REPO="$1"
if [ ! -d "$IDEA_REPO" ]; then
  echo "No IDEA repo directory passed"
  exit 1
fi

IDEA_REPO="$(cd "$IDEA_REPO"; pwd)"

JEDITERM="$(cd "`dirname "$0"`/.."; pwd)"

VERSION=`cat $JEDITERM/VERSION`

rm "$IDEA_REPO/community/lib/jediterm-pty-"*".jar"
cp "$JEDITERM/build/jediterm-pty-$VERSION.jar" "$IDEA_REPO/community/lib/"

rm "$IDEA_REPO/community/lib/src/jediterm-pty-"*"-src.jar"
cp "$JEDITERM/build/jediterm-pty-$VERSION-src.jar" "$IDEA_REPO/community/lib/src/"

sed -i -E 's/(<root url=.*jediterm\-pty\-)[0-9][0-9\.]*[0-9](.*$)/\1'$VERSION'\2/' "$IDEA_REPO/.idea/libraries/jediterm_pty.xml"
# print affected lines to verify changes
grep '<root url=' "$IDEA_REPO/.idea/libraries/jediterm_pty.xml"

sed -i -E 's/(<root url=.*jediterm\-pty\-)[0-9][0-9\.]*[0-9](.*$)/\1'$VERSION'\2/' "$IDEA_REPO/community/.idea/libraries/jediterm_pty.xml"
# print affected lines to verify changes
grep '<root url=' "$IDEA_REPO/community/.idea/libraries/jediterm_pty.xml"

sed -i -E 's/(libraryName\: "jediterm", version\: ")[^"]*(")/\1'$VERSION'\2/' "$IDEA_REPO/build/groovy/org/jetbrains/intellij/build/UltimateLibraryLicenses.groovy"
# print affected lines to verify changes
grep 'libraryName\: "jediterm", version\: "' "$IDEA_REPO/build/groovy/org/jetbrains/intellij/build/UltimateLibraryLicenses.groovy"

sed -i -E 's/(libraryName\: "jediterm-pty", version\: ")[^"]*(")/\1'$VERSION'\2/' "$IDEA_REPO/community/platform/build-scripts/groovy/org/jetbrains/intellij/build/CommunityLibraryLicenses.groovy"
# print affected lines to verify changes
grep 'libraryName\: "jediterm-pty", version\: "' "$IDEA_REPO/community/platform/build-scripts/groovy/org/jetbrains/intellij/build/CommunityLibraryLicenses.groovy"

echo "\nEverything looks fine, but please verify changes manually"
