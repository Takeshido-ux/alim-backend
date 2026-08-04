#!/bin/sh
set -eu

MEDIA_DIR="${MEDIA_ROOT_DIR:-/data/media}"
mkdir -p "$MEDIA_DIR"

# Railway volumes are often root-owned; fix before dropping privileges.
if [ "$(id -u)" = "0" ]; then
	chown -R app:app "$MEDIA_DIR" || true
	# also ensure parent mount is usable when volume is at /data
	if [ -d /data ]; then
		chown app:app /data || true
	fi
	exec runuser -u app -- java $JAVA_OPTS -jar /app/app.jar
fi

exec java $JAVA_OPTS -jar /app/app.jar
