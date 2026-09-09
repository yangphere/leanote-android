# Room 数据基础实施计划

## 建模

- [ ] 从现有 DBFlow 模型、REST DTO 与 API 契约整理领域字段、身份、关联、USN、脏状态和内容格式。
- [ ] 定义 Room Entity、主键、唯一约束、外键、索引、FTS Entity 与类型转换器。
- [ ] 定义账户范围 Repository/DAO 接口和事务不变量，禁止隐式读取全局活动账户。

## 实现

- [ ] 创建全新安装的 Room schema version 1、DAO、映射和 Repository。
- [ ] 实现笔记/附件/标签关联、冲突副本、游标推进与同步写入事务。
- [ ] 实现 FTS 增量维护和可重复全量重建。
- [ ] 将运行时调用方切换到 Repository，随后删除 DBFlow 依赖、注解处理和死代码；不保留双写或 fallback。

## 验证

- [ ] 运行 Entity/DAO/Repository/事务/账户隔离单元与集成测试。
- [ ] 验证 Markdown/HTML、附件身份、USN、脏状态、冲突副本的存取往返。
- [ ] 验证 FTS 创建、更新、删除、重建和关闭重开后一致。
- [ ] 运行 `.\gradlew.bat testDebugUnitTest`、`.\gradlew.bat lint`、数据库设备测试与 `git diff --check`。
- [ ] 扫描产品运行时确认无 DBFlow 依赖、双写、destructive migration fallback 或无账户范围查询。

## 回滚点

- [ ] Schema/DAO、Repository/映射、调用方切换、DBFlow 删除分别形成审查点。
- [ ] 在调用方切换完成前不删除旧代码；切换完成后不保留双运行路径。
