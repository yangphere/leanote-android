# 将 Android 现代化需求迁移到 Trellis

## 目标

将 `docs/adr/0001` 至 `0007` 中仍然有效的 Android 现代化方向转换为可追溯、可验收、可拆分执行的 Trellis 需求规划，同时保留原 ADR 及其被后续决定取代的关系。

## 背景与已确认事实

- `docs/adr/` 的决策文档涉及渐进式原生现代化、Android 14 平台基线、数据与 API 兼容、账户绑定同步、Compose 与 WebView 编辑器边界、有效 HTTPS，以及无旧客户端升级兼容的全新发布边界。
- Trellis 的任务 `prd.md` 承载需求、约束和验收条件；`.trellis/spec/` 承载项目开发规范，不作为本次需求迁移的主目标。
- `.trellis/tasks/00-bootstrap-guidelines` 仅负责记录当前真实编码惯例，本次迁移不复用该任务。
- 原 ADR 保留在 `docs/adr/`，不因迁移被删除；Trellis 需求必须保留到 ADR 的来源映射。
- 本任务处于规划阶段。任务创建与规划不授权修改产品代码或启动现代化实现。
- 当前没有部署给用户的旧版 APK，不存在需要接收现代化版本的 API 19–33 已安装用户，也不需要维护旧版发布线。
- Leanote 服务兼容只以 `https://me.xiqi.site/` 当前部署为准，其他服务端版本不在本路线范围内；测试凭据不得持久化到仓库或任务文档。

## 迁移规划时仓库证据

- 以下事实是 2026-09-09、基线提交 `707e8fa` 的平台迁移前快照，不代表当前工作树。后续任务不得把这些行当成迁移完成后的现状。
- 该快照的 `app/build.gradle` 使用 `compileSdkVersion 26`、`minSdkVersion 19`、`targetSdkVersion 26`；ADR 0002 中的 Android 14、`targetSdk 36`、`compileSdk 37` 当时尚未实现。
- 该快照的应用 ID 为 `com.leanote.android`，签名凭据来自环境变量；首个公开版本仍需建立安全、可复现的发布签名流程，但不承担旧 APK 签名连续性。
- 该快照仍使用 DBFlow 及其版本化迁移；它是实现 Room 新数据基础时需要替换的旧技术，不是需要兼容的已部署数据来源。
- `app/src/main/java/org/houxg/leamonax/network/api/` 与 `app/src/main/java/org/houxg/leamonax/service/NoteService.java:89-160,240-284` 证明现有 REST、USN 分页与冲突副本行为，但尚无完整兼容回归证据。
- 快照中的 `AccountDataStore.java` 与 `NoteSyncService.java` 显示同步可在执行期间读取全局当前账户，且使用旧式 Android Service；ADR 0004 是针对该错配风险的目标约束。
- 快照中的 `Editor.java` 与 `MarkdownEditor.java` 证明编辑内核是 WebView、assets 与 JavaScript bridge，且当时尚无 Compose 外壳、结构化 JSON 协议或受限加载来源实现。
- 快照中的 `ApiProvider.java` 信任任意证书并跳过主机名校验，服务地址也未强制 HTTPS；ADR 0006 是必须消除该高风险行为的目标约束。

## 需求

### R-01 需求来源可追溯

- ADR 0001 至 0007 中的每项约束都必须映射到一个明确的 Trellis 需求、设计决策或显式失效记录。
- 每项迁移后的需求必须标注来源 ADR，避免产生无法回溯的第二事实来源。
- 不得静默弱化、扩大或改写仍然有效的兼容性、安全性和数据保护边界；后续用户决定取代原条款时必须记录原因与替代边界。

### R-02 需求与决策分层

- Trellis PRD 记录用户结果、范围、约束和可观察验收条件。
- 技术边界、依赖关系、兼容策略、迁移与回滚设计记录在 `design.md`。
- 执行顺序、验证命令和审查关口记录在 `implement.md`。
- 原 ADR 继续解释难以逆转且存在权衡的架构决定，不用 PRD 替代其决策历史。

### R-03 规划交付物

- 采用一个父任务与六个可独立验收的子任务；父任务负责总体路线、来源追溯、跨任务约束与最终集成验收。
- 六个子任务分别负责平台与交付基线、Room 数据基础、Leanote API 与内容兼容、账户绑定可靠同步、Compose 与 WebView 编辑器边界、HTTPS 服务连接安全。
- 本次迁移负责完整规划父任务和全部子任务，不只创建占位任务。
- 规划必须覆盖六个来源主题，明确它们之间的依赖与可独立验收边界。
- 每项验收条件必须描述可观察结果或可复现证据，不以“代码已修改”作为完成证明。
- 复杂任务在离开规划阶段前必须具备 `prd.md`、`design.md`、`implement.md`，以及可供实现与检查代理加载的真实上下文清单。

### R-04 状态与证据不可混淆

- 迁移后的需求必须明确标记当前事实、目标要求和待补验证据。
- 不得因为 ADR 已存在而宣称目标已经实现。
- 直接反例必须转化为回归验收边界，尤其包括账户切换期间的同步身份固定、TLS 证书链与主机名校验、Room 数据与 Leanote 服务语义的一致性。

## 已确认的范围变化

- ADR 0003 的客户端覆盖升级、签名连续性和旧数据库迁移条款由 ADR 0007 取代。
- ADR 0003 的 Leanote 服务 API、USN、冲突、Markdown/HTML 内容和附件契约继续有效。
- 原 ADR 0003 保留原文并带显式取代说明，ADR 0007 记录替代边界和理由。

## 范围外

- 修改 Java、Kotlin、Gradle、Android 资源、数据库、网络、同步或编辑器实现。
- 启动任一现代化实现任务。
- 删除或合并原 ADR。
- 在本任务中填充 `00-bootstrap-guidelines` 所属的现状型编码规范。

## 验收条件

- [ ] ADR 0001 至 0007 均出现在来源到需求的追溯矩阵中，每项条款都映射到有效需求或带理由的失效记录。
- [ ] 需求按可独立验收边界组织，并明确跨边界依赖关系。
- [ ] 每个需求拥有可观察、可复现且不依赖实现细节表述的验收条件。
- [ ] `prd.md`、`design.md`、`implement.md` 之间职责分离，无相互矛盾或重复事实来源。
- [ ] 原 `docs/adr/0001` 至 `0006` 均保留；ADR 0003 的显式取代说明与新增 ADR 0007 准确表达后续决定。
- [ ] 每项目标要求均与当前实现状态分开陈述，未验证项不会被标记为完成。
- [ ] 规划产物通过 Trellis 任务校验与文本差异检查。

## 任务树

| 任务 | 主要来源 | 独立交付边界 |
|---|---|---|
| `09-09-android-platform-baseline` | ADR 0001、0002、0007 | Android 平台、目标技术栈、应用身份与首次发布基线 |
| `09-09-room-data-foundation` | ADR 0001、ADR 0003 的有效数据语义、ADR 0007 | 面向全新安装的 Room/Room FTS 数据基础，不兼容旧客户端数据库 |
| `09-09-leanote-api-content-compatibility` | ADR 0003 | REST、USN、冲突、内容格式与附件契约 |
| `09-09-account-bound-reliable-sync` | ADR 0004 | 固定账户范围的前台与持久同步 |
| `09-09-compose-webview-editor-boundary` | ADR 0001、0005 | Compose 外壳与隔离的 WebView 编辑内核 |
| `09-09-secure-https-service-connections` | ADR 0006 | 有效 HTTPS、系统信任与主机名校验 |

## 来源追溯矩阵

| ADR 条款 | 状态 | Trellis 归属 |
|---|---|---|
| ADR 0001：保持原生 Android，不迁移跨平台框架 | 有效 | `android-platform-baseline` |
| ADR 0001：Kotlin、Compose、分层数据、UDF、ViewModel、Coroutines/Flow、Hilt 目标栈 | 有效 | `android-platform-baseline`、`compose-webview-editor-boundary` |
| ADR 0001：按可运行纵向切片渐进迁移 | 有效 | 父任务集成门禁、`compose-webview-editor-boundary` |
| ADR 0001：保持单一 `:app`，仅凭明确证据拆模块 | 有效 | `android-platform-baseline` |
| ADR 0002：`minSdk 34`、`targetSdk 36`、`compileSdk 37` | 有效 | `android-platform-baseline` |
| ADR 0002：不按厂商系统名称判断兼容性 | 有效 | `android-platform-baseline` |
| ADR 0002：以后提高目标版本前完成行为回归 | 有效 | `android-platform-baseline`、父任务集成门禁 |
| ADR 0003：旧 APK 覆盖升级与历史签名连续性 | 被 ADR 0007 取代 | 不进入实现；平台任务只保留应用 ID 与安全发布身份 |
| ADR 0003：Leanote REST 路径、字段与错误响应 | 有效 | `leanote-api-content-compatibility` |
| ADR 0003：USN 分页与冲突语义 | 有效 | `leanote-api-content-compatibility`、`account-bound-reliable-sync` |
| ADR 0003：Markdown/HTML 规范内容与附件身份 | 有效 | `leanote-api-content-compatibility`、`room-data-foundation`、`compose-webview-editor-boundary` |
| ADR 0003：DBFlow 原地迁移、旧快照验证与失败保库 | 被 ADR 0007 取代 | 不进入实现；改由 `room-data-foundation` 创建全新 Room Schema |
| ADR 0004：同步会话固定账户、凭据、数据范围与游标 | 有效 | `account-bound-reliable-sync` |
| ADR 0004：持久同步使用唯一幂等 WorkManager，界面操作使用结构化协程 | 有效 | `account-bound-reliable-sync` |
| ADR 0005：Compose 外壳保留 WebView 编辑内核 | 有效 | `compose-webview-editor-boundary` |
| ADR 0005：结构化 JSON、受限来源、最小桥接与历史语料 | 有效 | `compose-webview-editor-boundary` |
| ADR 0006：仅允许系统信任、主机名匹配的 HTTPS | 有效 | `secure-https-service-connections` |
| ADR 0006：无任意信任、主机名绕过或明文 HTTP fallback | 有效 | `secure-https-service-connections` |
| ADR 0007：无旧客户端升级兼容，首发从 Android 14 与全新 Room 开始 | 有效 | `android-platform-baseline`、`room-data-foundation` |
| ADR 0007：继续兼容目标 Leanote 服务的数据/API 语义 | 有效 | `leanote-api-content-compatibility` 及其下游任务 |
