# Android 平台与交付基线

## 目标

建立仅面向 Android 14（API 34）及后续版本的原生 Android 现代化基础，同时保持明确的应用身份和安全发布边界。

## 来源与迁移前证据

- 来源：`docs/adr/0001-incremental-native-android-modernization.md`、`0002-android-14-minimum-platform-baseline.md`、`0007-greenfield-client-without-legacy-upgrade.md`。
- 2026-09-09、基线提交 `707e8fa` 的 `app/build.gradle` 使用 `compileSdkVersion 26`、`minSdkVersion 19`、`targetSdkVersion 26`，应用 ID 为 `com.leanote.android`；这是任务启动前快照，不代表当前工作树。
- 该快照仍是单一 `:app` 模块，尚无 Compose 目标栈实现。
- 当前没有部署给用户的旧版 APK，因此不存在 API 19–33 已安装用户或旧版发布线。

## 需求

- 保持原生 Android 路线，目标形态采用 Kotlin、Jetpack Compose、分层数据架构、单向数据流、ViewModel、Coroutines/Flow 和 Hilt。
- 采用可运行的纵向切片渐进迁移，不进行一次性重写。
- 首个现代化基线固定为 `minSdk 34`、`targetSdk 36`、`compileSdk 37`，兼容判断不得依赖 HyperOS 等厂商系统名称。
- 在没有明确构建、复用或协作证据前保持单一 `:app` 模块。
- 保留应用 ID `com.leanote.android` 作为应用身份，但不承担与未部署旧 APK 的签名或覆盖升级兼容；发布密钥和密码不得写入仓库。
- 首个对外版本直接以 API 34 为最低版本，不制作 API 19–33 最终维护版本，也不维持双发布线。
- `targetSdk 36` 页面必须正确处理 edge-to-edge、状态栏、导航栏与 IME；不得依赖 Android 16 大屏会继续遵守固定方向请求。
- 发布产物不得包含不兼容 16 KB page size 的原生库，也不得在没有明确发布渠道需求时请求安装其他 APK 的权限。
- 系统 Photo Picker 导入的私有副本必须有明确 MIME、跨配置重建的任务所有权和与附件关系一致的清理生命周期；失败不得留下孤立文件或延迟崩溃到同步阶段。

## 验收条件

- [x] 构建配置准确表达 API 34/36/37 基线，并能在受支持 Android 版本上安装与启动。
- [x] Compose/Hilt 基础设施冒烟可与尚未迁移的旧实现共同构建和运行；该调试入口不等同于用户可达的产品纵向切片。
- [ ] 首个发布候选能够在 API 34 及后续验证目标上完成全新安装、启动和基础冒烟验证（debug APK 已在 API 34/36 通过；受保护 release 签名尚未验证）。
- [ ] API 35+ 系统栏、IME、手势与三键导航不会遮挡交互控件，且 API 36 可调整尺寸/大屏不依赖固定竖屏请求。
- [ ] APK/AAB 中所有原生库满足 16 KB page-size 要求，合并 Manifest 不包含无 owner 的 `REQUEST_INSTALL_PACKAGES`。
- [ ] Photo Picker 的 URI 读取、复制、正文插入、MIME 异常、配置重建、关系删除和失败清理具有自动化或可复现的真实设备证据。
- [x] 仓库中不存在按厂商系统名称分支兼容的第二规则。
- [x] Gradle 模块仍为单一 `:app`，或拆分具有另行批准的明确依据。

## 范围外

- 执行数据库、同步、编辑器或 API 业务迁移。
- 在平台任务中缩窄或重新定义 `https://me.xiqi.site/` 的时间、REST、USN 或内容契约。
- 支持 API 33 及更低版本的现代化应用。
- 构建或维护面向未部署旧 APK 的兼容、安全维护版本。

## 依赖

- 后续 Room、同步和 Compose 子任务消费本任务的构建与运行时基线。
