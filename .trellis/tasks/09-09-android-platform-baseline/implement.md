# Android 平台与交付基线实施计划

本计划只能在本任务规格审核完成、待确认事项有结论或明确延期、且每一步证据可追溯后执行。历史提交中的实现不自动勾选本计划；`[x]` 表示已有可引用证据或任务所有者已明确确认，不表示本轮重新验证。

## 0. 规格与环境门禁

- [x] 确认本任务是现代化轨道唯一 `meta.depends_on: []` 的 ready 叶，并已由当前会话激活；不创建新任务或重复 `start`。
- [x] 完成 PRD/设计/研究审计，明确平台、Photo Picker seam 与 Room/API/Compose/HTTPS 下游的所有权边界。
- [x] 已确认首发只发布 APK，GitHub Actions 是唯一受支持 CI/发布入口，JDK 21 是仓库与 CI 的 Gradle 运行时基线；同时采用持久账本/启动清扫的 `abandoned` 策略，以及不在仓库保存 keystore、由 GitHub Actions `production` Environment 注入的签名策略。
- [ ] 在目标 GitHub Actions runner 记录 `java -version`、`gradlew -version` 的 Launcher/Daemon JVM、`JAVA_HOME`、Android SDK 目录、platform 37、Build Tools、Wrapper、AGP 和依赖解析结果，确认 Launcher/Daemon 均为 JDK 21。
- [x] 同步 `AGENTS.md`、移除 `.travis.yml` 并建立发布文档，使开发者入口与 JDK 21/SDK 37 基线一致；GitHub Actions `production` Environment、secret 注入和 APK 发布流程是唯一入口。workflow 的实际运行证据仍单独保持未关闭。

## 1. 构建链迁移

- [x] 以官方兼容表和任务所有者决策记录 Gradle 9.6.0、AGP 9.4.0、JDK 21、Kotlin/Compose 2.3.21、KSP 2.3.11、Compose BOM 2026.08.00 和 Hilt 2.60.1 的选择理由。
- [x] 按 Wrapper/JDK/AGP → AndroidX → Kotlin/Compose → ViewModel/Coroutines/Flow → Hilt 的顺序整理单一 `:app` 构建矩阵；删除失效 Support/JCenter/动态版本来源。
- [ ] 在 JDK 21、SDK 37 runner 上逐阶段构建，确认没有通过降低 SDK、旧 Support Library 或 source-level fallback 掩盖失败。
- [ ] 为 ButterKnife、DBFlow、Jetifier 和 `jdk.compiler` exports 记录退出条件；后续移除前不得把临时开关复制到新模块。

## 2. 应用身份与发布门禁

- [x] 保留 namespace `org.houxg.leamonax`、application ID `com.leanote.android` 和单一 `:app` 模块。
- [x] 取消固定方向、应用内 APK 安装权限和 Bugly upgrade activity；保留 Java crash reporter，关闭 native monitor 并排除 Bugly native 库。
- [ ] 对实际首发 APK 运行 merged Manifest、依赖和动态库扫描；确认没有 `REQUEST_INSTALL_PACKAGES`、方向锁、动态依赖、HTTP 仓库或未解释的原生库。
- [ ] 在缺少 keystore secret、`KEY_ALIAS`、`KEY_PWD`、`KEYSTORE_PWD` 的隔离环境运行 release，确认 `verifyReleaseSigning` 在打包前失败且没有产物。
- [ ] 在受保护 GitHub Actions `production` Environment 用已批准的签名材料构建首发 APK，并登记版本号、application ID、签名指纹和产物校验值；任何凭据不得进入日志或任务材料，runner 临时 keystore 必须在结束时删除。

## 3. Compose/Hilt 与系统行为

- [x] 保留最小 Compose 宿主、不可变 UI state、事件入口、ViewModel 和 Hilt wiring smoke；明确调试入口不是产品纵向切片。
- [ ] 在 API 34 与更高可用 API 上完成全新安装、冷启动、基础导航和 smoke 状态事件更新。
- [ ] 在 API 35+ 分别验证手势导航、三键导航、状态栏、导航栏和 IME 不遮挡控件；记录设备、窗口尺寸、操作步骤和结果。
- [ ] 在 API 36 大屏/可调整尺寸目标验证旋转、尺寸变化和用户主动预览旋转，不依赖 Manifest 固定方向。
- [ ] 通过真实 predictive-back 手势验证回调完成与取消；不得用普通返回键、菜单点击或静态代码扫描代替。

## 4. Photo Picker 平台 seam

- [x] 入口使用 `MediaStore.ACTION_PICK_IMAGES` 和 `image/*`，不请求相机或广泛存储权限。
- [x] JVM 测试覆盖受支持 MIME、未知 MIME、空 stream、部分复制、配置重建结果持有、外部路径保护、关系/正文消费失败和回滚失败的清理信号。
- [ ] 在真实 API 34+ 设备验证用户取消、URI/MIME 异常、后台复制、配置重建、结果确认一次和 View 销毁行为。
- [x] 实现进程终止后未确认副本的持久账本、`abandoned` 状态、应用启动清扫、WorkManager 补偿和关系 owner 核对；未确认选择不恢复，用户重新选择，配置重建不触发清理。
- [x] 为复制前、复制中、复制完成未确认、关系提交未确认四个进程终止窗口增加 JVM 回归测试；独立 JDK 21 编译运行的 20 个 Picker 核心/handler 测试通过。Android Gradle runner 与真实设备重放仍单独保持未关闭。
- [ ] 将关系持久化、正文插入、Room 事务、服务器文件集合 reconciliation 和上传 MIME 验收交给对应下游任务，不在本任务形成第二套契约。

## 5. 自动化验证与证据登记

- [x] 历史受控环境曾运行 `testDebugUnitTest`、`lintDebug`、`assembleDebug` 并记录 API 34/36 AVD smoke；证据注明执行环境未知且不可替代当前 runner。
- [x] 当前 Windows 本地 runner 已使用 JDK 21、SDK platform 37 与 Build Tools 36.0.0 完成 32 个 JVM 测试、`lintDebug`、debug 构建及 configuration-cache stored/reused；无签名 release 在 `verifyReleaseSigning` 失败且没有 release APK。该结果不替代下列 GitHub runner 与受保护签名门禁。
- [ ] 在 JDK 21、SDK 37 GitHub Actions runner 运行：
  - `.\gradlew.bat testDebugUnitTest --no-configuration-cache`
  - `.\gradlew.bat lintDebug --no-configuration-cache`
  - `.\gradlew.bat assembleDebug --no-configuration-cache`
  - 再次 `.\gradlew.bat assembleDebug` 并记录 configuration cache reuse
- [ ] 运行 `.\gradlew.bat assembleRelease` 的无签名 fail-closed 检查，以及受保护 GitHub Actions runner 的实际签名 APK 构建。
- [ ] 运行 `git diff --check`、依赖/Manifest/凭据扫描；扫描结果按误报和生产命中逐项解释，不全局抑制 lint。
- [ ] 验证 GitHub Actions 普通 PR 不注入 production secrets，受保护 tag/人工批准环境才可解码临时 keystore；成功和失败路径都清理临时文件且不向日志输出凭据。
- [ ] 对最终 APK 执行 `zipalign -c -P 16 -v 4`，列出每个 `.so` 并检查 ELF `PT_LOAD` 对齐；在真实 16 KB page-size 目标运行验证。
- [ ] 将每项结果记录为 `confirmed-current`、`verified`、`target-required` 或 `blocked`，包含命令、runner、设备、产物和未运行原因。

## 6. EVID 需求执行映射

- [ ] `EVID-01`：提交唯一 GitHub Actions workflow，完成 PR 无 secrets 检查、受保护 release APK 发布、Travis 禁止发布和 APK SHA-256 登记。
- [ ] `EVID-02`：提交 `SelectedImageStore` 持久账本/状态机/启动清扫/WorkManager 补偿，完成四个进程终止窗口、关系核对、配置重建和幂等性测试。
- [ ] `EVID-03`：移除仓库和 Travis 的加密 keystore 输入，接入 GitHub `production` Environment，完成临时文件清理、缺失 fail-closed、APK 签名指纹和访问控制验证。
- [ ] `EVID-04`：在 JDK 21/SDK 37 runner 登记完整环境矩阵，完成测试、lint、两次 debug 构建和 configuration cache 验证。
- [ ] `EVID-05`：在 API 34、API 35+ 导航模式和 API 36 大屏目标完成安装、系统栏/IME、窗口调整、Photo Picker 和 predictive-back 真实操作记录。
- [ ] `EVID-06`：对最终签名 APK 完成 Manifest/依赖/动态库扫描、ZIP/ELF 16 KB 检查和真实 16 KB page-size 运行验证。

## 7. 回滚点

- [ ] Wrapper/AGP/JDK/AndroidX、Kotlin/Compose、ViewModel/Coroutines/Flow、Hilt、发布门禁和 Picker seam 各形成可审查提交或等价回滚点。
- [ ] 任一矩阵阶段失败时整体恢复相互依赖版本；不得只降 SDK、恢复旧 Support Library 或并存两套插件。
- [ ] 回滚不能恢复固定方向、明文/全信任网络、无签名 release 或删除外部路径的文件逻辑；依赖本任务的子任务必须重新验证。
