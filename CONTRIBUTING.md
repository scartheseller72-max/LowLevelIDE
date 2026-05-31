# Contributing to LowLevelIDE

Thanks for your interest in improving LowLevelIDE. This document covers how to get a build
running and the conventions we follow.

## Getting started

1. Install Android Studio (Hedgehog or newer) or a standalone Android SDK + JDK 17.
2. Fork and clone the repository.
3. Copy `local.properties.example` to `local.properties` and set `sdk.dir`.
4. Build: `./gradlew :app:assembleDebug`
5. Lint: `./gradlew :app:lintDebug`

The Gradle wrapper JAR is committed, so no separate Gradle install is required.

## Project layout

- `app/src/main/java/com/example/lowlevelide/` — Kotlin sources, grouped by feature
  (`terminal/`, `ui/`, `bootstrap/`, `files/`, `settings/`, `system/`, `util/`).
- `app/src/main/assets/editor/` — the CodeMirror host page and bridge JS.
- `app/src/main/res/` — layouts, drawables, themes, strings.

## Coding conventions

- Kotlin official style (`kotlin.code.style=official`); keep functions small and documented.
- Prefer `DataStore`-backed flows in `AppSettings` for any new persistent preference.
- Anything reaching into Termux/Android internals via reflection must be wrapped so a failure
  degrades gracefully — never crash the app for a cosmetic feature.
- Add user-facing text to `res/values/strings.xml`; do not hard-code strings.

## Commit & PR process

1. Branch from `main` using a descriptive name (`feature/…`, `fix/…`).
2. Keep commits focused; write imperative subject lines ("Add terminal palette engine").
3. Update `CHANGELOG.md` under an `Unreleased`/next-version section.
4. Ensure `assembleDebug` and `lintDebug` pass locally; CI runs both on every PR.
5. Open a pull request and fill in the template.

## Reporting bugs / requesting features

Use the issue templates. For anything security-sensitive, follow [SECURITY.md](SECURITY.md)
instead of opening a public issue.
