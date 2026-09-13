# Android 平台基线规格审核

审核日期：2026-09-09。审核对象：`.trellis/tasks/09-09-android-platform-baseline`。

## 选叶与激活依据

- 父任务为 `09-09-android-modernization-requirements`，六个子任务均为 P2。
- `09-09-android-platform-baseline/task.json` 的 `meta.depends_on` 为 `[]`，是该轨道唯一不依赖其他现代化子任务的 ready 叶。
- `09-09-secure-https-service-connections`、API、Room、Compose 和同步子任务都直接或间接依赖平台基线。
- 本会话的 `task.py current --source` 已指向该任务，`task.json.status` 为 `in_progress`；本轮只进行规格审核，不再次执行 `start`，也不创建任务。
- 任务所有者已确认：首发只发布 APK，GitHub Actions 是唯一发布入口，JDK 21 是仓库与 CI 基线。
- 仓库没有显式 `track` 字段；本次“按轨道优先”采用父子任务图、`priority` 和 `meta.depends_on` 的可执行解释。独立的 `00-bootstrap-guidelines` 不属于该现代化轨道，未参与叶选择。

## 证据盘点

| 主题 | 当前证据 | 状态与限制 |
|---|---|---|
| SDK/应用身份 | `app/build.gradle:24-35` 为 `compileSdk 37`、`minSdk 34`、`targetSdk 36`、`applicationId com.leanote.android`；`settings.gradle:9-10` 只有 `:app` | `confirmed-current`（配置事实） |
| 工具链 | `build.gradle:1-5` 为 AGP 9.4.0、Kotlin/Compose 2.3.21、KSP 2.3.11、Hilt 2.60.1；Wrapper 为 Gradle 9.6.0 | `confirmed-current`（版本声明）；JDK/SDK 可运行性另验 |
| 本机 Gradle | `java -version` 显示 JDK 21，但 `gradlew -version` 的 Launcher/Daemon JVM 为 `D:\Scoop\apps\temurin8-jdk\current`；构建明确失败：Gradle requires JVM 17 or later | `blocked`；必须让 Launcher/Daemon 均使用 JDK 21 |
| 本机 Android SDK | 使用 JDK 21 重试后明确失败：SDK location not found；仓库没有 `local.properties` | `blocked`；无法把本轮命令结果当作构建失败之外的产品证据 |
| 历史构建/设备 | `research/version-matrix.md` 记录过 debug、JVM、lint 与 API 34/36 AVD 验证 | `verified (historical)`；原记录没有完整执行主机/SDK 路径，当前 shell 无法重放 |
| CI 交付 | 已确认 GitHub Actions 为唯一入口；当前未发现已验证的 workflow；`.travis.yml:2-10` 仍声明 JDK 8、API 26、Build Tools 28 | `blocked`；必须实现并验证 JDK 21/SDK 37 的 GitHub Actions，且遗留 Travis 不得发布 |
| 仓库指南 | `AGENTS.md:9` 仍要求 JDK 8、SDK 26、Build Tools 28 | `inconsistent`；后续必须同步开发者入口，避免执行错误矩阵 |
| 系统栏/方向 | 所有可见旧 Activity 继承 `BaseActivity`；Compose smoke 使用 `enableEdgeToEdge`、`safeDrawing`、`imePadding`；Manifest 已移除固定方向，但 `NotePreviewActivity` 仍有用户主动旋转操作 | 静态证据已具备；API 35+ 系统栏/IME、API 36 大屏与 predictive-back 仍 `blocked` |
| Photo Picker | `EditorFragment` 使用 `MediaStore.ACTION_PICK_IMAGES` 和 `image/*`；复制、结果持有和 managed path 保护有 JVM 测试；已确认持久账本、`abandoned`、启动/WorkManager 清扫和关系核对策略 | 策略已确定；账本/清扫实现、进程终止窗口和真实设备失败路径仍 `blocked` |
| 原生库/签名 | debug 包曾记录 ZIP/ELF 检查；首发 release APK 无受保护 keystore；`app/build.gradle:80-93` 有 fail-closed 签名门禁；仓库当前仍跟踪 `leanote-android-new.jks.enc` | release APK、GitHub Environment 注入、迁移清理和真实 16 KB page-size 运行目标仍 `blocked` |
| 网络安全 | `ApiProvider` 仍保留旧 trust-all/TLS 绕过 | 属于 HTTPS 子任务；平台基线不得宣称网络安全已关闭 |

## 规格问题与修正

1. `implement.md` 原将 edge-to-edge/IME、predictive back 写成“关闭”。目标要求是启用/正确处理并验证，现改为“实现并验证”；普通返回键不能替代 predictive-back 证据。
2. 原 PRD 把 NoteFile 关系持久化、服务器文件集合 reconciliation、inline image pruning 和上传 MIME 混入平台任务。这些分别由 Room、API 和 Compose/WebView 边界消费；平台任务只拥有 Picker URI → managed file 的导入 seam、生命周期、MIME 和失败清理协议，避免第二事实来源。
3. 原“构建成功”叙述没有区分历史受控环境与当前可复现环境。规格现在要求记录 Gradle Launcher/Daemon JVM、SDK 目录、Wrapper、AGP 和构建变体；缺任一项只能标记为 `blocked`。
4. 本地构建基线与 `.travis.yml`、`AGENTS.md` 冲突。交付基线必须覆盖 CI 与开发者入口，否则本地成功不能证明可发布。
5. `ViewModel.onCleared` 能处理配置重建和已完成未确认结果，但进程在确认前终止可能留下 managed copy。现已确认用持久导入账本、`abandoned` 状态、应用启动清扫、WorkManager 补偿和关系 owner 核对闭合该边界；进程终止不恢复未确认选择，用户重新选择。
6. 首发格式、CI 入口和 JDK 已由任务所有者确认：只发布 APK，GitHub Actions 唯一入口，JDK 21；签名、16 KB 检查和部署脚本按此固定。
7. 将 workflow、账本/清扫、签名迁移、JDK 21/SDK 37 构建、真实设备和 16 KB 检查整理为 `EVID-01` 至 `EVID-06`，每项都有输入、输出、失败门禁和证据登记要求；方案确认不等于实现验证。

## 已确认决策

- 首发只发布 APK，不构建或部署 AAB。
- GitHub Actions 是唯一受支持的 CI/发布入口；`.travis.yml` 不再承担发布职责。
- JDK 21 是仓库与 CI 的固定 Gradle 运行时基线。

## 已确认实施决策

- 进程终止发生在 Picker 副本完成但关系/正文尚未确认时，视为 `abandoned`；持久账本记录状态，应用启动清扫并由 WorkManager 补偿，清扫前核对关系 owner。未确认选择不恢复，用户重新选择；配置重建保留 pending。
- `leanote-android-new.jks.enc` 不再作为仓库签名输入；GitHub Actions `production` Environment 提供 Base64 keystore 和签名 secrets，keystore 只在 runner 临时目录存在并在结束时清理。签名私钥保持稳定，执行访问审计和离线备份，不做破坏 APK 更新能力的随意换钥。

## EVID 需求整理

- `EVID-01`：唯一 GitHub Actions workflow、PR/production 权限分流、JDK 21/SDK 37、APK 发布和 SHA-256 登记。
- `EVID-02`：Photo Picker 持久账本、状态机、启动/WorkManager 清扫、关系核对和进程终止窗口测试。
- `EVID-03`：移除仓库/Travis keystore 输入、GitHub Environment secrets、临时文件清理、缺失 fail-closed 和稳定签名指纹。
- `EVID-04`：JDK 21/SDK 37 环境登记、JVM 测试、lint、两次 debug 构建和 configuration cache。
- `EVID-05`：API 34、API 35+ 导航模式、API 36 大屏及真实 predictive-back/Picker 操作证据。
- `EVID-06`：最终签名 APK 的 Manifest/依赖/动态库、ZIP/ELF 16 KB 和真实 16 KB page-size 运行证据。

## 审核结论

平台目标、边界、实施策略和下游依赖已无产品决策悬项，`EVID-01` 至 `EVID-06` 已整理为可直接执行的 Trellis 需求，可以按修订后的 PRD/设计/实施材料指导后续开发；但当前仍处于“规格审核完成、实现证据未闭合”状态。GitHub Actions workflow、账本/清扫实现、签名迁移、设备和 16 KB 证据不能用历史 debug 记录、静态扫描或普通返回键替代。
