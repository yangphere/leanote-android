# Android 现代化迁移前状态证据

## 目的

本文件记录 2026-09-09 从基线提交 `707e8fa` 确认的平台迁移前快照与证据缺口。ADR 与 PRD 描述目标要求；这里不代表当前工作树，也不宣称目标已经实现。后续任务引用本文件时必须称为“迁移前证据”，并另行加载其依赖任务的最终验证产物。

## 构建与发布

- `build.gradle:3-11`：AGP `3.2.1`、Kotlin `1.3.11`。
- `app/build.gradle:26-45`：`compileSdkVersion 26`、`minSdkVersion 19`、`targetSdkVersion 26`、应用 ID `com.leanote.android`。
- `app/build.gradle:56-60`：快照中的发布签名从环境变量读取；由于没有已部署 APK，因此没有旧签名连续性要求。
- 快照为单一 `:app` 模块，未发现 Compose、WorkManager、Hilt 或 AndroidX ViewModel 目标栈。

## 数据与 API

- `app/build.gradle:18,81-86`：DBFlow `4.0.0-beta2`。
- `app/src/main/java/org/houxg/leamonax/database/AppDataBase.java:22-167`：数据库 `leanote_db` 版本 5，包含账户 USN、笔记字段和 FTS4 历史迁移。
- `app/src/main/java/org/houxg/leamonax/network/api/`：现有 REST 路径与 Retrofit 接口。
- `app/src/main/java/org/houxg/leamonax/service/NoteService.java:89-160,240-304`：USN 分页、冲突副本与图片地址转换行为。
- 唯一真实兼容目标为 `https://me.xiqi.site/`；测试凭据只通过受保护配置注入，不能进入仓库或日志。

## 账户与同步

- `app/src/main/java/org/houxg/leamonax/database/AccountDataStore.java:21-35`：快照中的活动账户按 token 与最近使用时间动态查询。
- `app/src/main/java/org/houxg/leamonax/background/NoteSyncService.java:26-103`：快照中的同步使用旧式 Android Service，并可能在长流程中重新读取全局活动账户。
- 快照中未发现 WorkManager、稳定账户会话上下文或跨账户串行同步队列。

## UI 与编辑器

- `app/src/main/AndroidManifest.xml`、`LaunchActivity.java`：基线提交中的传统 Activity 启动路由；不使用会随迁移失效的工作树行号定位历史证据。
- `MainActivity.java:58-93`、`NoteFragment.java:151-212`：主页面、导航抽屉与最近笔记列表。
- `NoteEditActivity.java:75-106`、`EditorFragment.java:129-151`：编辑页仍由 Fragment 和 WebView 内核承载。
- `MarkdownEditor.java:22-78`、`RichTextEditor.java:26-90`：本地 asset 顶层页面、多方法 JavaScript bridge、字符串插值执行 JavaScript。
- `Editor.java:92-115`：只拦截本地图片 URI，未集中限制导航来源。

## 网络安全

- `app/src/main/java/org/houxg/leamonax/network/ApiProvider.java:39-118`：快照中存在信任任意证书与恒真主机名验证器，服务地址未强制 HTTPS。
- 快照中未发现 Network Security Config 或统一的安全服务端点值对象。

## 快照时未关闭证据

- 快照时尚未运行现代 Android 构建、模拟器或设备测试。
- 快照时尚未对 `https://me.xiqi.site/` 执行真实 API 契约测试。
- 快照时尚未建立历史 Markdown/HTML/附件语料库。
- 快照时尚未验证 Compose、Room、WorkManager、Hilt 与目标 SDK 的具体兼容版本矩阵。
