#!/usr/bin/env bash
# Скачивает русскую модель распознавания речи Vosk в assets приложения.
# Модель (~45 МБ) не хранится в git: её кладёт сюда CI перед сборкой APK.
set -euo pipefail

MODEL=vosk-model-small-ru-0.22
TARGET=app/src/main/assets/model-ru
CACHE=${VOICE_MODEL_CACHE:-.voice-model}

if [ -f "$TARGET/uuid" ] && [ "$(cat "$TARGET/uuid")" = "$MODEL" ]; then
  echo "Модель уже на месте: $TARGET"
  exit 0
fi

mkdir -p "$CACHE"
if [ ! -f "$CACHE/$MODEL.zip" ]; then
  curl -fL --retry 4 --retry-delay 5 -o "$CACHE/$MODEL.zip.part" "https://alphacephei.com/vosk/models/$MODEL.zip"
  mv "$CACHE/$MODEL.zip.part" "$CACHE/$MODEL.zip"
fi

rm -rf "$TARGET" "$CACHE/$MODEL"
unzip -q "$CACHE/$MODEL.zip" -d "$CACHE"
mkdir -p "$(dirname "$TARGET")"
mv "$CACHE/$MODEL" "$TARGET"
# По uuid приложение понимает, что модель в APK сменилась и её надо распаковать заново.
echo "$MODEL" > "$TARGET/uuid"
du -sh "$TARGET"
