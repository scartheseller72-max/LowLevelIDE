# Changelog

All notable changes to LowLevelIDE are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.1.0] - 2026-05-31

### Added
- Editor colour schemes: Material Darker, Dracula, Monokai, Ayu Dark, Eclipse, IntelliJ (Settings → Appearance).
- Soft word-wrap toggle for the editor.
- Terminal colour palettes with full 16-colour ANSI tables: GitHub Dark, Solarized Dark, Dracula, Gruvbox Dark, Nord, Light.
- Storage Access Framework file importer in the file browser.
- Quick Git action menu (status, add, commit, log, pull, push, init) driving the active terminal.
- Global crash handler that persists reports to `<filesDir>/crash/` and shares them via `FileProvider`.
- Adaptive launcher icon with an Android 13 themed-icon monochrome layer; legacy raster mipmaps for API 24–25.
- GitHub Actions CI: debug APK build + lint on every push and pull request.

### Changed
- Committed `gradle-wrapper.jar` so the project builds without Android Studio.
- Termux terminal libraries now resolve from JitPack (zero-config) instead of unpublished Maven Central coordinates.
- Refreshed Material 3 palette to a teal/emerald brand identity (light + dark).
- Editor preferences are now applied in `onPageFinished`, removing a startup race.
- Version bumped to 2.1 (versionCode 3).

### Fixed
- Missing launcher bitmaps on pre-API-26 devices.

## [2.0.0] - 2026-05-05

### Added
- Initial public scaffold: terminal (Termux), CodeMirror editor, Alpine/PRoot bootstrap,
  drawer navigation, onboarding, settings, and foreground terminal service.
