# Compose 外壳与 WebView 编辑器边界设计

## 渐进式 UI 边界

首个切片新增已登录后的 Compose 最近笔记页面。`NotesViewModel` 在创建时接收并固定账户 ID，通过 Repository 在后台加载，输出单一不可变 `NoteListUiState`；Compose 只渲染状态并发送刷新、打开和新建意图。

预览与编辑继续使用现有 Activity Intent。Compose 不直接持有 WebView、DBFlow/Room Entity 或 Retrofit DTO，也不复制笔记本、标签、搜索和多选逻辑。编辑返回通过 Activity result/lifecycle 事件触发一次 ViewModel 刷新，不能由多个回调各自更新列表。

后续页面按同样纵向切片迁移；旧 Fragment 在其能力被完整替代前保留，替代后删除对应入口，避免两个可交互 UI 同时拥有同一状态。

## 编辑器协议

WebView 内核继续承载 Markdown 与富文本实现。原生与页面之间只暴露一个版本化 JSON 消息入口，消息信封包含 `version`、`type`、可选 `requestId` 和按类型校验的 `payload`。初始协议版本为 1；未知版本、类型、字段或畸形 JSON 返回明确协议错误，不执行部分消息。

命令至少覆盖设置标题/正文、组合初始化、插入图片/链接、切换编辑状态和请求内容；事件至少覆盖 ready、格式/选择状态、链接点击、图片点击和内容响应。旧 `HostApp`/`TinnyMceCallback` 回调只允许通过一个迁移适配器转成协议消息，全部页面迁移后删除旧多方法 bridge。

Android 向 JavaScript 发送数据使用 WebMessage 或等价的结构化序列化调用，不把标题、正文或 URL 插入 JavaScript 源码。bridge 仅在允许的编辑器顶层来源加载完成后启用，页面离开允许来源时立即失效。

## WebView 来源与资源

- 目标顶层来源使用 `WebViewAssetLoader` 的 `https://appassets.androidplatform.net/assets/...`；过渡期若必须使用 `file:///android_asset/...`，仍由同一客户端精确匹配两个编辑器入口。
- 任何远程页面都不能加载在拥有 bridge 的 WebView 中；顶层导航交给受限外部链接处理器，仅允许 `http`/`https`，其他 scheme 默认拒绝。
- `file:/getImage?id=<localId>` 仅由精确 path 和合法 ID 拦截；`https://me.xiqi.site/api/file/getImage` 按预期参数自动加载。
- 第三方 HTTPS 图片默认占位，用户点击后只单次加载；HTTP 与其他 scheme 永不加载。内容中的原 URL 始终保留。

## 测试策略

Compose 测试覆盖状态渲染、事件和 Activity 边界；ViewModel 测试覆盖固定账户、刷新与错误。WebView instrumentation 测试覆盖协议 Schema、特殊字符、来源切换、导航、图片白名单和第三方请求是否发生。历史 Markdown/HTML/附件语料来自 API 兼容任务。

## 回滚

每个纵向切片可把入口切回旧 Activity/Fragment，但不能回滚已收紧的 bridge 来源、结构化序列化或导航规则。禁止长期保留两套可写 UI 或新旧 bridge 并行成为正式接口。
