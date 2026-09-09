# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Android application. Project-wide Gradle configuration lives in `build.gradle`, while application settings and dependencies are in `app/build.gradle`. Production Java code is under `app/src/main/java/org/houxg/leamonax`, grouped by responsibility (`ui`, `service`, `network`, `database`, `editor`, and related packages). Android resources live in `app/src/main/res`; bundled editor JavaScript, CSS, fonts, and HTML are in `app/src/main/assets`. Local JVM tests belong in `app/src/test`, device tests in `app/src/androidTest`, and README images in `screenshot/`.

## Build, Test, and Development Commands

Use the checked-in Gradle wrapper from the repository root. The legacy toolchain expects JDK 8, Android SDK 26, and Build Tools 28.0.3.

- `./gradlew assembleDebug` (`.\gradlew.bat assembleDebug` on Windows): build a debug APK.
- `./gradlew installDebug`: install the debug build on a connected device or emulator.
- `./gradlew testDebugUnitTest`: run local JVM unit tests.
- `./gradlew connectedDebugAndroidTest`: run instrumentation tests on a connected target.
- `./gradlew lint`: run Android lint; review all findings even though the build currently does not abort on lint errors.
- `./gradlew clean`: remove generated build output.

## Coding Style & Naming Conventions

Match nearby Java and Kotlin code: four-space indentation, braces on the declaration line, and imports grouped before declarations. Use `UpperCamelCase` for classes, `lowerCamelCase` for methods and fields, and lowercase package names. Name Android resources with descriptive `snake_case` identifiers such as `activity_edit_note.xml`. Keep UI, persistence, network, and service logic in their existing packages; avoid introducing a second source of truth across layers.

## Testing Guidelines

Tests use JUnit 4, with Mockito available for JVM tests and Espresso/AndroidJUnit4 for device tests. Name test classes `*Test` and test methods after the behavior under test. Add focused regression coverage for bug fixes. No coverage threshold is configured, so PRs should state which test tasks were run and identify any device-only checks that remain.

## Commit & Pull Request Guidelines

History favors short, imperative subjects such as `Fix TitleHighlight`, `Add ...`, or `Fix #60 ...`; include an issue number when applicable. Keep each commit focused. Pull requests should explain the behavior change, link relevant issues, list verification commands and target Android versions, and include before/after screenshots for UI or resource changes.

## Security & Configuration

Keep `local.properties`, the decrypted keystore, and credentials out of Git. Release signing reads `KEY_ALIAS`, `KEY_PWD`, and `KEYSTORE_PWD`; Bugly configuration reads `BUGLY_PRD`. Provide these through the environment rather than source files.
<!-- TRELLIS:START -->
# Trellis Instructions

These instructions are for AI assistants working in this project.

This project is managed by Trellis. The working knowledge you need lives under `.trellis/`:

- `.trellis/workflow.md` — development phases, when to create tasks, skill routing
- `.trellis/spec/` — package- and layer-scoped coding guidelines (read before writing code in a given layer)
- `.trellis/workspace/` — per-developer journals and session traces
- `.trellis/tasks/` — active and archived tasks (PRDs, research, jsonl context)

If a Trellis command is available on your platform (e.g. `/trellis:finish-work`, `/trellis:continue`), prefer it over manual steps. Not every platform exposes every command.

If you're using Codex or another agent-capable tool, additional project-scoped helpers may live in:
- `.agents/skills/` — reusable Trellis skills
- `.codex/agents/` — optional custom subagents

Managed by Trellis. Edits outside this block are preserved; edits inside may be overwritten by a future `trellis update`.

<!-- TRELLIS:END -->
