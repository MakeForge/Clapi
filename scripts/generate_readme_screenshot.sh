convert docs/public/generated/screenshots/MainScreenPreviewsKt.MainScreenHttpPreviewDark.W1600dp_H800dp.png -alpha set \
  \( +clone -alpha extract -draw 'fill black polygon 0,0 0,8 8,0 fill white circle 8,8 8,0' \
     \( +clone -flip \) -compose Multiply -composite \
     \( +clone -flop \) -compose Multiply -composite \
  \) -alpha off -compose CopyOpacity -composite \
  \( -size 1600x800 xc:none -fill none -stroke "rgba(128,128,128,0.35)" -strokewidth 1 -draw "roundrectangle 0,0 1599,799 8,8" \) \
  -compose Over -composite \
  \( +clone -background black -shadow 35x16+0+8 \) +swap \
  -background none -layers merge +repage docs/public/generated/screenshots/MainScreenHttpPreviewDark-styled.png