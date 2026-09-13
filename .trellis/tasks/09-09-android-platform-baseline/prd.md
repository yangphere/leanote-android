# Android 平台与交付基线

## 审核结论与任务边界

- 选中的 ready 叶是 `09-09-android-platform-baseline`：在 `09-09-android-modernization-requirements` 轨道中，它是唯一 `meta.depends_on: []` 的子任务。
- `task.py current --source` 已指向本任务，状态为 `in_progress`。本轮是激活后的规格审核，只允许修改本任务的规格、研究和验收材料，不修改 Java/Kotlin、Gradle、Manifest、资源或其他产品实现。
- 本任务只交付 Android 14+ 的构建、运行时和发布前置基线，以及供后续任务消费的 Photo Picker 导入 seam；不交付 Room、同步、Leanote API、TLS、业务页面或 WebView bridge 迁移。
- 证据状态统一使用：`confirmed-current`（当前代码/配置直接证明）、`verified`（自动化或真实环境证明）、`target-required`（目标要求）、`blocked`（缺少环境或受保护证据）、`superseded`（被后续 ADR 取代）。没有证据不得从 `target-required` 推断为 `verified`。

## 目标与用户可观察结果

首个公开 Android 客户端以 Android 14（API 34）为最低平台，使用明确、可复现且可安全发布的现代 Android 构建链，同时保留 `com.leanote.android` 作为应用身份。用户应能在受支持设备上全新安装、启动并完成基础导航；发布系统在缺少签名或发现不兼容原生库时必须失败并说明原因，不得生成含糊的产物。

## 来源与当前证据

- 需求来源：`docs/adr/0001-incremental-native-android-modernization.md`、`docs/adr/0002-android-14-minimum-platform-baseline.md`、`docs/adr/0007-greenfield-client-without-legacy-upgrade.md`。
- `707e8fa` 是迁移前快照：当时 `app/build.gradle` 为 API 26/19/26，仍使用旧 Support Library 和 DBFlow；它不是当前状态。
- `4556705` 是已存在的基线实现快照。当前 `app/build.gradle:24-35` 为 `compileSdk 37`、`minSdk 34`、`targetSdk 36`、`applicationId com.leanote.android`；`settings.gradle:9-10` 仍只有 `:app`。
- 当前构建环境的 PATH 指向 JDK 21，但 `gradlew -version` 仍选择 JDK 8；在 JDK 21 重试又因没有 Android SDK 目录失败。因此历史 `research/version-matrix.md` 中的成功记录必须标为历史受控环境证据，不能替代当前可复现结果。
- 规格审计时 `.travis.yml:2-10` 和 `AGENTS.md:9` 仍写着 JDK 8、API 26、Build Tools 28；当前实现已删除 Travis 配置并同步开发者入口和发布文档。GitHub Actions workflow 的真实 runner 运行证据仍未闭合。
- 完整证据、命令输出和未关闭项见 `research/spec-audit-2026-09-09.md` 与 `research/version-matrix.md`。

## 需求

### PLAT-01 构建矩阵

- 保持原生 Android 路线；目标栈为 Kotlin、Compose、分层数据、单向数据流、ViewModel、Coroutines/Flow 和 Hilt，但本任务只提供可运行的基础设施冒烟，不宣称完成业务迁移。
- 固定首轮平台边界：`minSdk 34`、`targetSdk 36`、`compileSdk 37`。Gradle Wrapper、AGP、JDK、Kotlin/Compose Compiler、KSP、Compose BOM、Hilt 和关键 AndroidX 版本必须明确、可解析且各只有一个事实来源。
- Gradle 必须在 JDK 21 运行；验证必须同时记录 Launcher JVM、Daemon JVM、`JAVA_HOME`、Wrapper、AGP 和 Android SDK 目录，且 Launcher/Daemon 均为 JDK 21。不得通过降低 SDK、恢复旧 Support Library 或增加 source-level fallback 绕过失败。
- 应用依赖只允许 `google()`、`mavenCentral()` 和明确的 HTTPS JitPack；禁止 JCenter、HTTP 仓库、动态版本和未经解释的镜像。Gradle Plugin Portal 只用于插件解析。
- 在没有明确构建、复用或协作证据前保持单一 `:app` 模块；Java/Kotlin 可在迁移期共存。

### PLAT-02 应用身份与发布安全

- 保持 `namespace org.houxg.leamonax` 与 `applicationId com.leanote.android`；首发只承诺全新安装，不承诺未部署旧 APK 的覆盖升级、历史签名连续性或 DBFlow 数据迁移。
- 发布密钥、口令和明文 keystore 路径不得写入仓库、日志、测试产物或命令行。GitHub Actions 的受保护 `production` Environment 注入 Base64 keystore 和 `KEY_ALIAS`、`KEY_PWD`、`KEYSTORE_PWD`，workflow 只在 runner 临时目录解码并在结束时清理；仓库不得继续版本化 `leanote-android-new.jks.enc`。缺少任一项时，release 必须在打包前失败，不得回退 debug 签名或产出未签名文件。签名私钥保持长期稳定，定期审计访问和离线备份，不进行会破坏 APK 更新能力的随意换钥。
- `BUGLY_PRD` 只从受保护环境注入；缺失时为空值。应用内 APK 安装不是当前渠道能力，合并 Manifest 不得包含 `REQUEST_INSTALL_PACKAGES` 或 Bugly upgrade activity。
- 首发发布产物确定为 APK；AAB 不属于本任务的发布范围。APK 必须纳入签名、16 KB 和部署验收，GitHub Actions 不得额外发布未批准的格式。

### PLAT-03 纵向基础设施冒烟

- 建立最小 Compose 宿主、不可变 UI state、事件入口、ViewModel 和 Hilt 注入路径；调试入口可以通过受控方式启动，但不成为用户可达的产品页面，也不复制全局账户、DBFlow 或业务服务逻辑。
- 旧 Activity/Fragment/WebView 与新基础设施必须能共同构建和运行；该冒烟只证明框架 wiring，不证明 Room、同步、API 或编辑器契约。

### PLAT-04 系统栏、IME、方向与返回

- 所有可见的 View Activity 使用统一的 edge-to-edge/insets owner；Compose 宿主使用同一策略的 `safeDrawing` 与 IME insets。页面不得重复加全屏 padding，必须在 API 35+ 系统栏、IME、手势和三键导航下保持控件可操作。
- Manifest 不锁定固定方向。API 36 大屏/可调整尺寸是布局不变量；现有用户主动旋转预览操作若保留，不能被解释为发布兼容依赖，且必须验证旋转和调整尺寸后的布局。
- predictive back 必须按真实手势验证，包括回调取消/完成行为；普通返回键、菜单点击或静态代码扫描不能替代该证据。

### PLAT-05 原生库与包完整性

- 发布依赖优先使用纯 Java/Kotlin。每个残留 `.so` 都要在最终 APK 中列出，并提供 ZIP 对齐、ELF `PT_LOAD` 对齐和真实 16 KB page-size 运行目标证据；ZIP 对齐不能单独关闭该要求。
- Bugly 仅保留 Java crash reporter，关闭 native monitor，并从包中排除 `libBugly_Native.so`。不得用 lint 全局关闭掩盖真实不兼容库。

### PLAT-06 Photo Picker 导入 seam

- 通过系统 `MediaStore.ACTION_PICK_IMAGES` 请求 `image/*`；不为该流程请求相机或广泛外部存储权限。用户取消选择是无操作，不得误报失败。
- 对返回 URI 解析非空 MIME 和可读 `ContentResolver` stream；未知/不支持 MIME、空 stream、复制失败都必须向调用方返回明确错误且不保留部分文件。
- 在后台把内容复制到应用受管的 `filesDir/selected-images`，以可跨配置重建的生命周期 owner 持有运行任务和未确认结果；结果只能被有效 View 消费并确认一次。
- 导入 seam 输出受管文件句柄和显式 `pending/succeeded/failed/acknowledged/abandoned` 状态。关系持久化、正文插入、服务器 reconciliation、附件上传 MIME 和 Room 事务由下游任务拥有；平台 seam 必须提供可幂等的成功确认与失败清理回调。
- 任何清理只允许作用于 `SelectedImageStore` 的规范化受管目录。关系或正文消费失败时必须删除受管副本并保留回滚错误。导入前写入持久账本，复制完成后原子记录 `succeeded`；进程终止时未确认记录按 `abandoned` 处理，由应用启动清扫并由 WorkManager 补偿，清扫前必须向关系 owner 核对，已建立关系的文件不得删除。进程终止不提供未确认选择的恢复 UI，用户重新选择；配置重建仍必须保留 pending 导入。

### PLAT-07 交付入口与一致性

- 唯一受支持的 CI/发布入口是 GitHub Actions；其 runner 必须提供 JDK 21、Android SDK platform 37 和所需 Build Tools，执行与本地相同的构建、测试、lint、APK 签名和包完整性检查。
- `.travis.yml` 仅作为遗留配置保留时不得触发发布；`AGENTS.md` 和 GitHub Actions 发布文档不得继续把 API 26/JDK 8/Build Tools 28 表述为当前基线，且必须明确 GitHub Actions 是唯一入口。
- 流水线必须保留受保护签名注入、敏感输出脱敏、失败不部署和实际发布产物登记；不得因历史加密 keystore 文件存在而默认 release 已可验证。

## 实现与验证证据闭合需求

以下需求是后续实现和验收的强制门禁。`confirmed` 只表示方案已由任务所有者确认；只有完成对应实现、命令和真实环境验证后，才能标为 `verified`。

### EVID-01 GitHub Actions 工作流

- 在 `.github/workflows/` 建立唯一受支持的 Android 构建/发布 workflow；`.travis.yml` 不得触发构建或发布。
- Pull Request 只运行不读取生产 secrets 的 `testDebugUnitTest`、`lintDebug` 和 `assembleDebug`；受保护 tag 或人工批准的 `production` Environment 才能构建并发布首发 APK。
- workflow 必须固定 JDK 21、Android SDK platform 37、所需 Build Tools 和 Gradle Wrapper，记录 runner、版本和依赖解析结果；不得用旧 API 26/JDK 8 路径或未固定镜像替代。
- release 仅上传已签名的 `app-release.apk` 及 SHA-256；缺少签名材料、构建失败、包检查失败或证据登记不完整时不得发布。

### EVID-02 Photo Picker 账本与清扫

- `SelectedImageStore` 必须在复制前写入持久导入账本，至少记录导入 ID、受管路径、MIME、创建时间和状态；状态转换为 `pending → succeeded → acknowledged`，失败和进程终止分别进入 `failed`/`abandoned`。
- 配置重建保留 `pending`；进程终止不恢复未确认选择。应用启动立即清扫，WorkManager 提供补偿；清扫前向关系 owner 核对，已有关系的文件只能补记确认，无关系的受管副本才可删除。
- 账本写入、状态转换、确认、回滚和清扫必须幂等，且任何删除都要经过规范化 managed-path 所有权检查；不得删除外部路径。
- 必须覆盖复制前、复制中、复制完成未确认、关系提交未确认四个进程终止窗口，以及配置重建、重复确认和重复清扫。

### EVID-03 签名迁移

- `leanote-android-new.jks.enc` 不得作为仓库签名输入；迁移后当前树不得跟踪该文件，Travis 解密/发布步骤不得再使用它。
- GitHub Actions `production` Environment 提供 Base64 keystore、`KEY_ALIAS`、`KEY_PWD`、`KEYSTORE_PWD`；workflow 只在 runner 临时目录解码，通过受控路径交给 Gradle，成功和失败路径都清理临时文件。
- 普通 PR 不得读取生产 secrets；缺少任一签名输入时，`verifyReleaseSigning` 必须在 APK 打包前失败，不得回退 debug 签名或生成未签名 APK。
- 签名私钥保持稳定；只做访问审计和离线备份，不做会破坏后续 APK 更新能力的随意换钥。发布必须记录 APK 签名指纹和 SHA-256，不记录秘密值。

### EVID-04 JDK 21 / SDK 37 构建证据

- 在目标 GitHub Actions runner 使用 JDK 21 和 SDK platform 37，记录 `java -version`、`gradlew -version` 的 Launcher/Daemon JVM、`JAVA_HOME`、SDK 目录、Build Tools、Wrapper、AGP、构建变体和依赖解析结果。
- 依次通过 `testDebugUnitTest`、`lintDebug`、`assembleDebug`，再执行一次 `assembleDebug` 并证明 configuration cache 被复用；lint 不得以全局关闭掩盖错误。
- 缺 JDK 21、SDK 37、固定依赖或构建工具时必须明确失败并保持 `blocked`，不得降低 SDK、切回旧 Support Library 或增加 source-level fallback。

### EVID-05 真实设备行为证据

- 在 API 34 和至少一个更高版本完成 APK 全新安装、冷启动、基础导航、Compose/Hilt smoke 和 Photo Picker 取消/返回。
- 在 API 35+ 分别验证手势导航、三键导航、状态栏、导航栏和 IME 不遮挡控件；在 API 36 大屏/可调整尺寸目标验证旋转、尺寸变化和预览页主动旋转。
- 通过真实 predictive-back 手势验证完成和取消回调；普通返回键、菜单点击、静态扫描或历史截图不能替代。
- 每次记录设备 API、镜像/ABI、导航模式、窗口尺寸、构建变体、操作步骤、结果和产物 SHA-256；没有目标设备时逐项保持 `blocked`。

### EVID-06 APK 与 16 KB 完整性证据

- 对最终签名 APK 运行 merged Manifest、依赖和动态库扫描；确认 application ID、SDK 边界、无安装权限、无 Bugly upgrade activity、无动态/HTTP 依赖和无未解释的原生库。
- 运行 `zipalign -c -P 16 -v 4`；列出每个 `.so` 并检查 ELF `PT_LOAD` 对齐为 16 KB。ZIP 对齐通过不能替代 ELF 证据。
- 在真实 16 KB page-size 运行目标安装并启动 APK，验证所有残留原生库可加载；没有 release APK 或真实 16 KB 目标时不得标为 `verified`。

## 输入、输出与状态契约

| 场景 | 输入 | 输出/状态 | 失败处理 |
|---|---|---|---|
| 构建 | Wrapper、JDK 21、SDK 37、固定依赖 | debug/release 候选及可追溯元数据 | 缺 SDK/JDK/依赖立即失败，不降低矩阵 |
| 发布 | APK、keystore、四项环境输入（含可选 `BUGLY_PRD`） | 已签名且包完整性通过的 APK | 缺签名或库证据不得发布 |
| Compose/Hilt 冒烟 | 调试启动入口 | UI state、事件更新、返回后的可观察状态 | wiring 失败明确暴露，不用产品页面假成功 |
| Photo Picker | 用户选择的 URI、MIME、可读 stream | managed file + `pending` 结果，调用方确认后 `acknowledged` | 取消无操作；URI/MIME/复制/消费失败清理并返回错误 |
| 生命周期销毁 | running 或 pending 导入、持久账本和关系核对结果 | 配置重建保留 pending；进程终止转为 `abandoned` 并清理受管副本 | 清扫只作用于规范目录；关系已存在时补记确认，不删除文件 |
| GitHub Actions 发布 | JDK 21、SDK 37、production secrets、Gradle Wrapper | 已签名 `app-release.apk`、签名指纹、SHA-256 和完整证据登记 | PR 不读生产 secrets；缺输入、失败或证据不全则不发布 |
| 设备与包验证 | 首发 APK、API 34/35+/36 设备、16 KB page-size 目标 | 安装/运行/窗口行为、predictive back、Photo Picker 和 16 KB 验证记录 | 无目标设备、release APK 或 16 KB 目标时保持 `blocked` |

## 兼容性、依赖和范围边界

- 平台兼容只按 API level 和 Android 行为变更判断，不按 HyperOS、MIUI 或其他厂商名称分支。
- 后续 `09-09-secure-https-service-connections` 负责移除 `ApiProvider` 的 trust-all/TLS 绕过；本任务可以在该缺口开放时完成构建基线，但不得宣称网络安全已闭合。
- `09-09-leanote-api-content-compatibility` 定义 REST、USN、Markdown/HTML 和附件传输语义；`09-09-room-data-foundation` 定义实体、关系和 FTS；`09-09-compose-webview-editor-boundary` 消费 Compose 与编辑器交接；`09-09-account-bound-reliable-sync` 最后消费全部边界。
- 不在本任务重新定义服务地址、数据模型、同步调度、WebView bridge 或产品纵向切片，也不因平台导入 seam 复制这些规则。

## 验收条件

- [x] `confirmed-current`：当前配置声明 `minSdk 34`、`targetSdk 36`、`compileSdk 37`、应用 ID `com.leanote.android`，且 Gradle settings 只有 `:app`。
- [x] `confirmed-current`：依赖版本为固定值，仓库未恢复 JCenter/HTTP；Manifest 静态扫描无固定方向、`REQUEST_INSTALL_PACKAGES` 和 Bugly upgrade activity。
- [x] `verified (historical)`：受控历史环境曾完成 debug/JVM/lint 构建，并在 API 34/36 AVD 完成安装、冷启动、Compose/Hilt smoke 和 Picker 打开/返回；该记录不替代当前环境重放。
- [x] `verified (local runner)`：在 JDK 21、SDK 37 与 Build Tools 36.0.0 可定位的 Windows runner 上重放 `testDebugUnitTest`、`lintDebug`、`assembleDebug`，32 个 JVM 测试、lint 和 debug 构建通过，随后两次 configuration-cache 构建分别 stored/reused；GitHub runner 证据仍由 EVID-04 单独门禁。
- [ ] `blocked`：首发 APK 在 API 34 及至少一个更高版本完成全新安装、冷启动、基础导航、系统栏/IME、predictive back、可调整尺寸和大屏证据。
- [ ] `blocked`：受保护 release 签名验证通过；缺少签名材料时 `verifyReleaseSigning` 失败且没有 release 产物。
- [ ] `blocked`：最终发布 APK 的 Manifest、动态库列表、ZIP/ELF 16 KB 检查和真实 16 KB page-size 运行验证完成。
- [ ] `blocked`：Photo Picker 的 MIME/URI/复制、配置重建、调用方确认、消费失败清理，以及持久账本、进程终止 `abandoned`、启动/WorkManager 清扫和关系核对具有自动化或真实设备证据；下游关系/服务器语义不在此项重复验收。
- [ ] `blocked`：GitHub Actions 作为唯一受支持入口使用 JDK 21、SDK 37 和同一版本矩阵，且 `AGENTS.md`/发布文档不再指向旧 API 26/JDK 8 基线；`.travis.yml` 不发布。
- [x] `superseded`：旧 APK 覆盖升级、历史签名连续性和 DBFlow 原地迁移由 ADR 0007 取代，不进入本任务验收。

### 证据闭合验收

- [ ] `EVID-01 blocked`：唯一 GitHub Actions workflow 按 PR/受保护 release 分流，使用 JDK 21/SDK 37，发布 APK 并登记 SHA-256；Travis 不触发发布。
- [ ] `EVID-02 blocked`：持久账本、状态机、启动/WorkManager 清扫、关系核对和四个进程终止窗口测试全部通过。
- [ ] `EVID-03 blocked`：仓库和 Travis 不再使用加密 keystore；GitHub production secrets 临时解码、清理、缺失 fail-closed 和签名指纹登记均有证据。
- [ ] `EVID-04 blocked`：JDK 21/SDK 37 runner 完成 JVM 测试、lint、两次 debug 构建和 configuration cache 复用，并登记完整环境矩阵。
- [ ] `EVID-05 blocked`：API 34/35+/36 真实设备完成安装、导航、系统栏/IME、大屏调整、Photo Picker 和 predictive-back 证据。
- [ ] `EVID-06 blocked`：最终签名 APK 完成 Manifest/依赖/动态库扫描、ZIP/ELF 16 KB 检查和真实 16 KB page-size 运行验证。

## 已确认决策

- 首发只发布 APK，不构建或部署 AAB。
- GitHub Actions 是唯一受支持的 CI/发布入口；`.travis.yml` 不再承担发布职责。
- JDK 21 是仓库和 CI 的固定 Gradle 运行时基线。

## 已确认实施决策及影响

- 进程终止发生在 Picker 副本完成但关系/正文尚未确认时，视为 `abandoned`；持久账本、启动清扫和 WorkManager 补偿负责最终清理，关系 owner 是已提交关系的权威来源。未确认选择不恢复，用户重新选择；配置重建不触发清理。
- `leanote-android-new.jks.enc` 不再作为仓库签名输入；GitHub Actions `production` Environment 提供受保护 secrets，keystore 只在 runner 临时目录短暂存在并在结束时删除。签名私钥保持稳定，访问审计和备份轮换不等于更换签名身份。

上述策略已经确认，但当前环境缺失的设备、SDK、workflow、签名和 16 KB 证据仍保持 `blocked`，不得把策略确认等同于实现完成。

## 范围外

- 数据库、同步、编辑器、WebView bridge、Leanote API/USN/内容和 TLS 业务迁移。
- API 33 及更低版本的现代化应用、旧 APK 覆盖升级、历史 DBFlow 数据迁移和双发布线。
- 在平台任务中改变 `https://me.xiqi.site/` 的服务契约或使用真实测试账号。
