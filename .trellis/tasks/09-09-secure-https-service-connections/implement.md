# 安全 HTTPS 服务连接实施计划

## 收敛端点与客户端

- [ ] 清点登录、同步、附件、图片和其他 Retrofit/OkHttp 构造位置。
- [ ] 实现唯一 `ServiceEndpoint` 解析/规范化与错误类型，并让所有调用方消费该值。
- [ ] 建立单一安全 HTTP 客户端工厂，使用平台默认 TLS 和主机名校验。
- [ ] 将 endpoint、账户本地 ID 与 token 快照绑定为不可变客户端；认证客户端不带 token，账户切换不复用旧 base URL。
- [ ] 删除 trust-all、恒真 hostname verifier、HTTP fallback、自动重定向和重复客户端配置。
- [ ] 将附件/笔记图片下载及头像加载迁移到共享客户端和 exact-origin 解析，移除 `URL.openStream()` 与原始远程 URL Glide 旁路。
- [ ] 在 Manifest 禁用 cleartext，并移除 BODY/Stetho 敏感流量日志、带 token URL 日志及 `Account.toString()` 的 token 输出。

## 账户修复流程

- [ ] 为失效端点建立显式阻断状态，保持本地浏览、编辑和导出。
- [ ] 在每个网络入口统一阻断失效账户，不允许附件或图片旁路。
- [ ] 新地址通过真实 TLS/API 验证后原子更新；失败保留原配置和本地数据。

## 验证

- [ ] 单元测试地址解析、规范化、scheme、host、端口和重定向规则。
- [ ] 测试账户切换、认证前后客户端与 token 注入，证明 endpoint 和 credential 不会跨账户混配。
- [ ] 受控 TLS 测试覆盖有效、不受信、过期、主机名不匹配、HTTP、跨 origin HTTPS 和降级重定向；所有 3xx 断言没有后续请求。
- [ ] 请求记录测试覆盖附件、笔记图片和头像，证明它们使用同一客户端、拒绝跨 origin，且部分下载不会发布。
- [ ] 架构扫描拒绝生产源码中的宽松信任、`URL.openStream()`、独立远程图片加载与敏感 BODY 日志；非生产命中必须逐项白名单说明。
- [ ] 对 `https://me.xiqi.site/` 执行不泄露凭据的登录/连接冒烟。
- [ ] 运行 `.\gradlew.bat testDebugUnitTest`、`.\gradlew.bat lint`、相关设备测试与 `git diff --check`。
- [ ] 扫描 `TrustManager`、`HostnameVerifier`、`http://`、`openStream`、远程 Glide 与独立客户端构造；逐项解释测试服务器或构建仓库等非生产命中。

## 回滚点

- [ ] 端点值对象、客户端收敛和账户修复 UI 分阶段提交。
- [ ] 任何回滚都保持 fail-closed；禁止恢复跳过 TLS 的旧实现。
