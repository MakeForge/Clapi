#!/bin/bash
INPUT_SVG=$1
SIZES=(16 32 64 128 192 256 512 1024)

mkdir -p icons

# 1. Generate all individual PNGs first
for SIZE in "${SIZES[@]}"; do
    resvg -w "$SIZE" -h "$SIZE" "$INPUT_SVG" "clapi-icon-${SIZE}.png"
    echo "Generated crisp clapi-icon-${SIZE}.png"
done

# 2. Bundle into Windows .ico (Run OUTSIDE the loop, fixed filename typo)
echo "Generating clapi.ico..."
convert clapi-icon-16.png \
       clapi-icon-32.png \
       clapi-icon-48.png \
       clapi-icon-64.png \
       clapi-icon-128.png \
       clapi-icon-256.png \
       clapi.ico

# 3. Bundle into macOS .icns (Run OUTSIDE the loop, omitted 64x to prevent dimension errors)
echo "Generating clapi.icns..."
png2icns clapi.icns \
    clapi-icon-16.png \
    clapi-icon-32.png \
    clapi-icon-128.png \
    clapi-icon-256.png \
    clapi-icon-512.png

echo "All icons, .ico, and .icns successfully generated!"