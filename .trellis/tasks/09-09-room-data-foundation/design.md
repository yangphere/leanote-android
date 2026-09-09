# Room 数据基础设计

## 数据所有权

Room 是首个公开版本的唯一持久化事实来源。Schema 明确表达账户、笔记本、笔记、标签、笔记标签关联、附件与同步游标；所有账户数据行都能沿外键或显式账户 ID 回到所属账户。查询、事务和 FTS 不从全局活动账户隐式补范围。同步请求队列表、状态机和租约由 `09-09-account-bound-reliable-sync` 独占定义，本任务只提供其所依赖的 Room 数据库与迁移机制。

领域模型与网络 DTO 不直接作为 Room Entity。数据层用集中映射保持 `noteId`、本地 ID、USN、删除/脏状态、冲突副本、Markdown/HTML 格式和附件身份；DAO 只处理存取，Repository 负责领域操作与事务组合。

## Schema 与 FTS

- 新安装直接创建 Room schema version 1，不读取 DBFlow 文件或表。
- 唯一约束和外键保护服务端 ID、本地 ID、账户隔离和关联完整性。
- 笔记保存、删除、冲突副本与同步游标推进在明确事务中完成。
- Room FTS 只索引可搜索的规范字段；新增、修改、删除和全量重建必须得到相同结果。
- 首次公开发布前允许显式清除开发数据重建；发布后只允许版本化 migration，禁止 destructive fallback。

## 数据访问边界

Repository 接口向 ViewModel 与同步引擎暴露不可变结果或 Flow。写操作返回明确结果/错误；不得吞掉约束、磁盘或事务失败。Compose 和 WorkManager 只依赖 Repository/Use Case，不直接访问 DAO。

## 测试策略

内存 Room 测试覆盖 DAO 与事务，文件数据库测试覆盖 FTS 重建、关闭重开和并发边界。契约数据来自 API/内容任务的脱敏夹具；不创建伪 DBFlow 升级夹具。

## 回滚

公开发布前可以整体回滚 Room 切换并清理开发构建数据，但不能保留 DBFlow 与 Room 双写。首次发布后，任何回滚必须保留已发布 Room Schema 的可读性并通过正式 migration 前进修复。
