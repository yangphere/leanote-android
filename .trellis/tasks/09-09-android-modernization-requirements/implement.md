# Android 现代化路线执行计划

## 阶段 1：建立平台与安全基础线

- [ ] 完成 `09-09-android-platform-baseline`，得到可构建、可安装的现代 Android 基线。
- [ ] 在平台基线上完成 `09-09-secure-https-service-connections`，消除全部 TLS 绕过。
- [ ] 复核两项结果没有引入跨平台框架、旧客户端升级兼容、明文/全信任 fallback 或第二套服务契约。

## 阶段 2：固化目标服务契约

- [ ] 完成 `09-09-leanote-api-content-compatibility` 的契约夹具、真实服务基线与脱敏证据。
- [ ] 确认客户端只对 `https://me.xiqi.site/` 执行目标服务回归，且通过统一 HTTPS 边界连接。

## 阶段 3：并行能力迁移

- [ ] 在平台与 API 语义稳定后完成 `09-09-room-data-foundation`。
- [ ] 在平台、HTTPS 与内容契约稳定后完成 `09-09-compose-webview-editor-boundary`，先交付最近笔记纵向切片，再收敛编辑器桥接。

## 阶段 4：可靠同步

- [ ] 在平台、Room、API 与 HTTPS 子任务完成后启动 `09-09-account-bound-reliable-sync`。
- [ ] 验证账户切换、重复触发、排队、进程重启、USN 推进、冲突与错误归属。

## 阶段 5：父任务集成门禁

- [ ] 对照 PRD 来源追溯矩阵逐条检查 ADR 0001 至 0007。
- [ ] 运行所有子任务声明的单元、集成、构建、lint、模拟器/设备和真实服务验证。
- [ ] 确认未运行或受保护环境证据仍明确标记，不能用其他测试代替。
- [ ] 检查应用仅连接目标 HTTPS 服务、各账户数据隔离、内容与附件语义一致、WebView bridge 只在允许来源启用。
- [ ] 执行 `python ./.trellis/scripts/task.py validate`、任务图校验与 `git diff --check`，复核任务元数据、文档链接和无敏感凭据。

## 启动规则

父任务本身不作为产品实现目标启动。每次只启动依赖已满足且规划经过单独审核的子任务；任务创建和本路线审核不自动授权任何子任务进入 `in_progress`。
