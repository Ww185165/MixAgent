# ADR-0001：基础技术栈与本地基础设施

- 状态：已接受
- 日期：2026-07-21

## 背景与问题

MixAgent 面向调酒新手，需要同时保存配方等结构化数据、知识文档及其文本向量，并需要为后续 Agent 工作流提供缓存、可观测性和可复现测试环境。项目现阶段以模块化单体为主，不提前拆分微服务。

## 目标与约束

- 使用 JDK 17 和 Spring Boot 4.1.0。
- 项目重点偏向 Agent、RAG 和推荐评估，减少普通增删改查样板代码。
- 本地开发环境可以通过 Docker 复现，但必须由开发者明确启动。
- 模型供应商和向量维度尚未确定，数据库设计不能提前锁死具体模型。
- 不引入尚无数据证明需要的消息队列、独立向量数据库或搜索集群。

## 备选方案

### 数据访问

1. 原生 MyBatis：SQL 完全显式，但普通增删改查代码较多。
2. MyBatis-Plus：基础查询使用 BaseMapper，复杂查询仍可编写自定义 SQL。
3. Spring Data JPA：对象映射能力完整，但复杂 PostgreSQL 和向量查询需要较多绕行。

### 数据与检索

1. PostgreSQL + pgvector：一个实例同时承担事务数据和初期向量检索。
2. MySQL + 独立向量数据库：组件更多，当前规模缺少必要性。
3. PostgreSQL + Elasticsearch：混合检索能力更强，但运维和一致性成本更高。

## 最终决策

- 使用 MyBatis-Plus 的 Spring Boot 4 启动依赖。
- MyBatis-Plus 仅作为基础设施层实现细节；领域层和应用层不直接依赖 BaseMapper、IService 或 QueryWrapper。
- 普通单表操作使用 BaseMapper，复杂候选召回、统计和混合检索使用显式 SQL。
- 使用 PostgreSQL 保存结构化业务数据，使用 pgvector 保存知识片段向量。
- 使用 Redis 承担后续缓存和短期状态，当前阶段不预设具体缓存场景。
- 使用 Flyway 管理扩展、表和索引演进。
- 使用 Docker Compose 描述本地 PostgreSQL 和 Redis，由开发者手动执行启动命令。
- 使用 Testcontainers 验证真实 PostgreSQL 扩展和迁移脚本。

## 负面影响

- PostgreSQL 内置全文检索对连续中文文本分词有限，需要通过规范化名称、pg_trgm、向量召回和后续实测补足。
- 业务库与向量检索共享实例，未来高负载时可能相互争用资源。
- MyBatis-Plus 的条件构造器如果越过基础设施边界，会导致领域逻辑与数据库框架耦合。

## 风险与缓解措施

- 在模型确定前，knowledge_chunk.embedding 使用不固定维度的 vector 字段，并暂缓创建 HNSW 索引。
- 通过 Repository 端口隔离数据库实现，禁止应用服务直接拼接 QueryWrapper。
- 对复杂 SQL 编写真实 PostgreSQL 集成测试，并记录查询计划和索引效果。
- 数据库连接、密码和端口均通过环境变量配置，不提交真实凭据。

## 验证方式

- Testcontainers 启动带 pgvector 的 PostgreSQL，执行全部 Flyway 迁移。
- 验证 vector、pg_trgm 扩展和五张基础表存在。
- 模型确定后补充固定维度向量索引、召回正确性和性能测试。

## 未来演进条件

只有当中文关键词召回质量、数据库资源竞争或检索 P95 延迟经过评测不达标时，才将关键词检索迁移到 Elasticsearch/OpenSearch，或将向量检索迁移到独立服务。迁移时保持应用层检索接口不变。
