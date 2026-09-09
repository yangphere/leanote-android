# Room 数据基础

## 目标

为首个公开版本建立 Room 与 Room FTS 数据基础，在不承担未部署旧客户端数据库兼容的前提下完整表达 Leanote 账户数据与同步语义。

## 来源与当前证据

- 来源：`docs/adr/0001-incremental-native-android-modernization.md`、`docs/adr/0007-greenfield-client-without-legacy-upgrade.md`，以及 `docs/adr/0003-preserve-data-and-api-contracts-during-migration.md` 中继续有效的数据与服务语义。
- `app/build.gradle:18,81-86` 当前使用 DBFlow `4.0.0-beta2`。
- `app/src/main/java/org/houxg/leamonax/database/AppDataBase.java:22-167` 当前数据库名为 `leanote_db`、版本为 5，并包含账户 USN、笔记字段和 FTS4 等历史迁移。
- 当前没有部署给用户的旧版 APK；DBFlow 数据库只作为现有领域模型和行为的代码证据，不作为公开版本必须接收的用户数据来源。

## 需求

- Room Schema 必须表达账户、笔记本、笔记、标签、附件关联、未同步更改、同步游标、冲突副本和内容格式语义。
- Room FTS 必须索引规范笔记内容，并具有明确的创建、更新、删除和重建一致性规则。
- 各账户的数据、游标和未同步更改必须在持久化层隔离，不能依赖可变化的全局活动账户确定查询范围。
- 首个公开版本只支持全新创建目标 Room Schema；不实现 DBFlow 到 Room 的原地迁移、不读取旧数据库快照，也不验证旧 APK 覆盖升级。
- 在首次公开发布之前可以通过明确的开发数据重建处理 Schema 变化；首次发布后必须采用正常的版本化 Room migration，不得使用破坏性 fallback。

## 验收条件

- [ ] 全新安装可以创建目标 Room Schema，并完成账户、笔记本、笔记、标签、附件、同步游标和未同步更改的持久化回归。
- [ ] 账户隔离、关联约束、事务边界和删除行为具有可执行测试。
- [ ] Markdown/HTML 正文、附件身份、冲突副本、脏标记和 USN 的存取结果满足 API/内容兼容任务定义的语义。
- [ ] Room FTS 的创建、更新、删除和重建结果一致。
- [ ] 切换完成后产品运行时不再依赖 DBFlow，且没有伪装成兼容路径的 destructive migration fallback。

## 范围外

- DBFlow 到 Room 的原地迁移、旧数据库快照兼容和旧 APK 覆盖升级。
- 改写 Leanote 服务端数据模型或 REST 契约。

## 依赖

- 启动前要求 `09-09-android-platform-baseline` 与 `09-09-leanote-api-content-compatibility` 完成，以消费稳定的构建基线、实体语义和测试夹具。
