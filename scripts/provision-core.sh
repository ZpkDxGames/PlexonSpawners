#!/usr/bin/env bash
set -euo pipefail

CORE_VERSION="2.0.4"
CORE_SHA256="61d625a717da9f46ee9231e1970d84b4c317ae12cf4090cdf7c9d39b6a1a9baf"
CORE_URL="https://github.com/ZpkDxGames/PlexonCore/releases/download/v${CORE_VERSION}/PlexonCore-${CORE_VERSION}.jar"
JAR="/tmp/PlexonCore-${CORE_VERSION}.jar"

curl --fail --location --retry 3 --output "$JAR" "$CORE_URL"
echo "${CORE_SHA256}  ${JAR}" | sha256sum --check
mvn -B install:install-file \
  -Dfile="$JAR" \
  -DgroupId=com.zpkdxgames \
  -DartifactId=PlexonCore \
  -Dversion="$CORE_VERSION" \
  -Dpackaging=jar \
  -DgeneratePom=true

echo "Provisioned verified PlexonCore ${CORE_VERSION}."
