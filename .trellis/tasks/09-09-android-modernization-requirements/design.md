# Android 现代化需求迁移设计

## 边界

父任务是路线图与集成门禁，不直接拥有产品实现。六个子任务分别拥有可独立验收的能力边界；父子关系表达归属，执行依赖由子任务文档和 `task.json.meta.depends_on` 明确表达。

ADR 保留“为什么做出决定”，PRD 保留“必须得到什么结果”，`design.md` 保留技术边界与权衡，`implement.md` 保留执行顺序和验证关口。ADR 0003 被 ADR 0007 取代的条款只能出现在失效映射中，不能被下游任务重新引入。

## 依赖图

```text
android-platform-baseline ─> secure-https-service-connections
platform + https ──────────> leanote-api-content-compatibility
platform + api ────────────> room-data-foundation
platform + api + https ────> compose-webview-editor-boundary
platform + room + api + https ─> account-bound-reliable-sync
all six children ──────────> parent integration review
```

`android-platform-baseline` 是唯一首波实现任务。HTTPS 安全边界完成后再启动 API 任务，避免真实服务契约验证继续依赖全信任 TLS；API 契约稳定后，Compose 与 Room 可以并行。可靠同步最后消费平台、Room、API 与 HTTPS 边界。任务的 `meta.depends_on` 是可执行启动与归档门禁，不只是人工阅读提示。

## 跨任务不变量

- 账户 ID 是本地数据、同步会话、错误和 UI 状态的显式范围，不能通过全局当前账户隐式推导长生命周期工作。
- `https://me.xiqi.site/` 是唯一服务契约与远程资源来源基线；其他 Leanote 服务版本不进入兼容矩阵。
- Markdown/HTML 规范内容、附件身份、USN 和冲突语义由 API 任务定义，Room、同步和编辑器只能消费该契约。
- 所有网络入口使用同一 HTTPS 端点校验与平台默认 TLS；任何子系统不得建立绕过。
- 首个公开版本是全新安装，不读取 DBFlow 用户数据库；首次发布后的 Schema 变化才进入 Room 版本化迁移体系。
- 现代化按可运行纵向切片推进；迁移中的旧 Activity/Fragment/WebView 与新 Compose 页面之间必须保持显式边界。

## 证据模型

每项验收结果只能标记为以下一种状态：

- `confirmed-current`：由当前代码或配置证明；
- `target-required`：规划要求但尚未实现；
- `verified`：实现后由自动或真实环境验证证明；
- `blocked`：缺少设备、服务、凭据或受保护环境证据；
- `superseded`：被后续 ADR 明确取代。

不存在证据时不得从 `target-required` 推断为 `verified`。真实服务、签名、模拟器/设备和内容语料验证均需保留明确结果，不得由单元测试替代。

## 回滚与恢复

各子任务必须保持聚焦提交和明确回滚点。父任务只在六个子任务均完成、跨层回归通过且无未解释 ADR 偏差时关闭；任一子任务回滚后，依赖它的任务必须重新验证。规划文档回滚不得删除 ADR 历史或把失效条款恢复为有效要求。
