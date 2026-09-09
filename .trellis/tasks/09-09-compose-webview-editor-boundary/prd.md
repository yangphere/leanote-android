# Compose 外壳与 WebView 编辑器边界

## 目标

把应用页面、导航和状态管理渐进迁移到 Compose，同时保留并隔离承载历史内容兼容性的 WebView 编辑内核。

## 来源与迁移前证据

- 来源：`docs/adr/0001-incremental-native-android-modernization.md`、`0005-compose-shell-with-webview-editor-boundary.md`。
- 迁移前快照中的 `Editor.java` 与 `MarkdownEditor.java` 使用 WebView、assets 和 JavaScript bridge 编辑 Markdown/富文本内容。
- 2026-09-09 平台迁移前快照中，`app/src/main/AndroidManifest.xml` 与 `LaunchActivity.java` 显示启动流程由传统 Activity 路由到登录或主页面；具体历史行号以父任务的带提交快照为准。
- `MainActivity.java:58-93`、`NoteFragment.java:151-212` 显示主页面的最近笔记列表已有集中查询入口，点击笔记通过 Intent 打开现有预览 Activity。
- `MainActivity.java:144-166`、`NoteEditActivity.java:75-106` 与 `EditorFragment.java:129-151` 显示新建与编辑可以继续通过现有 Activity/Fragment/WebView 边界工作。
- 平台迁移前的 AGP、Kotlin 和 Android Support 依赖不支持目标 Compose 栈；本任务消费已验收的平台基线，不自行维护第二套构建配置。
- 快照中的 `MarkdownEditor.java` 与 `RichTextEditor.java` 顶层页面均来自应用内 assets，但通过多个 `addJavascriptInterface` 方法暴露回调。
- 快照中的 `MarkdownEditor.java` 与 `RichTextEditor.java` 通过字符串插值调用 JavaScript；HTML 转义不等价于 JavaScript 字符串编码，标题、正文、链接或图片地址可能截断脚本边界。
- `Editor.java:92-115` 只拦截精确的本地图片 URI，未限制其他 WebView 导航；历史内容可能包含本地图片、目标 Leanote 服务图片和外部远程图片。

## 需求

- 新的应用页面、导航和状态管理采用 Compose、ViewModel 与单向数据流，并按可运行纵向切片迁移。
- 首轮现代化保留 Markdown 与富文本 WebView 编辑内核，不在同一任务替换编辑器实现。
- 原生与编辑器之间使用版本化、结构化 JSON 消息，桥接接口只暴露完成编辑所需的最小能力。
- WebView 只加载允许的本地或明确批准来源；不得向不可信页面暴露高权限 JavaScript 接口。
- 历史 Markdown、HTML、图片地址和 JavaScript 回调协议用真实内容语料验证。
- 首个纵向切片限定为已登录后的最近笔记列表：Compose shell 使用 ViewModel 与单向数据流表达当前账户 ID、加载、空列表、错误、刷新和笔记列表状态。
- 首个切片只封装现有最近笔记查询，不迁移 DBFlow Schema 或同步服务；数据加载不得阻塞主线程，ViewModel 创建时固定账户 ID，刷新时不得重新读取可变化的全局活动账户。
- 点击现有笔记继续通过 Intent 打开当前预览 Activity；新建笔记继续通过现有创建路径进入 `NoteEditActivity` 与 WebView 编辑器；编辑返回后 Compose 列表重新加载。
- 编辑器桥接收敛为单一、版本化的 `postMessage(json)` 入口；接收端必须校验版本、消息类型、请求 ID 和各类型 payload Schema，未知或畸形消息明确拒绝。
- Android 向 JavaScript 传递标题、正文、链接和图片地址时必须使用结构化序列化边界，不得把内容插入可执行脚本文本。
- 只有允许的应用内编辑器顶层页面可以获得桥接能力；远程页面不得继承或重新获得 bridge。
- 顶层导航不得继续留在编辑器 WebView；经过 scheme 校验的 `http`/`https` 外部链接交给系统处理，其他 scheme 默认拒绝。
- 本地图片只允许精确的 `file:/getImage?id=<localId>` 形式，并在读取前校验 ID；目标服务图片只允许 `https://me.xiqi.site/api/file/getImage` 的预期参数形式，两类图片可以自动显示。
- 其他 HTTPS 图片必须保留原始 Markdown/HTML URL，但默认不发起网络请求并显示占位；用户明确点击后只为该图片执行单次加载。
- HTTP 图片以及 `javascript:`、任意 `file:`、`content:`、`intent:` 等其他 scheme 不加载、不导航，也不得从正文中删除或改写原地址。

## 验收条件

- [ ] 每个迁移切片保持可导航、可编辑、可保存，并能与未迁移页面共存。
- [ ] 原生与 WebView 之间不存在未经验证的拼接脚本参数或无限制桥接方法。
- [ ] 非允许来源不能加载到具有应用桥接权限的 WebView。
- [ ] 历史 Markdown、富文本、图片与回调语料在迁移前后结果一致。
- [ ] 替换编辑内核未被夹带进本任务。
- [ ] 已登录用户进入首个 Compose shell 后可以观察加载、空列表、错误、刷新及最近笔记内容状态。
- [ ] 点击、创建、编辑返回流程继续穿过现有 Activity/WebView 边界，且返回后列表反映已保存结果。
- [ ] 首个切片不改变 DBFlow Schema、USN、同步服务或 WebView JavaScript bridge，也不重复实现完整旧列表功能。
- [ ] 本地图片和目标服务图片按精确白名单自动显示；非法本地 ID 或越界路径明确失败。
- [ ] 第三方 HTTPS 图片在用户点击前没有网络请求，点击后仅单次加载；HTTP 与其他 scheme 始终被拒绝。
- [ ] 图片加载策略不会改写或删除笔记中的原始 Markdown/HTML 地址。

## 范围外

- 重写 Markdown 或富文本编辑器内核。
- 为迁移方便改变笔记的规范内容格式。
- 在首个切片迁移账户切换、导航抽屉、笔记本、标签、搜索、多选、Room、WorkManager 或编辑器桥接协议。

## 依赖

- 启动前要求 `09-09-android-platform-baseline`、`09-09-secure-https-service-connections` 与 `09-09-leanote-api-content-compatibility` 完成，以消费 Compose 基线、统一远程资源安全策略、历史内容语料与契约。
