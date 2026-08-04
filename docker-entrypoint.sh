#!/bin/sh
set -eu

MEDIA_DIR="${MEDIA_ROOT_DIR:-/data/media}"

# Railway bind-mounts /data as root-owned; ensure the media dir exists and is writable.
mkdir -p "$MEDIA_DIR"
chmod 777 "$MEDIA_DIR" 2>/dev/null || true
if [ -d /data ]; then
	chmod 777 /data 2>/dev/null || true
fi

exec java $JAVA_OPTS -jar /app/app.jar
