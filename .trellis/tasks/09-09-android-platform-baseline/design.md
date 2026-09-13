# Android 平台与交付基线设计

## 设计边界

本任务是现代化路线的第一波平台叶。它拥有构建矩阵、应用身份、发布门禁、系统窗口行为和 Photo Picker 导入 seam；不拥有 Room schema、Leanote DTO/API、同步队列、TLS 策略或 WebView bridge。所有下游任务必须消费这里的明确边界，不能复制第二套版本、账户、文件或安全规则。

## 构建版本矩阵

| 组件 | 基线 | 单一来源/验证要求 |
|---|---:|---|
| Gradle Wrapper | 9.6.0 | `gradle/wrapper/gradle-wrapper.properties`；记录实际 wrapper 输出 |
| Android Gradle Plugin | 9.4.0 | `build.gradle`；确认支持 compile SDK 37 |
| JDK | 21（仓库与 CI 固定基线） | Gradle Launcher 和 Daemon 都必须为 JDK 21；不能只看 shell 的 `java -version` |
| Kotlin/Compose Compiler | 2.3.21 | 根插件声明；与 Compose plugin 版本一致 |
| KSP | 2.3.11 | 根插件声明；仅供 Hilt 等注解处理 |
| Compose BOM | 2026.08.00 | `app/build.gradle`；不使用动态版本 |
| Hilt | 2.60.1 | 根插件与 app 依赖一致 |
| Android SDK | min 34 / target 36 / compile 37 | `app/build.gradle`；runner 必须安装 platform 37 |

版本选择以 `research/version-matrix.md` 的官方来源为依据。实现前必须在实际 runner 记录 SDK 目录、Build Tools revision、Wrapper、AGP、Kotlin、JDK、依赖解析结果；缺少其中任何一项时，构建证据只能标为 `blocked`。

迁移顺序为：Gradle/JDK/AGP → AndroidX → Kotlin/Compose → ViewModel/Coroutines/Flow → Hilt。每个阶段都要在同一 runner 通过最小构建后再进入下一阶段，并删除已失效的兼容配置、仓库或插件。ButterKnife、DBFlow 和 Jetifier 的过渡开关必须带退出条件，不能被后续任务当作永久目标架构。

## 模块与依赖边界

- 保持单一 `:app` 模块；`settings.gradle` 是模块事实来源。
- Java 与 Kotlin 在纵向迁移期共存；新增 Compose 代码使用 Kotlin。
- Compose 页面只通过 ViewModel/Repository 接口消费状态，不直接访问 DBFlow、Room、Retrofit 或全局账户。
- Hilt 负责新代码的依赖装配；遗留静态上下文和旧 Service 可暂时存在，但不作为新目标栈的第二容器或成功标准。
- 应用依赖仓库只允许 `google()`、`mavenCentral()` 和 HTTPS JitPack；插件解析的 Gradle Plugin Portal 不等于应用依赖仓库。

## 应用身份、签名与交付

- `namespace` 保持 `org.houxg.leamonax`，`applicationId` 保持 `com.leanote.android`。
- 首发只验证全新安装。ADR 0007 已取代旧 APK 覆盖升级、历史签名连续性和 DBFlow 原地迁移，不得在平台任务中恢复这些承诺。
- release 签名使用 GitHub Actions 受保护 `production` Environment 的 keystore secret 和 `KEY_ALIAS`、`KEY_PWD`、`KEYSTORE_PWD`；workflow 在 runner 临时目录解码并通过受控路径交给 Gradle，结束时无论成功失败都删除。`leanote-android-new.jks.enc` 不再作为仓库签名输入，凭据不出现在版本控制、命令行、日志或测试产物。首发只产出 APK，不产出 AAB。签名私钥保持稳定，定期审计访问和离线备份，不进行破坏 APK 更新能力的随意换钥。
- `BUGLY_PRD` 是可选环境输入，缺失时为空；只保留 Java crash reporter，关闭 native monitor。无应用内 APK 安装 owner，因此移除 `REQUEST_INSTALL_PACKAGES` 与 Bugly upgrade activity。
- GitHub Actions 是唯一受支持的交付流水线，必须使用 JDK 21、SDK platform 37，明确 APK 签名材料来源、文件命名和部署步骤，并执行构建、测试、lint、包完整性检查后再发布。生产 workflow 只允许受保护 tag/人工批准环境读取签名 secrets；普通 PR 不得注入 secrets。规格审计时遗留的 `.travis.yml` 已由当前实现删除，不再存在并行发布入口。

## 系统窗口与返回行为

### View Activity

所有可见的 View Activity 继承 `BaseActivity`。基类在内容安装前关闭 decor fitting，并在根内容上合并 system bars、display cutout 和 IME insets，保留原始 padding。页面不得再次把同一组全屏 insets 加到根视图；需要局部处理时必须明确消费关系并用设备测试证明无双重 padding。不可见的 widget redirect Activity 不承担用户窗口基线。

### Compose Activity

Compose 宿主使用同一 edge-to-edge 策略，根节点负责 `safeDrawing` 与 `imePadding`。平台 smoke 只证明 Compose/Hilt/ViewModel wiring 和状态事件更新，不证明业务列表、账户、数据或编辑器已迁移。

### 大屏与 predictive back

Manifest 不锁定固定方向。API 36 大屏可能忽略方向请求，布局必须能在旋转和可调整尺寸后继续工作；预览页的用户主动旋转属于独立 UX 操作，不得成为发布兼容依赖。predictive back 需在 API 34+ 真实手势下验证回调完成和取消；普通返回键或静态扫描不能替代。

## Photo Picker 导入 seam

```text
Picker URI + MIME
        │ ContentResolver（非空 stream）
        ▼
后台复制到 filesDir/selected-images
        │ pending result（跨配置由 ViewModel 持有）
        ▼
下游关系/正文消费者确认一次
        ├─ acknowledged：所有权交给关系 owner
        ├─ failed：清理 managed copy，返回错误
        └─ abandoned：进程终止后的账本/清扫策略清理
```

- Picker 入口只请求 `image/*`，用户取消是无操作，不请求相机或广泛存储权限。
- MIME 必须非空且属于受支持白名单；复制错误、空 stream 和消费者失败均为明确错误，不返回空成功。
- managed copy 的目录所有权由 `SelectedImageStore` 的规范化路径判定；任何路径越界都不删除。
- Fragment 只在有效 View 生命周期观察结果；ViewModel 持有 running/pending 状态并保证确认幂等。关系写入顺序、正文插入、服务器 reconciliation、附件上传和 Room 事务由对应下游任务定义。
- `SelectedImageStore` 在复制前写入持久导入账本，复制完成后原子记为 `succeeded`，下游关系提交后记为 `acknowledged`。配置重建保留 pending；进程终止将未确认记录视为 `abandoned`，应用启动立即清扫并由 WorkManager 补偿。清扫前向关系 owner 核对，已有关系的文件只补记确认，不删除；无关系的受管副本删除。进程终止不恢复未确认选择，用户重新选择。所有操作必须幂等且只触及规范化受管目录。

## 下游交接

| 输出 | 消费任务 | 不变量 |
|---|---|---|
| SDK/Gradle/JDK/模块基线 | HTTPS、API、Room、Compose、同步 | 不复制版本事实来源，不降低 min/target/compile SDK；Gradle 运行时固定 JDK 21 |
| edge-to-edge/大屏/back 验证矩阵 | Compose/WebView | 不用静态布局或普通返回键替代设备证据 |
| Picker managed-file seam | Room/API/Compose | 关系、正文、服务器文件和上传语义各归其任务；路径清理保持 owner 检查 |
| 应用 ID/签名/包完整性门禁 | 父任务集成 | 首发全新安装并发布 APK；没有签名或 16 KB 证据不发布 |

## 证据闭合顺序

1. 先建立唯一 GitHub Actions workflow 和 `production` Environment 权限边界；PR 只执行无 secrets 的 debug/test/lint，受保护 tag 或人工批准才允许 release。
2. 在同一 runner 固定并登记 JDK 21、SDK platform 37、Build Tools、Wrapper、AGP 和依赖解析结果；通过 JVM 测试、lint、两次 debug 构建及 configuration cache 检查。
3. 实现 `SelectedImageStore` 持久账本和清扫策略；先完成 JVM 竞态测试，再在真实设备验证配置重建、进程终止和关系核对。
4. 迁移签名输入到 GitHub Environment，临时解码并清理；无 secrets 的 release 必须 fail-closed，有 secrets 的 release 只产生首发 APK。
5. 对最终签名 APK 进行 Manifest/依赖/动态库扫描、ZIP/ELF 16 KB 检查，再在 API 34/35+/36 和真实 16 KB page-size 目标完成安装与运行验证。
6. 只有 EVID-01 至 EVID-06 的命令、runner、设备、产物和校验值全部登记后，任务验收才能从 `blocked` 转为 `verified`；历史 debug 记录不能替代当前证据。

## 回滚

平台升级以聚焦提交回滚，必须整体恢复互相依赖的 Wrapper、AGP、JDK、Kotlin、Compose 和 Hilt 版本，不能留下部分矩阵。回滚不能恢复明文/全信任网络、固定方向、旧 Support Library 或无签名 release fallback；Picker 失败时只清理受管目录，不删除外部路径。依赖本任务的下游在平台回滚后必须重新跑自己的构建和运行验证。
