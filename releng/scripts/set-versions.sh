#!/bin/sh
set -eu

#
# This script can be used to set the Gyrex features and product versions.
# 

echoerr() { echo "$@" 1>&2; }

if [ -z ${1+x} ] || [ -z "$1" ]; then
  echoerr "ERROR: missing version"
  echo "$(basename "$0") <major.minor.service>"
  exit 1
fi

VERSION="${1}.qualifier"
echo "Using verion: $VERSION"

MYPATH="$( cd "$(dirname "$0")" ; pwd -P )"
pushd "$MYPATH"

# create temp folder for repo (but only use if if MAVEN_REPO not set)
TEMPREPO=`mktemp -d 2>/dev/null || mktemp -d -t 'mvntemprepo'`
if [ -z ${MAVEN_REPO+x} ] || [ -z "$MAVEN_REPO" ]; then
  echo "Using temp. Maven repository: $TEMPREPO"
  MAVEN_REPO="$TEMPREPO"
fi

# allow additional maven arguments
if [ -z ${MAVEN_ARGS+x} ] || [ -z "$MAVEN_ARGS" ]; then
  MAVEN_ARGS=""
fi

echo ""
echo ""
echo "**************************************************"
echo "Building Gyrex to have it in the local repository."
echo "**************************************************"
echo ""
mvn clean install -Dmaven.repo.local="$MAVEN_REPO" "$MAVEN_ARGS" -f "../aggregator/pom.xml" 

echo ""
echo ""
echo "******************"
echo "Updating versions."
echo "******************"
echo ""
set -x
find "../features/" -name "pom.xml" -exec mvn org.eclipse.tycho:tycho-versions-plugin:set-version -DnewVersion="$VERSION" -Dmaven.repo.local="$MAVEN_REPO" "$MAVEN_ARGS" -f {} \;
find "../products/" -name "pom.xml" -exec mvn org.eclipse.tycho:tycho-versions-plugin:set-version -DnewVersion="$VERSION" -Dmaven.repo.local="$MAVEN_REPO" "$MAVEN_ARGS" -f {} \;
set +x

# cleanup temp folder
rm -rf "$TEMPREPO"
