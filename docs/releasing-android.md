# Android 构建与发布

GitHub Actions 的 `.github/workflows/android.yml` 是唯一受支持的 CI 和发布入口。普通 Pull Request 只在 JDK 21、Android SDK 37 与 Build Tools 36.0.0 上执行 JVM 测试、lint 和 debug APK 构建，不读取生产签名材料。

带 `v` 前缀的 tag 或人工触发进入受保护的 `production` Environment。该 Environment 必须配置以下 secrets：

- `KEYSTORE_BASE64`
- `KEY_ALIAS`
- `KEY_PWD`
- `KEYSTORE_PWD`
- `BUGLY_PRD`（可选）

workflow 仅在 runner 临时目录解码 keystore，通过 `RELEASE_KEYSTORE_PATH` 交给 Gradle，并在成功或失败后清理。缺少任一必需值时，release 在打包前失败；没有 debug 签名或未签名 APK fallback。发布范围只有 `app-release.apk`，同时登记签名证书信息和 SHA-256。

本地 debug 验证使用仓库 Gradle Wrapper：

```powershell
$env:JAVA_HOME = '<JDK 21 directory>'
.\gradlew.bat testDebugUnitTest --no-configuration-cache
.\gradlew.bat lintDebug --no-configuration-cache
.\gradlew.bat assembleDebug --no-configuration-cache
.\gradlew.bat assembleDebug
```

本地不得保存生产 keystore。生产 release、真实设备验证和 16 KB page-size 运行证据只在受控 runner/设备登记。
