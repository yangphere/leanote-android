# Research: Empty Files Marker and Android API Compatibility Boundary

- Query: Trace the Android request path for deleting the last image or attachment, map the target server's `FilesPresent` / `HasFiles` contract, identify test seams, and separate executable local evidence from protected real-service evidence.
- Scope: mixed (Android client plus the sibling Leanote server implementation that defines the supported `https://me.xiqi.site/` contract)
- Date: 2026-09-13

## Findings

### Root cause and end-to-end request path

1. The editor persists the changed note content and then prunes image relations before a later upload. `NoteEditActivity.saveAsDraft` updates the DB row and calls `NoteService.pruneUnusedNoteFiles` (`app/src/main/java/org/houxg/leamonax/ui/edit/NoteEditActivity.java:348-365`). The image-import rollback path also deletes a local relation through `NoteFileService.deleteLocalImage` (`app/src/main/java/org/houxg/leamonax/ui/edit/EditorFragment.java:317-327`; `app/src/main/java/org/houxg/leamonax/service/NoteFileService.java:57-76`).
2. `NoteService.saveNote` builds common multipart fields, calls `handleFileBodies`, and sends the same map either to `note/addNote` or `note/updateNote` (`app/src/main/java/org/houxg/leamonax/service/NoteService.java:239-263`). Retrofit declares those endpoints as multipart `@PartMap` plus file parts (`app/src/main/java/org/houxg/leamonax/network/api/NoteApi.java:30-36`).
3. `handleFileBodies` prunes again, reloads all remaining related `NoteFile` rows, and only emits `Files[index][LocalFileId]`, `IsAttach`, `FileId`, and `HasBody` from inside a non-empty-list branch (`app/src/main/java/org/houxg/leamonax/service/NoteService.java:380-399`). Consequently, after deletion of the last relation, the multipart request contains no `Files[...]`, `FilesPresent`, or `HasFiles` field. This is not a serialization problem in `Note`: response-side `Files` is represented by `Note.noteFiles` (`app/src/main/java/org/houxg/leamonax/model/Note.java:61-64`), while request-side file metadata is assembled dynamically in the service.
4. Authentication and routing are added below the service: `ApiProvider` uses the account host plus `/api/`, adds the access token as a query parameter for non-auth routes, and creates `NoteApi` from Retrofit (`app/src/main/java/org/houxg/leamonax/network/ApiProvider.java:50-69,113-130`). The current trust-all TLS implementation at `ApiProvider.java:81-107` is a separate blocking dependency owned by `09-09-secure-https-service-connections`; it must not be treated as acceptable real-service evidence.

### Target server contract

The sibling Leanote server contains an explicit compatibility rule in `app/controllers/api/ApiNoteController.go:495-508`:

- `FilesPresent`, `HasFiles`, `Files`, `Files[0][LocalFileId]`, or any form key prefixed by `Files[` means the client submitted the complete file set.
- If none is present, the server must interpret the request as "leave assets unchanged" because historical clients omit all `Files[...]` keys for an empty list.
- If presence is explicit, even with `noteOrContent.Files` empty, the controller constructs asset work and passes the complete requested list into fenced attachment reconciliation (`app/controllers/api/ApiNoteController.go:525-568`).

The bound payload remains `info.ApiNote.Files []NoteFile`, whose entries use `FileId`, `LocalFileId`, `Type`, `Title`, `HasBody`, and `IsAttach` (`app/info/Api.go:11-38`). On reconciliation, the server computes the retained attachment IDs from the submitted list and deletes rows/files absent from it (`app/service/AttachService.go:816-846`); an empty list therefore deletes all attachments for that note and updates `AttachNum`. Final-state verification explicitly compares the complete attachment set and count (`app/service/AttachService.go:726-784`). Image identity is additionally tied to note content; removing the last inline image must still send the changed `Content`, while the explicit empty marker ensures the asset list has unambiguous replacement semantics.

### Smallest structural fix

The marker belongs to the single owner of request-side file-set encoding, not to UI code, the response DTO, or Retrofit annotations.

Recommended shape:

- Extract a package-private, pure multipart-field encoder next to `NoteService` (or a package-private static method in it) that accepts the already-pruned `List<NoteFile>` and the `Map<String, RequestBody>`.
- The encoder must always add `FilesPresent=1`, including when the list is empty, then add the existing indexed metadata for every retained file. It can return the subset whose server ID is empty so the caller can create binary parts without re-deriving `HasBody` in a second source of truth.
- `handleFileBodies` remains responsible for DB pruning/loading and binary `MultipartBody.Part` construction, but delegates all file-set presence and metadata encoding to that one pure boundary.
- Keep the marker unconditional for both add and update requests because the shared builder describes the complete submitted set. The current target server accepts the marker, and an empty add naturally has an empty set. Do not encode emptiness as a fake `Files[0]` record, `FileIds=""`, or a UI-specific flag.
- Use the existing boolean wire convention (`1`/`0`) from `NoteService.java:46-49,547-549`; the server tests presence via `c.Has`, so the marker's semantic payload is only affirmative presence.

This is preferable to placing a lone `requestBodyMap.put` in `saveNote`: a pure file-set encoder gives one testable owner for marker presence, indexed fields, and upload selection, while keeping database and Android framework dependencies out of the contract test.

### RED / GREEN tests

Add focused JVM tests in `app/src/test/java/org/houxg/leamonax/service/` against the package-private encoder:

1. **RED: empty complete set** — encode `Collections.emptyList()` and assert the map contains exactly the affirmative `FilesPresent` contract field among file-related keys. This fails against current behavior because no file field is emitted.
2. **GREEN: retained remote file** — encode one `NoteFile` with both local/server IDs and assert `FilesPresent`, all four indexed metadata keys, `HasBody=0`, and no upload candidate. This prevents the empty-list fix from changing retained identity.
3. **GREEN: new local file** — encode one file without a server ID and assert `HasBody=1` plus exactly one upload candidate. Test both `IsAttach=0` and `IsAttach=1` if the helper owns that conversion.
4. Read `RequestBody` values into an Okio buffer for assertions; do not assert only that a helper constant equals itself. Also add a source-level or request-construction integration assertion that `handleFileBodies` invokes the encoder after pruning/loading so the pure helper cannot become an unused, tautological test seam.

No new mocking framework is needed: the project already has JUnit 4.13.2 and Mockito 5.20.0 (`app/build.gradle:96-105`), and OkHttp/Okio are production dependencies. A broader Retrofit request test may be added later, but by itself testing `NoteApi.update(map, parts)` would only prove Retrofit serializes a supplied map; it would not prove `NoteService` supplies the marker.

### Broader task evidence map

The marker fix closes only one executable request-contract defect. The task PRD and implementation plan require a broader compatibility baseline:

- **Locally executable now:** fixed request/response fixtures; endpoint method/path/field tests; DTO and mapping tests; non-success and malformed-response propagation; USN pagination and cursor advancement; deletion, offline dirty upload, concurrent conflict-copy behavior; Markdown versus HTML round-trip; image/attachment identity and empty-set replacement; focused JVM tests followed by `testDebugUnitTest`, `lintDebug`, and `git diff --check`.
- **Requires a runnable Android build environment:** the full Gradle gates depend on JDK 21 plus Android SDK platform 37. The platform task currently records these current-run checks as blocked, so historical build/device results cannot be promoted to current verification.
- **Requires protected real-service execution:** login and all target API calls against only `https://me.xiqi.site/`; creation/update/read; multi-page USN; conflict; image and attachment upload/download/delete; and cleanup. Credentials must come from protected environment inputs, logs must exclude token/password/note body/attachment bytes, and every mutation must be limited to run-created IDs in a resource ledger. Cleanup must run by those exact IDs in reverse order; cleanup failure is a failed result with redacted leftover IDs.
- **Requires predecessor closure:** `09-09-android-platform-baseline` is still `in_progress`, and `09-09-secure-https-service-connections` is still `planning`. The API task's `task.json.meta.depends_on` requires both completed before activation. Real-service evidence must use the shared system-trusted, hostname-verified HTTPS boundary; the current `ApiProvider` trust-all client cannot produce admissible evidence.
- **Not substitutable:** fixture/JVM success cannot establish the deployed service's current USN/conflict behavior; direct HTTP scripts cannot establish the final Android network stack; service success cannot establish device/UI behavior; and historical runner/device evidence cannot close a current-run gate.

### Files found

- `app/src/main/java/org/houxg/leamonax/service/NoteService.java` — owns note save, file-relation pruning, multipart metadata, and binary file part creation.
- `app/src/main/java/org/houxg/leamonax/network/api/NoteApi.java` — Retrofit note endpoint definitions.
- `app/src/main/java/org/houxg/leamonax/network/ApiProvider.java` — base URL, token query injection, Retrofit client, and currently unsafe TLS configuration.
- `app/src/main/java/org/houxg/leamonax/model/Note.java` — response-side note and `Files` mapping.
- `app/src/main/java/org/houxg/leamonax/model/NoteFile.java` — local/server file identities and upload metadata.
- `app/src/main/java/org/houxg/leamonax/database/NoteFileDataStore.java` — loads and removes note/file relations.
- `app/src/main/java/org/houxg/leamonax/ui/edit/NoteEditActivity.java` — persists edited content and triggers pruning/save.
- `app/src/main/java/org/houxg/leamonax/ui/edit/EditorFragment.java` — image insertion and rollback deletion path.
- Sibling server `app/controllers/api/ApiNoteController.go` — authoritative empty-list presence-marker interpretation for the supported deployment.
- Sibling server `app/info/Api.go` — bound `ApiNote` / `NoteFile` field contract.
- Sibling server `app/service/AttachService.go` — complete-set attachment reconciliation and verification.

### External references and versions

- No third-party web source is needed to establish marker semantics; the supported contract is explicitly the current Leanote deployment represented by the sibling server code.
- Relevant client toolchain snapshot: compile SDK 37, min SDK 34, target SDK 36, Java source level 17 (`app/build.gradle:24-67`); JUnit 4.13.2 and Mockito 5.20.0 (`app/build.gradle:96-105`); OkHttp logging interceptor 3.4.1 and Retrofit RxJava adapter 2.1.0 (`app/build.gradle:134-145`); Gradle wrapper 9.6.0 (`gradle/wrapper/gradle-wrapper.properties:1-7`). Actual Gradle execution remains subject to the platform task's JDK/SDK evidence gate.

### Related specs and task artifacts

- `.trellis/tasks/09-09-leanote-api-content-compatibility/prd.md` — target-service scope, content/attachment invariants, protected credentials, resource ledger, and acceptance gates.
- `.trellis/tasks/09-09-leanote-api-content-compatibility/design.md` — fixture plus real-service dual verification and typed-boundary requirements.
- `.trellis/tasks/09-09-leanote-api-content-compatibility/implement.md` — inventory, client boundary, real-service, local validation, and rollback checklist.
- `.trellis/spec/guides/cross-layer-thinking-guide.md` — requires one owner for payload fields and complete boundary testing.
- `.trellis/spec/guides/code-reuse-thinking-guide.md` — prohibits duplicated interpretation of the same payload fields.
- `.trellis/tasks/09-09-android-platform-baseline/prd.md` and `.trellis/tasks/09-09-secure-https-service-connections/prd.md` — executable dependency and admissible-network-evidence boundaries.

## Caveats / Not Found

- `jbcontext search` could not run because no index exists for repository `github.com/yangphere/leanote-android` on the current branch; findings were obtained through targeted local reads and exact-text search after that failure.
- No existing Android unit test covers `NoteService` multipart note-save construction, `FilesPresent`, `HasFiles`, or empty file-set replacement. Existing service tests focus on selected-image storage and MIME handling.
- The current editor's `createAttach` method returns `null` (`app/src/main/java/org/houxg/leamonax/ui/edit/NoteEditActivity.java:368-375`), so new attachment creation is not presently an editor UI seam. Remote attachment retention/deletion still remains part of the synchronization contract and must be covered at service/request level.
- The sibling server source is strong evidence for intended/current repository behavior but is not proof that the live `https://me.xiqi.site/` deployment runs the exact same revision. Protected real-service verification remains mandatory.
- No Gradle tests, device tests, network requests, or live-service operations were run during this research-only pass.
