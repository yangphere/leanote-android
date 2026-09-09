# Android 平台与交付基线实施计划

## 准备

- [x] 从 Android、AGP、Kotlin、Compose 和 Hilt 官方文档确认支持 `compileSdk 37` 的稳定版本矩阵及所需 JDK。
- [x] 记录当前 Wrapper、插件、仓库、Support Library、注解处理和打包配置，标出一次升级必须同步修改的文件。
- [x] 保留应用 ID；确认签名信息只从环境或受保护发布系统注入。

## 构建链迁移

- [x] 按 Wrapper/JDK/AGP → AndroidX → Kotlin/Compose → ViewModel/Coroutines/Flow → Hilt 顺序迁移，每步保持 `:app` 可构建。
- [ ] 设置 `minSdk 34`、`targetSdk 36`、`compileSdk 37`，并分别关闭 edge-to-edge/IME、大屏方向、predictive back 与 16 KB page-size 行为证据。
- [x] 删除旧 Support Library、废弃仓库和重复插件；仅保留已记录的 ButterKnife/DBFlow/Jetifier 过渡闸门，不保留第二版本来源。
- [x] 建立调试用最小 Compose 宿主、ViewModel/UDF 和 Hilt 注入基础设施冒烟路径，不把它表述为用户可达的业务纵向切片。
- [x] 移除无发布渠道 owner 的应用内 APK 升级能力与 `REQUEST_INSTALL_PACKAGES`，确认剩余依赖不再携带未对齐的 Bugly 原生库。
- [x] 收敛 Photo Picker URI 复制、MIME、配置重建和附件文件清理生命周期，并以失败路径回归保护。

## 验证

- [x] 运行 `.\gradlew.bat assembleDebug`、`.\gradlew.bat testDebugUnitTest` 和 `.\gradlew.bat lint`。
- [ ] 在 API 34 与一个更高可用 API 的模拟器或设备执行全新安装、冷启动、系统栏/IME、可调整尺寸、Photo Picker 和明确的 predictive-back 手势冒烟。
- [x] 检查 APK application ID、SDK 元数据和签名注入路径；扫描仓库确认无密钥或口令。
- [ ] 移除既有全信任 TLS 兼容配置并完成目标 Leanote 服务回归（属于后续网络安全任务，本任务只记录缺口）。
- [x] 运行 `git diff --check`，并在版本矩阵中记录未运行的设备、16 KB page-size 或受保护签名验证；不用普通返回键替代 predictive-back 证据。

## 回滚点

- [ ] 每个构建矩阵阶段形成聚焦提交；失败时整体回滚该阶段的版本组合。
- [ ] 禁止用降低目标 SDK、恢复旧 Support Library 或并存两套插件解决构建失败。
