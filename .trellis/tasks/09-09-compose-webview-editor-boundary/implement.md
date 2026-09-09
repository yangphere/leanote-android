# Compose 外壳与 WebView 编辑器边界实施计划

## 首个最近笔记切片

- [ ] 在平台任务提供的 Compose/Hilt/ViewModel 基线上定义 `NoteListUiState`、事件和固定账户 Repository 接口。
- [ ] 实现 ViewModel 后台加载、空/错/刷新状态与编辑返回刷新，避免重复状态写入。
- [ ] 实现已登录 Compose shell 和最近笔记列表；点击与新建继续调用现有 Activity Intent。
- [ ] 保留旧导航抽屉、笔记本、标签、搜索、多选、同步和 WebView 编辑器，不复制其逻辑。

## 编辑器边界收敛

- [ ] 清点 Markdown/RichText 页面全部 Android↔JavaScript 命令、事件和历史回调差异。
- [ ] 定义并测试 JSON v1 信封及每种 payload Schema，建立单一 bridge 与临时旧回调适配器。
- [ ] 用结构化序列化替换 JavaScript 字符串插值；覆盖引号、反斜杠、换行、Unicode 和 HTML 边界样本。
- [ ] 集中实现允许顶层来源、bridge 生命周期、外部导航和资源拦截策略。
- [ ] 实现本地/目标服务图片自动加载、第三方 HTTPS 占位与单次加载、其他 scheme 拒绝。
- [ ] 迁移两个编辑器页面后删除旧多方法 bridge 和未使用回调实现。

## 验证

- [ ] 运行 ViewModel/状态 reducer JVM 测试与 Compose UI 测试。
- [ ] 在 API 34 及更高目标运行 WebView instrumentation：协议、来源、导航、特殊字符、图片和 bridge 失效。
- [ ] 用历史 Markdown、HTML、图片和附件语料验证打开、编辑、保存、返回与再次打开。
- [ ] 运行 `.\gradlew.bat testDebugUnitTest`、`.\gradlew.bat connectedDebugAndroidTest`、`.\gradlew.bat lint`、`.\gradlew.bat assembleDebug` 和 `git diff --check`。

## 回滚点

- [ ] 最近笔记切片、JSON 协议、来源策略和图片策略分别形成审查点。
- [ ] UI 入口可以回切旧页面；安全桥接与来源限制不得回滚为字符串插值或无限制导航。
