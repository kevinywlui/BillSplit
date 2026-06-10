# TODO

## 16 KB Page Alignment (Android 15)

ML Kit (`libmlkit_google_ocr_pipeline.so`) and `libandroidx.graphics.path.so` are not 16 KB
page-aligned, causing a warning on Pixel 9 (Android 15). `useLegacyPackaging = true` in
`app/build.gradle.kts` did not resolve it.

Proper fix requires upstream library updates. Revisit when newer versions of
`com.google.mlkit:text-recognition` and `androidx.graphics:graphics-path` are available.
