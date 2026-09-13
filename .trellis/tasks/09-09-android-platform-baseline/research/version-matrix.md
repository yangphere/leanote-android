# Android 平台基线版本与验证证据

查询与验证日期：2026-09-09。

## 版本矩阵

| 组件 | 采用版本 | 依据 |
|---|---:|---|
| JDK | 21 | 任务所有者已确认 JDK 21 为仓库与 CI 基线；AGP 9.4 的最低 JDK 为 17，JDK 21 满足兼容要求。 |
| Gradle Wrapper | 9.6.0 | AGP 9.4 的兼容 Gradle 为 9.6。 |
| Android Gradle Plugin | 9.4.0 | 官方发布说明标明最高支持 API 37、Gradle 9.6、JDK 17 和 Build Tools 36.0.0。 |
| Kotlin / Compose Compiler plugin | 2.3.21 | AGP 9+ 使用内置 Kotlin；Compose Compiler Gradle plugin 与 Kotlin 版本一致。 |
| KSP | 2.3.11 | 与当前内置 Kotlin 构建链配套，供 Hilt 编译器使用。 |
| Compose BOM | 2026.08.00 | Android 官方 Compose BOM 当前稳定版本；Compose 1.12 起要求 compileSdk 37 和 AGP 9。 |
| Hilt | 2.60.1 | 当前稳定 Dagger/Hilt 版本，通过 KSP 接入。 |
| SDK | min 34 / target 36 / compile 37 | 产品 ADR 固定的平台边界。 |

官方来源：

- Android Gradle Plugin 9.4.0 release notes: <https://developer.android.com/build/releases/agp-9-4-0-release-notes>
- Migrate to built-in Kotlin: <https://developer.android.com/build/migrate-to-built-in-kotlin>
- Compose dependencies and compiler setup: <https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler>
- Compose BOM mapping: <https://developer.android.com/develop/ui/compose/bom/bom-mapping>
- Dagger releases: <https://github.com/google/dagger/releases>
- KSP releases: <https://github.com/google/ksp/releases>

## 迁移兼容桥

- Support Library 已统一迁移为 AndroidX。应用依赖仓库仅保留 `google()`、`mavenCentral()` 和 HTTPS JitPack；`settings.gradle` 的 `pluginManagement` 另使用 Gradle Plugin Portal 解析 Gradle 插件，不把它表述为应用依赖仓库。
- 旧 DBFlow 仅升级到仍可解析的 4.2.4 并做编译 API 适配；数据库替换属于后续 Room 任务。
- ButterKnife 10.2.3 仍要求最终编译期 R 常量和 JDK compiler exports，因此暂时启用 `android.nonFinalResIds=false`、`android.enableAppCompileTimeRClass=false` 和显式 `--add-exports`。这些选项在 AGP 10 会移除，必须在升级 AGP 10 前删除 ButterKnife 兼容桥。
- Jetifier 仅用于尚未完成 AndroidX 发布的旧依赖，同样是 AGP 10 前必须清理的过渡项。
- Android 14 图片选择使用系统 Photo Picker；不再在启动时请求已失效的外部存储、相机或电话权限。
- 新版 Joda 对 RFC 3339 解析更严格。时间解析保留目标服务既有的一位数时区 offset 兼容，并将超过毫秒精度的小数秒截到毫秒；发送时始终按 UTC 输出 `Z`，不会把本地时间误标为 UTC。
- 已移除 Bugly upgrade/Beta 组件与应用内 APK 安装权限；仅保留固定版本 `com.tencent.bugly:crashreport:4.1.9.3`，关闭 native crash monitor，并从最终打包中排除 `libBugly_Native.so`。

## 已确认交付与生命周期策略

- 首发只发布 APK；GitHub Actions 是唯一受支持的 CI/发布入口，生产 workflow 使用 JDK 21 和 SDK platform 37，`.travis.yml` 不得触发发布。
- `leanote-android-new.jks.enc` 不再作为仓库签名输入。GitHub Actions `production` Environment 提供 Base64 keystore、`KEY_ALIAS`、`KEY_PWD` 和 `KEYSTORE_PWD`；keystore 仅在 runner 临时目录存在，任务结束时清理。
- 签名私钥保持长期稳定；执行访问审计和离线备份，不做会破坏 APK 更新能力的随意换钥。密钥策略已确认，实际 Environment 配置与签名构建仍待验证。
- Photo Picker 进程终止策略已确认：持久账本记录导入状态，未确认记录转为 `abandoned`，应用启动清扫并由 WorkManager 补偿；清扫前核对关系 owner，未确认选择不恢复，用户重新选择，配置重建保留 pending。

## 已记录验证（历史受控环境）

以下结果来自先前的受控执行记录。原记录没有完整保存执行主机、`JAVA_HOME`、Gradle Daemon JVM 和 Android SDK 路径；在当前 shell 不能重放，因此只能作为 `verified (historical)`，不能覆盖本次审计的 `blocked` 项。

- `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-configuration-cache`：成功；20 个 JVM 测试全部通过，lint 为 0 errors / 284 warnings。
- `.\gradlew.bat assembleDebug`：连续执行成功，第二次复用配置缓存。
- `.\gradlew.bat assembleRelease`（无发布签名材料）：按预期在 `verifyReleaseSigning` 明确失败，未降级为未签名 release 产物；首发格式为 APK。
- APK 元数据：`com.leanote.android`、`minSdk 34`、`targetSdk 36`、`compileSdk 37`；debug APK 由 Android Debug 证书签名。

## 模拟器实测

- API 34 / Android 14 Google APIs x86_64：先卸载应用再安装 debug APK，`LaunchState: COLD`，最终进入旧实现的 `SignInActivity`，无 `AndroidRuntime` 崩溃。
- API 36 / Android 16 Google APIs x86_64：从空白 AVD 安装 debug APK，冷启动进入 `SignInActivity`，无 `AndroidRuntime` 崩溃。
- 两个 AVD 均由调试 root 启动未导出的 `PlatformSmokeActivity`；Hilt Activity 和 Compose 首屏成功渲染，点击“运行检查”后 StateFlow 状态从 0 更新为 1，返回后恢复 `SignInActivity`。
- 两个 AVD 均成功打开系统 `android.provider.action.PICK_IMAGES` 图片选择器并返回 `SignInActivity`。这验证平台入口和返回路径，不替代后续编辑器任务对选取文件复制、正文插入和失败清理的端到端测试。

## 未关闭证据

- 没有受保护发布密钥，尚未验证首发 release APK 的生产签名；签名配置只有在 keystore 与 `KEY_ALIAS`、`KEY_PWD`、`KEYSTORE_PWD` 同时存在时才启用，缺失任一项时 release 构建会明确失败而不会产生未签名产物。
- `ApiProvider` 中既有的全信任证书与恒真主机名校验仍然存在。它不是平台构建迁移引入的兼容项，但在网络安全任务移除并完成 `https://me.xiqi.site/` 回归前，不能宣称发布安全边界已经关闭。
- API 34/36 的既有模拟器记录只证明安装、冷启动、调试 Compose/Hilt 入口和系统 Picker 打开/返回；尚未在真实内容导入中验证跨配置重建、正文插入、关系删除与上传，也未完成 API 35+ 手势/三键导航下的系统栏与 IME、API 36 大屏旋转/调整尺寸和真正的 predictive-back 手势证据。
- debug APK 已通过 16 KB ZIP 对齐检查；最终包中未发现 Bugly native 库，仅保留 AndroidX libandroidx.graphics.path.so。对四个 ABI 的 ELF PT_LOAD 对齐检查均为 0x4000，但仍缺少受保护首发 release APK 与真实 16 KB page-size 运行目标证据。
- 本任务没有执行 Room、同步、Leanote API、WebView/编辑器架构或业务页面迁移，也没有使用真实服务测试账号。

## 2026-09-09 审计复核

- `java -version`：当前 shell 的 PATH 指向 Microsoft JDK 21；任务所有者已确认 JDK 21 为目标基线。
- `gradlew.bat -version`：Gradle 9.6.0 的 Launcher/Daemon 实际使用 `D:\Scoop\apps\temurin8-jdk\current`。
- 在该环境运行 `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-configuration-cache`，Gradle 在配置阶段明确失败：`Gradle requires JVM 17 or later to run`。
- 临时切换到 JDK 21 后重试，因仓库没有 `local.properties` 且未设置 Android SDK 路径而明确失败：`SDK location not found`。本次没有修改 `local.properties`，也没有伪造 SDK 证据。
- `.travis.yml:2-10` 仍声明 `oraclejdk8`、`android-26` 和 `build-tools-28.0.3`；它与本矩阵不兼容，已确认不再作为发布入口。GitHub Actions 是唯一目标入口，但当前尚未创建或验证 workflow；`AGENTS.md:9` 也仍描述旧矩阵。正式实现必须同步开发者入口并验证 GitHub Actions。
- 当前 `ApiProvider` 的 trust-all/恒真 hostname verifier 仍在；这是 `09-09-secure-https-service-connections` 的责任，本任务不把它标为已关闭。
- `NotePreviewActivity` 保留用户主动旋转操作；这不等于 Manifest 固定方向，但必须在 API 36 大屏/调整尺寸设备上验证布局。
- `ImageImportViewModel.onCleared` 覆盖配置重建和部分完成竞态；进程终止时不假设调用 `onCleared`，已确认由持久账本、`abandoned` 状态、启动清扫、WorkManager 补偿和关系核对处理；这些实现和进程终止窗口证据仍未验证。

## 证据登记要求

后续每次更新验证必须记录：提交、命令、Wrapper/AGP、Launcher/Daemon JVM、`JAVA_HOME`、SDK 目录与 platform/build-tools、设备 API/导航模式/窗口尺寸、构建变体、产物路径与校验值。缺少签名、设备、16 KB page-size 或真实服务环境时，逐项标记 `blocked`，不以静态扫描或历史日志替代。

## EVID-01 至 EVID-06 闭合登记

| 需求 | 必须登记的证据 | 当前状态 |
|---|---|---|
| EVID-01 GitHub Actions 工作流 | workflow 路径/触发条件、runner、JDK 21/SDK 37、PR 与 production 权限边界、APK 产物和 SHA-256 | `blocked`：当前尚未创建或验证 workflow |
| EVID-02 Photo Picker 账本与清扫 | 状态机、账本位置、启动/WorkManager 清扫、关系核对、四个进程终止窗口测试 | `blocked`：当前实现仍为 ViewModel 生命周期清理 |
| EVID-03 签名迁移 | 仓库/Travis 无 keystore 输入、Environment secrets、临时文件清理、缺失 fail-closed、签名指纹 | `blocked`：当前仍跟踪 `.enc`，无受保护 release |
| EVID-04 JDK 21 / SDK 37 构建 | Launcher/Daemon JVM、`JAVA_HOME`、SDK/Build Tools、测试、lint、两次 debug 构建和 cache 命中 | `blocked`：当前 runner 仍选择 JDK 8 且缺 SDK 路径 |
| EVID-05 真实设备行为 | API 34、API 35+ 两种导航、API 36 大屏/调整尺寸、Photo Picker、predictive-back 的操作记录 | `blocked`：只有历史 API 34/36 smoke 记录 |
| EVID-06 APK 与 16 KB | 最终签名 APK、Manifest/依赖/动态库清单、ZIP 对齐、ELF 对齐、真实 16 KB 运行结果 | `blocked`：只有历史 debug 包检查 |

## 2026-09-13 实现进展

- 已新增 `.github/workflows/android.yml`：PR 路径不读取生产 secrets，并在 JDK 21、SDK 37、Build Tools 36.0.0 上执行测试、lint 和两次 debug 构建；tag/人工触发的 release job 绑定受保护 `production` Environment，只发布签名 APK 与 SHA-256。第三方 actions 使用完整 commit SHA 固定。
- 已删除 `.travis.yml` 和受跟踪的 `leanote-android-new.jks.enc`。Gradle 只接受 runner 临时路径 `RELEASE_KEYSTORE_PATH` 以及 `KEY_ALIAS`、`KEY_PWD`、`KEYSTORE_PWD`；缺少任一输入仍由 `verifyReleaseSigning` fail closed。
- 已实现 Photo Picker 持久账本：复制前记录 `pending`，完成后原子写入 `succeeded`，调用方完成关系与正文插入后记为 `acknowledged`，失败/放弃分别记录 `failed`/`abandoned`。进程 session 将当前导入与旧进程残留分离，启动清扫及每日 WorkManager 补偿在删除前查询 `NoteFile` 关系 owner，且删除仍受 managed-path 规范化检查保护。
- TDD RED 尝试命令 `\.\gradlew.bat testDebugUnitTest --tests org.houxg.leamonax.service.SelectedImageLedgerTest --no-configuration-cache` 在测试编译前被当前环境的 `SDK location not found` 阻塞，未获得可归因于新断言的 Gradle RED。随后使用 JDK 21、JUnit 4.13.2 和 Android API stub 对 Picker 核心执行独立编译；首次编译因测试 lambda 的 checked `IOException` 失败，修正测试并补充失败删除补偿回归后 GREEN：`SelectedImageStoreTest`、`SelectedImageLedgerTest`、`ImageImportResultHandlerTest` 共 20 个测试通过。
- `git diff --check` 通过（只有工作树 CRLF 转换提示）。GitHub workflow 尚未在 GitHub runner 运行；本机隔离 SDK 安装尝试因新版 Android CLI 不接受旧 `sdkmanager` 包名而未能补齐 platform 37，因此 EVID-01/EVID-04 保持 `blocked`，不得将 workflow 静态存在等同于 runner 证据。
- EVID-03 仍缺受保护 Environment 中的真实签名构建与签名指纹；EVID-05 仍缺 API 34/35+/36 目标上的当前设备操作；EVID-06 仍缺最终签名 APK 与真实 16 KB page-size 运行。任务保持 `in_progress`。

## 2026-09-13 独立复核与本地重放

- Windows 11 本地 runner 使用 Microsoft OpenJDK 21.0.12.1 与 Gradle 9.6.0；`gradlew --version` 的 Launcher JVM 与 Daemon JVM 均指向 `D:\Scoop\apps\microsoft21-jdk\current`。
- `ANDROID_HOME`/`ANDROID_SDK_ROOT` 指向系统临时目录 `leanote-android-sdk`；已安装 `platforms/android-37.0`、Build Tools 36.0.0/37.0.0 和 platform-tools 37.0.1。当前 Android CLI 采用斜杠包名，workflow 已据此使用 `platforms/android-37.0` 与 `build-tools/36.0.0`。
- `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-configuration-cache`：成功，32 个 JVM 测试为 0 failures / 0 errors，`lintDebug` 与 debug APK 构建通过。
- 随后两次 `.\gradlew.bat assembleDebug --configuration-cache`：第一次 stored，第二次 reused。debug APK SHA-256 为 `CFB2B2FA7E27D6851FDD32718D389A6F64843C695261D2934A9534E0A100065B`。
- 无签名输入执行 `.\gradlew.bat assembleRelease --no-configuration-cache`：按预期在 `:app:verifyReleaseSigning` 失败，错误明确列出 `RELEASE_KEYSTORE_PATH`、`KEY_ALIAS`、`KEY_PWD`、`KEYSTORE_PWD`，且 `app-release.apk` 不存在。
- 独立 JDK 21/JUnit 运行 Picker 核心与 handler 测试 20/20 通过；复核补充覆盖了当前进程中失败删除由 WorkManager 候选重试的路径。
- 上述只构成本地自动化证据。GitHub-hosted runner、受保护 `production` Environment 的真实签名、API 34/35+/36 当前设备、Photo Picker 进程终止 E2E、最终签名 APK 和真实 16 KB page-size 运行仍保持 `blocked`。
