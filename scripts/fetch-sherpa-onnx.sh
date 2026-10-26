#!/usr/bin/env bash
# Скачивает движок распознавания речи sherpa-onnx (aar с нативными библиотеками) в app/libs.
# В git он не хранится (50 МБ), в Maven Central его нет; качаем из релизов проекта и сверяем хеш.
set -euo pipefail

VERSION=1.13.8
SHA256=633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96
TARGET=app/libs/sherpa-onnx.aar

if [ -f "$TARGET" ] && echo "$SHA256  $TARGET" | sha256sum -c --status; then
  echo "sherpa-onnx $VERSION уже на месте"
  exit 0
fi

mkdir -p "$(dirname "$TARGET")"
curl -fL --retry 4 --retry-delay 5 -o "$TARGET.part" \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$VERSION/sherpa-onnx-$VERSION.aar"
echo "$SHA256  $TARGET.part" | sha256sum -c
mv "$TARGET.part" "$TARGET"
