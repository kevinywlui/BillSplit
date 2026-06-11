# TODO

_Nothing actively blocking. Add real, verified follow-ups here as they come up._

## Resolved / no longer applicable

- **16 KB page alignment (Android 15).** Previously tracked here because ML Kit's
  `libmlkit_google_ocr_pipeline.so` was not 16 KB page-aligned. Receipt parsing now goes
  straight to Claude's vision API and ML Kit is no longer a dependency (it is not declared in
  `app/build.gradle.kts` and is not imported anywhere), so that native library is no longer
  packaged. If a 16 KB-alignment warning resurfaces from another `.so`, identify the owning
  library and bump it.
