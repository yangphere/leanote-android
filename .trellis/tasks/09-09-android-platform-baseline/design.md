# Android 平台与交付基线设计

## 构建版本矩阵

实现开始时以 Android 与 Jetpack 官方兼容表选择一组相互支持的 Gradle Wrapper、AGP、JDK、Kotlin、Compose Compiler/插件、Compose BOM、Hilt 和 AndroidX 版本，并把选择集中在 Gradle 版本目录或项目现有的单一依赖位置。ADR 固定的产品边界是 `minSdk 34`、`targetSdk 36`、`compileSdk 37`；工具版本不得凭猜测填写，也不得并存两套 Kotlin/Support/AndroidX 事实来源。

迁移顺序为：Gradle/JDK/AGP → AndroidX → Kotlin/Compose → ViewModel/Coroutines/Flow → Hilt。每一步保持 `:app` 可构建，并在下一步前删除已经失效的兼容配置、仓库或插件。

## 应用结构

- 保持单一 `:app` 模块。
- Java 与 Kotlin 可以在纵向迁移期共存；新目标栈代码使用 Kotlin。
- Compose 作为新页面的 UI 层，不要求一次性转换全部 XML/Fragment。
- ViewModel 暴露不可变 UI 状态和事件；数据访问通过接口注入，不由 Compose 直接调用 DBFlow、Room、Retrofit 或全局账户。
- Hilt 负责应用级依赖装配；不得同时保留手写全局单例作为第二容器。

## 发布边界

应用 ID 保持 `com.leanote.android`。首个公开版本只验证全新安装，不执行旧 APK 覆盖升级；发布签名仍由环境或受保护签名系统提供，任何密钥、口令或本机 keystore 路径不得进入版本控制。

SDK 兼容只按 API level 和 Android 行为变更判断，不按 HyperOS、MIUI 或其他厂商名称分支。以后提高 `targetSdk` 前必须新增对应行为变更回归证据。

## 平台行为边界

- 传统 View Activity 在公共基类统一启用 edge-to-edge，并把 system bar 与 IME insets 分发到页面根视图；Compose 宿主使用同一策略和 Compose insets，页面不得各自维护冲突的固定 padding。
- API 36 大屏可能忽略方向请求，因此产品布局以可调整尺寸为不变量，不通过 Manifest 锁定竖屏制造虚假兼容。
- 发布依赖优先使用纯 Java/Kotlin 实现。确需原生库时，必须由 lint 与 16 KB page-size 设备/模拟器证明 ELF load segment 兼容；APK ZIP 对齐不能替代该证据。
- 应用内 APK 升级不是当前发布渠道需求，不保留 `REQUEST_INSTALL_PACKAGES` 或只为该能力存在的升级 SDK。

## Photo Picker 数据流

系统 Picker URI 由可跨配置重建保留的状态所有者在后台复制到应用受管目录。导入边界解析并保存非空 MIME；未知或不支持的媒体在进入附件关系前明确失败。复制成功但数据库/正文插入失败时立即清理，附件关系删除或笔记删除时由同一受管文件 owner 清理磁盘副本。Fragment 只在有效 View 生命周期消费结果，不持有跨重建的 WebView 引用。

## 回滚

在没有公开用户数据的前提下，平台升级按聚焦提交回滚。回滚必须同时恢复互相依赖的 Wrapper、AGP、JDK、Kotlin 和 Compose 配置，不能留下部分升级的构建矩阵；不得用降低 `minSdk` 或引入旧支持库作为“构建成功” fallback。
