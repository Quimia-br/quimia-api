#!/bin/sh
set -eu

if [ "$#" -ne 2 ]; then
    echo "usage: package-discloud-release.sh INPUT_JAR OUTPUT_ZIP" >&2
    exit 2
fi

input_jar=$1
output_zip=$2

if [ ! -f "$input_jar" ] || [ ! -r "$input_jar" ]; then
    echo "input JAR is missing or unreadable: $input_jar" >&2
    exit 1
fi

if [ -z "$output_zip" ] || [ -d "$output_zip" ]; then
    echo "output path is invalid" >&2
    exit 1
fi

if [ "$input_jar" = "$output_zip" ] \
        || { [ -e "$output_zip" ] && [ "$input_jar" -ef "$output_zip" ]; }; then
    echo "input and output paths must be different" >&2
    exit 1
fi

if [ ! -f discloud.config ]; then
    echo "discloud.config is missing from the repository root" >&2
    exit 1
fi

if ! jar --list --file "$input_jar" | grep -Fxq 'BOOT-INF/'; then
    echo "input JAR is not an executable Spring Boot archive" >&2
    exit 1
fi

stage=$(mktemp -d)
trap 'rm -rf "$stage"' EXIT
cp "$input_jar" "$stage/app.jar"
cp discloud.config "$stage/discloud.config"
mkdir -p "$(dirname "$output_zip")"
jar --create --file "$stage/release.zip" --no-manifest -C "$stage" app.jar discloud.config

expected=$(printf 'app.jar\ndiscloud.config')
actual=$(jar --list --file "$stage/release.zip" | tr -d '\r' | LC_ALL=C sort)
if [ "$actual" != "$expected" ]; then
    echo "deployment ZIP must contain only app.jar and discloud.config" >&2
    exit 1
fi

mv -f -- "$stage/release.zip" "$output_zip"
