# Android Platform Baseline

> Executable build, signing, and verification contracts for the single-module Android application.

## Scenario: Build and Release the Android 14+ Client

### 1. Scope / Trigger

Apply this contract whenever changing the Gradle toolchain, Android SDK levels,
dependencies, manifest, application startup, Compose infrastructure, or release
signing. The repository contains one Android application module, `:app`.

This baseline does not define the Room model, Leanote API behavior, sync
semantics, WebView editor bridge, or TLS policy. Those contracts belong to their
own Trellis tasks and specs.

### 2. Signatures

- Debug APK: `.\gradlew.bat assembleDebug`
- JVM tests: `.\gradlew.bat testDebugUnitTest --no-configuration-cache`
- Android lint: `.\gradlew.bat lintDebug --no-configuration-cache`
- Release artifact: `.\gradlew.bat assembleRelease`
- Application module: `:app`
- Namespace: `org.houxg.leamonax`
- Application ID: `com.leanote.android`
- SDK contract: `minSdk 34`, `targetSdk 36`, `compileSdk 37`
- Java source and target compatibility: 17
- Gradle wrapper / AGP: 9.6.0 / 9.4.0

### 3. Contracts

#### Toolchain

- Run Gradle with JDK 17 or newer; JDK 17 is the repository baseline.
- Keep Kotlin/Compose Compiler, KSP, Compose BOM, Hilt, Gradle, and AGP versions
  explicit in version-controlled build files. Do not use dynamic versions such
  as `latest.release`.
- Resolve application dependencies only through `google()`, `mavenCentral()`,
  and the explicitly declared HTTPS JitPack repository in `build.gradle`.
  Plugin resolution separately uses `google()`, `mavenCentral()`, and
  `gradlePluginPortal()` under `pluginManagement` in `settings.gradle`.
  Gradle Plugin Portal is a plugin source, not an application dependency
  repository. Do not restore JCenter, Bintray, an HTTP repository, or an
  unverified mirror in either scope.
- Keep configuration cache enabled and verify that a second `assembleDebug`
  invocation reuses it.

#### Release signing environment

The release keystore path is `leanote-android-new.jks` at the repository root.
It is local secret material and must not be committed. All of these environment
variables are required together:

- `KEY_ALIAS`
- `KEY_PWD`
- `KEYSTORE_PWD`

`BUGLY_PRD` is optional and becomes an empty `BuildConfig.BUGLY_KEY` when it is
absent. `TRAVIS_TAG` and `TRAVIS_BUILD_NUMBER` may supply the version name and
version code; their existing staging defaults remain valid for local debug
builds.

Missing release signing material is an error. The build must not silently
produce an unsigned release APK or fall back to debug signing.

#### Transitional compatibility switches

The following switches are temporary compatibility bridges for legacy
ButterKnife, DBFlow, and support-era dependencies:

- `android.enableJetifier=true`
- `android.nonFinalResIds=false`
- `android.enableAppCompileTimeRClass=false`
- the narrowly scoped `jdk.compiler` exports in `org.gradle.jvmargs`

Do not copy these switches to another module or treat them as permanent. Remove
the affected legacy processors and dependencies before upgrading to AGP 10,
then delete the switches.

#### Edge-to-edge and large screens

- Every visible View-based activity extends `BaseActivity`.
  `BaseActivity.onCreate` calls `WindowCompat.setDecorFitsSystemWindows(window,
  false)` before content is installed. `onPostCreate` applies the maximum of
  system-bar, display-cutout, and IME insets to the content root while
  preserving its original padding.
- A View screen must not add a second full-screen inset padding owner. If a
  component needs local inset behavior, subtract or consume the root-owned
  inset explicitly and add a device test for the resulting layout.
- Compose hosts use `WindowCompat.setDecorFitsSystemWindows(window, false)`,
  `WindowInsets.safeDrawing`, and `imePadding()` at their screen root.
- The manifest must not request a fixed orientation. API 36 large-screen and
  resizable-window behavior is a layout invariant, not a manufacturer-name or
  orientation-lock branch.

#### Native libraries and install permissions

- The client has no in-app APK installer owner. The merged manifest must not
  contain `android.permission.REQUEST_INSTALL_PACKAGES` or a Bugly upgrade
  activity.
- Bugly is limited to the Java crash reporter. Disable native monitoring and
  exclude `libBugly_Native.so` from packaging. Inspect the final APK rather
  than inferring the result from dependency declarations.
- Run Android lint's 16 KB checks and `zipalign -c -P 16 -v 4` against the
  packaged APK. If any `.so` remains, also inspect its ELF load alignment and
  run on a 16 KB page-size target; ZIP alignment alone is not ELF evidence.

#### Photo Picker managed-file lifecycle

- Launch `MediaStore.ACTION_PICK_IMAGES` with `image/*`; do not request camera
  or broad storage permissions for this flow.
- Resolve the selected URI through `ContentResolver`, require a non-null stream,
  and accept only MIME values listed by `SelectedImageStore`. MIME is preserved
  as the managed file suffix; unknown MIME fails before a destination file is
  retained. Upload MIME resolution must always return a non-null media type,
  using `application/octet-stream` only for a readable legacy file whose suffix
  is unknown.
- Copy on a background executor to `filesDir/selected-images`. A Fragment-scoped
  `ImageImportViewModel` owns the running import and pending result across view
  recreation; only a live view observes the result. `onCleared` interrupts the
  executor and deletes any completed but unacknowledged copy.
- Persist the `NoteFile` relationship before inserting its local URI. A failed
  copy or relationship write deletes the managed copy; an immediate editor
  insertion failure attempts to roll back the persisted relationship and always
  deletes its managed copy. If rollback fails, preserve that failure as
  suppressed evidence on the insertion error; it must not retain the copy.
- Server relationship reconciliation must still run when the remote file list
  is empty or null; an empty authoritative set removes stale local rows before
  attempting managed-file cleanup. Inline-image pruning must reserve every
  `isAttach=true` relationship because attachments are not represented in note
  body markup.
  Removing an image from content, replacing the server relationship set,
  deleting a note, or deleting an empty new note must delete the relationship
  first and then attempt managed file cleanup. Paths outside the managed
  directory are never deleted.
- A missing upload body is an explicit failure that identifies the local file
  ID. Do not return a null multipart part or defer the error to Retrofit.

### 4. Validation & Error Matrix

| Condition | Required result |
|---|---|
| Android SDK 37 is unavailable | Gradle fails with an actionable SDK/platform error; do not lower `compileSdk` |
| Gradle runs on a JDK older than 17 | Build fails; do not add source-level compatibility fallbacks |
| Any release keystore or required signing variable is absent | `verifyReleaseSigning` fails before release packaging |
| A dynamic or HTTP dependency repository is introduced | Review fails and the repository entry must be removed |
| Android lint reports an error | `lintDebug` fails; do not set `abortOnError false` |
| A third-party lint false positive is proven | Add the narrowest issue-and-message/path suppression to `app/lint.xml` |
| API 34+ media selection is needed | Use the system Photo Picker; do not request legacy broad storage permissions |
| Picker MIME is null or unsupported | Reject the import and retain no managed file or `NoteFile` relationship |
| URI copy or relationship persistence fails | Surface the error and delete the partial or completed managed copy |
| Editor insertion fails after relationship persistence | Attempt to roll back the relationship, delete the managed copy even when rollback fails, and preserve rollback failure as suppressed evidence |
| A managed relationship is removed | Delete the database row first, then delete only a file owned by `SelectedImageStore` |
| An upload body is absent | Fail before request creation and include the `NoteFile.localId` in the error |
| A visible Activity does not inherit the shared inset owner | Review fails unless the Activity provides an equivalent tested Compose inset owner |
| The merged manifest contains an orientation lock or install-packages permission | Review fails; remove it unless a separately approved product owner exists |
| A packaged native library is present | Prove ZIP and ELF 16 KB compatibility plus 16 KB runtime loading before release |
| No API 34+ device is available | Keep install, cold-start, navigation, picker, back, and Compose UI evidence open |
| Production signing keys are unavailable | Verify fail-closed behavior only; do not claim release-signing evidence |

### 5. Good / Base / Bad Cases

- Good: JDK 17+, SDK 37, and the checked-in wrapper produce a debug APK; all JVM
  tests pass; lint has zero errors; a second debug build reuses configuration
  cache.
- Base: No release secrets are present. Debug verification passes and
  `assembleRelease` fails explicitly in `verifyReleaseSigning`.
- Good: A Picker result survives view recreation, inserts one local image URI,
  and is acknowledged once; relation removal deletes its managed private copy.
- Base: A readable legacy attachment has an unknown suffix. Upload uses
  `application/octet-stream` without guessing an image type.
- Bad: The build lowers SDK levels, adds JCenter, suppresses all lint errors, or
  emits an unsigned release artifact to make CI appear green.
- Bad: A Fragment owns a raw Picker stream, copies on the main thread, silently
  accepts a null MIME, or deletes arbitrary paths received from the database.

### 6. Tests Required

For every platform-baseline change:

1. Run `lintDebug`, `testDebugUnitTest`, and `assembleDebug` from a clean Gradle
   invocation. Assert zero lint errors and all JVM tests passing.
2. Run `assembleDebug` again. Assert configuration cache reuse.
3. Inspect the APK. Assert application ID `com.leanote.android`, min SDK 34,
   target SDK 36, and a valid debug signature.
4. Run `assembleRelease` without secret material in a controlled environment.
   Assert failure names the keystore and required environment variables and no
   release artifact is published.
5. Scan source and repositories. Assert there is no `android.support` import,
   HTTP/Maven legacy repository, dynamic dependency version, committed
   credential, manifest orientation lock, `REQUEST_INSTALL_PACKAGES`, Bugly
   upgrade activity, or packaged `libBugly_Native.so`.
6. On an API 34+ device or emulator, test fresh install, cold start, basic
   navigation, system Photo Picker, predictive back, and the Compose/Hilt smoke
   activity. If unavailable, record each item as unverified.
7. Run `SelectedImageStoreTest`, `UploadMimeTypesTest`,
   `ImageImportViewModelTest`, `ImageImportViewModelRaceTest`, and
   `ImageImportResultHandlerTest`. Assert supported MIME copying, unknown MIME
   rejection without residue, partial-copy cleanup, external-path protection,
   non-null upload MIME, local-ID upload errors, retained results across view
    recreation, delayed-delivery cleanup, persistence-failure cleanup, and
    relationship rollback after an immediate editor insertion failure, including
    managed-copy deletion when rollback itself fails. Add reconciliation coverage
    for an empty remote file set and for preserving `isAttach=true` rows while
    pruning inline images.
8. Exercise API 35+ gesture and three-button navigation plus IME, and API 36
   rotation/resizing on a large-screen target. Static manifest and layout scans
   do not close these visual behavior checks.
9. Run `zipalign -c -P 16 -v 4` on the APK and list packaged native libraries.
   For each remaining `.so`, require ELF and 16 KB runtime evidence.

### 7. Wrong vs Correct

#### Wrong

```groovy
repositories {
    jcenter()
}

android {
    lint {
        abortOnError false
    }
}

buildTypes {
    release {
        // Produces an artifact even when production signing is missing.
    }
}
```

This restores an unavailable dependency source, makes lint fail open, and
allows ambiguous release artifacts.

#### Correct

```groovy
repositories {
    google()
    mavenCentral()
    maven { url = uri('https://jitpack.io') }
}

def verifyReleaseSigning = tasks.register('verifyReleaseSigning') {
    doLast {
        if (!releaseSigningConfigured) {
            throw new GradleException(
                    'Release signing requires leanote-android-new.jks and KEY_ALIAS, KEY_PWD, KEYSTORE_PWD.'
            )
        }
    }
}
```

Lint remains fail closed, dependency sources are explicit HTTPS repositories,
and release packaging has a deterministic signing precondition.

For Picker files, deleting any path returned by a provider or database is
incorrect:

```java
// Wrong: the path may not belong to this application.
new File(path).delete();

// Correct: canonical ownership is checked before deletion.
SelectedImageStore.from(context).deleteIfManaged(new File(path));
```
