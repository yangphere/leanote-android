# 安全 HTTPS 服务连接实施计划

## 收敛端点与客户端

- [ ] 清点登录、同步、附件、图片和其他 Retrofit/OkHttp 构造位置。
- [ ] 实现唯一 `ServiceEndpoint` 解析/规范化与错误类型，并让所有调用方消费该值。
- [ ] 建立单一安全 HTTP 客户端工厂，使用平台默认 TLS 和主机名校验。
- [ ] 删除 trust-all、恒真 hostname verifier、HTTP fallback 和重复客户端配置。

## 账户修复流程

- [ ] 为失效端点建立显式阻断状态，保持本地浏览、编辑和导出。
- [ ] 在每个网络入口统一阻断失效账户，不允许附件或图片旁路。
- [ ] 新地址通过真实 TLS/API 验证后原子更新；失败保留原配置和本地数据。

## 验证

- [ ] 单元测试地址解析、规范化、scheme、host、端口和重定向规则。
- [ ] 受控 TLS 测试覆盖有效、不受信、过期、主机名不匹配、HTTP 和降级重定向。
- [ ] 对 `https://me.xiqi.site/` 执行不泄露凭据的登录/连接冒烟。
- [ ] 运行 `.\gradlew.bat testDebugUnitTest`、`.\gradlew.bat lint`、相关设备测试与 `git diff --check`。
- [ ] 扫描 `TrustManager`、`HostnameVerifier`、`http://` 和独立客户端构造；逐项解释测试服务器或构建仓库等非生产命中。

## 回滚点

- [ ] 端点值对象、客户端收敛和账户修复 UI 分阶段提交。
- [ ] 任何回滚都保持 fail-closed；禁止恢复跳过 TLS 的旧实现。
