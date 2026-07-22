# 知识文档入库与混合检索

## 模块职责

- `knowledge.api`：接收管理员上传、状态查询、重试和检索请求。
- `knowledge.application`：编排去重、状态机、分块、向量化和 RRF（倒数排名融合）。
- `knowledge.domain`：定义模型、规则与端口，不依赖 Spring（Java 应用框架）、数据库或云模型。
- `knowledge.infrastructure`：实现本地文件存储、百炼适配、MyBatis（持久层框架）持久化和有界任务调度。

## 处理链路

1. 接口检查扩展名和上传大小，应用服务计算 SHA-256（安全散列算法）。
2. 相同内容直接返回冲突及已有文档编号，避免重复计费和重复索引。
3. 原文件写入 `MIXAGENT_KNOWLEDGE_STORAGE_PATH`，数据库登记为 `UPLOADED`。
4. 有界线程池异步执行严格 UTF-8（统一字符编码）解析、字符窗口分块和批量向量化。
5. 片段批量替换和文档变为 `READY` 处于同一事务。
6. 检索时分别获取向量候选和关键词候选，再用 RRF 融合；任一路失败时降级返回另一路结果。

模型调用不会放进数据库事务。模型超时只消耗任务线程，不会长期占用数据库连接。当前任务队列位于单个进程内，重启时会补投 `UPLOADED` 文档，并把超时处理中任务标记为 `FAILED` 供管理员重试。

## 必需配置

```powershell
$env:MIXAGENT_AI_BASE_URL="https://你的业务空间地址/compatible-mode/v1"
$env:DASHSCOPE_API_KEY="你的本地密钥"
$env:MIXAGENT_ADMIN_USERNAME="admin"
$env:MIXAGENT_ADMIN_PASSWORD="你的本地管理密码"
```

默认向量模型为 `text-embedding-v4`，维度为 1024。修改 `MIXAGENT_EMBEDDING_DIMENSIONS` 并不能自动修改数据库结构；更换维度前必须新增 Flyway（数据库迁移工具）脚本并重建已有向量。

原文件默认保存在项目同级的 `MixAgentData/knowledge`，不在 Docker（容器技术）卷中。关闭应用或 Docker Desktop（容器桌面程序）不会删除这些文件。

## 接口

所有接口使用 HTTP Basic（HTTP 基本认证）：

- `POST /api/v1/admin/knowledge/documents`：`multipart/form-data` 上传，字段为 `file`，可选 `title`。
- `GET /api/v1/admin/knowledge/documents/{documentId}`：查询处理状态。
- `POST /api/v1/admin/knowledge/documents/{documentId}/retry`：只允许重试 `FAILED` 文档。
- `POST /api/v1/admin/knowledge/search`：请求体示例为 `{"query":"清爽、酸甜、适合新手", "topK":5}`。

手工验证命令示例：

```powershell
curl.exe -u "admin:你的本地管理密码" -F "file=@F:\资料\cocktail-notes.md" -F "title=调酒基础" http://localhost:8080/api/v1/admin/knowledge/documents

curl.exe -u "admin:你的本地管理密码" http://localhost:8080/api/v1/admin/knowledge/documents/1

curl.exe -u "admin:你的本地管理密码" -H "Content-Type: application/json" -d '{"query":"清爽酸甜的新手配方","topK":5}' http://localhost:8080/api/v1/admin/knowledge/search
```

## 故障语义

- 文件空、类型错误、标题过长或非 UTF-8：`400`。
- 内容散列重复或状态不允许重试：`409`。
- 文档不存在：`404`。
- 模型或检索基础设施不可用：检索接口返回 `503`；异步入库记录为 `FAILED`。
- 上传超过 Spring（Java 应用框架）请求上限：`413`。

服务端日志保留 `requestId`（请求标识）和异常堆栈，但不记录知识原文、密钥或模型原始响应。
