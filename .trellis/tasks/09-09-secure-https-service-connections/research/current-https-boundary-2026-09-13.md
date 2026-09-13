# Research: Current HTTPS boundary and implementation seams

- Query: Locate every production and test path that constructs a network client, parses or persists a Leanote endpoint, transfers attachments/images, bypasses TLS validation, follows redirects, or reports endpoint failures; determine a fail-closed migration for the supported `https://me.xiqi.site/` deployment.
- Scope: mixed (repository code, Trellis/ADR contracts, and current official platform/library references)
- Date: 2026-09-13

## Findings

### Governing decisions and task state

- `docs/adr/0006-require-valid-https-for-custom-services.md:1-3` requires HTTPS with the system trust chain and hostname verification, and forbids trust-all, hostname bypass, and production HTTP fallback.
- `.trellis/tasks/09-09-secure-https-service-connections/prd.md:5-35` additionally requires differentiated safe errors, preservation of local account data, a visible repair state, and uniform blocking of every network entry when a saved endpoint becomes unsafe.
- `.trellis/tasks/09-09-secure-https-service-connections/design.md:3-25` calls for one immutable `ServiceEndpoint`, one secure client factory, no HTTPS-to-HTTP redirect, and atomic endpoint replacement only after a real validation succeeds.
- The modernization scope names `https://me.xiqi.site/` as the only compatibility target (`.trellis/tasks/09-09-android-modernization-requirements/prd.md:15`). The HTTPS task design currently accepts any syntactically valid system-trusted HTTPS endpoint but promises compatibility only for that target (`design.md:5-7`). If product intent is now to connect **only** to `me.xiqi.site`, planning should explicitly change the endpoint allowlist; it should not be inferred silently during implementation.
- `task.json` currently has `status: planning` and `meta.depends_on: ["09-09-android-platform-baseline"]`. The dependency task is still `in_progress` on 2026-09-13, so `task.py start` must remain blocked until that task is completed and archived. Research does not activate either task.

### Current TLS bypass is unconditional

- `app/src/main/java/org/houxg/leamonax/network/ApiProvider.java:81-96` installs an `X509TrustManager` whose client/server checks are empty and whose accepted issuer list is empty.
- `ApiProvider.java:97-107` initializes an `SSLContext` with that manager, installs its socket factory, and installs a `HostnameVerifier` that always returns `true`.
- `ApiProvider.java:108-110` catches setup failure and only prints the exception. The client then continues with an implicit configuration, so initialization has a hidden security fallback rather than a typed fail-closed result.
- `ApiProvider.java:113-117` passes `host + "/api/"` directly to Retrofit. `SignInActivity.java:314-329` accepts both `http` and `https` with a loose regex, so plaintext API traffic is an intentional current path.
- No `networkSecurityConfig` or explicit `usesCleartextTraffic="false"` exists in `app/src/main/AndroidManifest.xml:11-17`. Target SDK 36 defaults are helpful but are not a substitute for typed endpoint validation, because callers can still construct non-HTTP stacks or libraries can customize TLS.

### Credentials and content are exposed to logs and redirects

- `ApiProvider.java:70-79` enables `HttpLoggingInterceptor.Level.BODY` for debug builds. Authentication uses GET query parameters for email/password (`network/api/AuthApi.java:12-19`), password changes use query parameters (`network/api/UserApi.java:16-20`), and the interceptor appends the access token to query strings (`ApiProvider.java:50-68`). BODY logging therefore records credentials, tokens, note multipart bodies, and response content.
- `NoteFileService.java:116-125,154-156` embeds the access token in an image URL and logs the complete URL. This is a direct token disclosure even without the OkHttp logging interceptor.
- `Account.java:178-193` includes `accessToken` in `toString()`. Any future structured account log can disclose it; this should be redacted as part of the same logging boundary.
- OkHttp 3.4.1 follows ordinary and cross-protocol redirects by default. Because the current network interceptor executes for each network request and reads `Account.getCurrent().getAccessToken()` dynamically (`ApiProvider.java:52-68`), a redirect retaining an API-looking path can cause the token to be appended to the redirected host. The safest contract for this API is `followRedirects(false)` and `followSslRedirects(false)`; treat every 3xx as a typed service/protocol failure instead of attempting a credential-bearing redirect.

### Three independent remote data paths exist

1. **Retrofit/OkHttp API path**: `ApiProvider.java:50-118` owns login, registration, user, notebook, note sync, note mutation, and multipart note/attachment upload. Representative callers are `AccountService.java:17-27`, `NoteService.java:89-120`, `NoteService.java:239-264`, and `NoteService.java:463-479`.
2. **JDK URL download path**: `NoteFileService.java:106-151` builds a token-bearing URL and calls `URI.toURL().openStream()`. It does not use `ApiProvider`'s client, timeout, endpoint validation, redirect policy, or error classification. Remote editor images/attachments therefore bypass any fix limited to Retrofit.
3. **Glide path**: `Leamonax.java:40` initializes the global image loader; `AccountAdapter.java:69-76`, `Navigation.java:441-449`, and `SettingsActivity.java:324-333` feed `Account.avatar` directly to Glide. `User.java:37-44` resolves a server-relative avatar by raw string concatenation with the current account host. This is a second remote-resource bypass and can accept a response-provided absolute URL outside the Leanote origin.

`PictureViewerActivity.java:36-43` and `NoteAdapter.java:190-203` load local files and are not remote bypasses. External browser links in About/Navigation are not Leanote API connections, but `Navigation.java:566-580` explicitly rewrites HTTPS to HTTP for the blog/explore links; those should be reviewed separately so a broad static scan does not falsely report the service boundary as clean.

### Endpoint/account binding is currently unsafe across account changes

- `ApiProvider.getInstance()` initializes only when `mApiRetrofit == null` (`ApiProvider.java:39-44`).
- `MainActivity.onChangeAccount()` updates `lastUseTime` and starts sync but does not rebuild or switch the provider (`MainActivity.java:172-179`; `Navigation.java:250-258`).
- The already-created Retrofit base URL is therefore still bound to the previous host, while the token interceptor reads the newly current account dynamically. A switched account's token can be sent to the previous account's host. This is both an account-isolation and HTTPS boundary defect.
- The migration should bind endpoint plus credential snapshot to an immutable per-account `ServiceClient` (or an equivalent keyed provider). Login/repair uses an unauthenticated client; authenticated operations use a client created for one account identity and endpoint. Never combine a cached base URL with a dynamic global token lookup.

### Saved-account repair state is not implemented

- `Account.java:23-54` persists host/token/USNs but has no endpoint-security state or failure category.
- `LaunchActivity.java:31-40` initializes the provider and routes every token-bearing account directly to `MainActivity`; there is no safe endpoint check or repair route.
- `NoteSyncService.java:31-39,55-100` gates only on the presence of a signed-in account and then starts remote work. Local editing remains available in `MainActivity.java:147-169`, which is useful, but network blocking is not centralized.
- `SignInActivity.java:184-186,314-329` is the only host input path. It mutates the singleton client before authentication and only distinguishes a loose regex error from a generic network error (`SignInActivity.java:219-227,278-286`).
- `AccountService.saveToAccount()` keys lookup by `(email, host)` and writes the host immediately after authentication (`AccountService.java:29-40`). A repair that reuses this path may create a second account row rather than atomically updating the original account, so a dedicated account-ID-based transaction is required.
- If endpoint security state is persisted in DBFlow, the current database version is 5 and migrations are centralized in `AppDataBase.java:22-167`. A new column/migration must preserve account, token, notes, attachments, and USNs. Alternatively, syntactic invalidity can be derived from the saved host, but certificate failures need a durable state if the UI must remain explicitly blocked across process restart.

### Recommended production seam and exact files

The implementation should preserve a single fact source rather than patching each caller independently:

- Add `app/src/main/java/org/houxg/leamonax/network/ServiceEndpoint.java` as the only parser/resolver. Require an absolute HTTPS URI, normalized lowercase DNS host, no user-info/query/fragment, and a deterministic trailing-slash/API-base rule. If the product decision is exact-target-only, require canonical authority `me.xiqi.site` and default port; otherwise retain the current design's generic valid-HTTPS rule but label all non-target deployments unsupported.
- Add a single secure client/provider seam under `network/` (for example `SecureHttpClientFactory` plus a typed `EndpointSecurityFailure`). Leave both `sslSocketFactory` and `hostnameVerifier` unset so OkHttp uses its platform/system defaults. Set both redirect flags false, use bounded timeouts, and remove BODY logging/Stetho network capture for credential-bearing calls.
- Refactor `ApiProvider.java` to consume `ServiceEndpoint` and an immutable account/credential binding. Initialization must return or throw a typed failure; it must never print-and-continue. Split unauthenticated auth/validation from authenticated account operations so token injection is explicit and cannot read `Account.getCurrent()` during an in-flight call.
- Route `NoteFileService.java` through the same client and endpoint resolver. Prefer a streaming Retrofit/OkHttp response keyed by `fileId`; do not construct/log a token-bearing `URL`, and publish a downloaded file only after a complete successful response.
- Resolve `User.avatar` through `ServiceEndpoint` and either download it through the shared client into an app-owned local file or install a Glide model loader backed by the same OkHttp client and exact-origin validator. Raw response URLs must not become arbitrary Glide network requests.
- Add explicit platform defense in `app/src/main/AndroidManifest.xml` and a minimal `res/xml/network_security_config.xml`: cleartext disabled and only system trust anchors, with no debug/user CA override. This is defense in depth; code-level endpoint/origin checks remain authoritative.
- Add/modify `Account.java`, `AccountDataStore.java`, `AppDataBase.java`, and `AccountService.java` for a durable, non-sensitive endpoint state and atomic repair-by-local-account-ID if the PRD's persisted repair-state requirement is retained.
- Modify `LaunchActivity`, `SignInActivity`, `SettingsActivity` (or a focused repair screen), account list rendering, and localized strings so invalid saved accounts enter local-only mode and expose repair without deleting local data. All sync/mutation/image entry points must fail before creating a network request.
- Remove token material from `Account.toString()`, `NoteFileService` logs, throwable printing, and any HTTP logger. User-facing categories can distinguish invalid address, insecure scheme/unsupported origin, untrusted/expired certificate, hostname mismatch, unreachable network, authentication, and service response; diagnostic logs should record only a safe category and request operation, never endpoint query strings or note content.

### Test files and coverage needed

There are currently no HTTPS/endpoint tests and no `MockWebServer` dependency. Existing tests under `app/src/test/` cover platform/editor/file seams only.

Suggested focused tests:

- `app/src/test/java/org/houxg/leamonax/network/ServiceEndpointTest.java`: canonical target, case/trailing slash normalization, missing host, user-info, query/fragment, HTTP, malformed URI, deceptive suffix (`me.xiqi.site.evil`), explicit non-default port, IDN/IP literal, and unsupported origin according to the final product rule.
- `app/src/test/java/org/houxg/leamonax/network/SecureHttpClientFactoryTest.java`: default hostname verifier/system trust are not replaced, redirect flags are false, and no logging interceptor can emit query/body content. Avoid asserting implementation class names when behavior can be asserted.
- `app/src/test/java/org/houxg/leamonax/network/ApiProviderAccountBindingTest.java`: account switch cannot combine old endpoint with new token; auth client has no token; authenticated client uses exactly one captured account identity; 3xx is not followed.
- `app/src/test/java/org/houxg/leamonax/network/NetworkBoundaryArchitectureTest.java`: scan production sources for `X509TrustManager`, custom `HostnameVerifier`, `sslSocketFactory`, `URL.openStream`, `http://` service constants, and independent remote image loaders. Every non-production/static-asset match must be explicitly allowlisted and explained.
- Instrumented/DB tests for saved HTTP/unsupported/certificate-blocked account launch, local browse/edit/export while blocked, no sync/upload/download request, failed repair preserving the old row, and successful real validation atomically updating only the intended account.
- Controlled TLS tests must separately exercise untrusted/self-signed, expired, and hostname-mismatch failures. A self-signed fixture alone cannot prove the error classifier distinguishes the latter two because chain trust may fail first. Tests may inject a test-only trust root into a test client, but production constructors must expose no custom-CA or trust-bypass switch.
- Verify remote image and attachment paths with a request-recording server: no cross-origin fetch, no redirect follow, no token/body log, partial downloads not published, and a blocked account makes zero requests.

`app/build.gradle:134-150` currently resolves an old OkHttp/Retrofit/Glide stack (logging-interceptor 3.4.1, Retrofit RxJava adapter 2.1.0, Glide 4.11.0, Stetho OkHttp 1.4.2), and no MockWebServer test dependency is declared. Dependency alignment should be inspected before adding test artifacts; do not introduce a second incompatible OkHttp line only for tests.

### External references and current target observation

- Android Network Security Configuration: <https://developer.android.com/privacy-and-security/security-config> — declarative cleartext and trust-anchor policy; fetched successfully on 2026-09-13.
- Android cleartext communications risk guidance: <https://developer.android.com/privacy-and-security/risks/cleartext-communications> — cleartext permits reading and manipulation of transmitted data; fetched successfully on 2026-09-13.
- Android `NetworkSecurityPolicy`: <https://developer.android.com/reference/android/security/NetworkSecurityPolicy> — runtime inspection API for cleartext policy; fetched successfully on 2026-09-13.
- OkHttp 3.4.1 source, `OkHttpClient`: <https://github.com/square/okhttp/blob/parent-3.4.1/okhttp/src/main/java/okhttp3/OkHttpClient.java#L224-L240> shows that the unset socket factory is built from the system-default trust manager and the builder's hostname verifier is used; lines 425-435 show default hostname verification and redirects; lines 706-715 document protocol-redirect behavior.
- A no-credential `curl` observation on 2026-09-13 reached `https://me.xiqi.site/` with HTTP 200, `ssl_verify_result=0`, and HSTS. This proves only that the current workstation can establish one system-verified HTTPS connection to the root page. It is not Android, API, login, token, attachment, redirect, or certificate-negative evidence.

### Activation and completion evidence gates

- **Activation blocker:** `09-09-android-platform-baseline` remains `in_progress`; the HTTPS task must not start until that dependency is completed according to `task.json.meta.depends_on`.
- **Automatable once activated:** endpoint/parser tests; account-binding tests; client/redirect tests; architecture scan; DB migration tests; `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `git diff --check` under the platform task's JDK 21/SDK 37 evidence contract.
- **Requires controlled TLS infrastructure:** valid chain, self-signed/untrusted CA, expired leaf, hostname mismatch, HTTP input, HTTPS-to-HTTP redirect, cross-origin HTTPS redirect, and proof that each blocked case emits zero follow-up credential-bearing requests.
- **Requires Android runtime evidence:** API 34+ device/emulator network behavior, merged manifest/network-security-config inspection, account local-only mode, process restart persistence, and all image/attachment callers using the same boundary.
- **Requires protected external evidence:** a temporary protected account against `https://me.xiqi.site/` for login plus a minimal target API/attachment smoke. Record operation/result/category and artifact hash only; do not persist email, password, token, response body, note text, or attachment bytes.
- Builds and unit tests cannot substitute for the device, controlled-certificate, live target, process-restart, or protected-credential evidence. Missing infrastructure must remain `blocked`/`unrun`, not inferred as passing.

## Caveats / Not Found

- `jbcontext search` was attempted first as required, but the repository index reported no matching `dev`/`master` index and returned no results. The inventory therefore used targeted source inspection and `rg`, excluding generated/build directories and the large vendored MathJax asset tree.
- No current test creates an OkHttp/Retrofit TLS client, uses MockWebServer, validates redirects, or exercises account endpoint repair.
- No supported credential source was available, so no authenticated call to `me.xiqi.site` was attempted.
- No controlled TLS fixture, Android device, emulator, or process-restart run was available in this research pass.
- The exact-target-only versus generic-valid-HTTPS rule is the main planning ambiguity. Current task artifacts say generic valid HTTPS with compatibility promised only for `me.xiqi.site`; implementation must not silently narrow or broaden that contract.
