# Data Pilot

Data Pilot 是一个基于 Java 21、Spring Boot 和 Spring AI 构建的自然语言问数项目。它能够连接 MySQL 业务数据库，同步数据库结构，使用大模型生成 SQL，并在安全校验后以只读方式执行查询。

项目定位是一个小而完整、便于学习和演示的 Text-to-SQL 工程，当前已经覆盖数据源管理、元数据同步、SQL 安全网关、Schema RAG、异步任务和 SSE 进度推送等核心能力。

## 当前能力

- 管理和测试 MySQL 数据源连接
- AES-256-GCM 加密保存数据源密码
- 同步表、字段、主键、外键和中文注释
- 使用 Spring AI 调用 OpenAI-compatible 模型生成结构化 SQL
- 使用 JSqlParser 校验 SQL，只允许安全的只读查询
- 通过只读数据库账号、查询超时和最大行数构建多层安全边界
- 执行失败后携带脱敏错误进行有限次数的 SQL 修复
- 使用 Ollama `bge-m3` 和 PostgreSQL pgvector 检索相关 Schema
- RAG 不可用时自动回退完整 Schema
- 使用 Redis 保存短期异步查询结果并广播任务事件
- 使用 SSE 推送任务状态，并支持刷新页面后恢复进度
- 提供固定的 Text-to-SQL 与 RAG 评测数据集

## 技术栈

- Java 21
- Spring Boot 4
- Spring AI
- MyBatis-Plus
- Spring JDBC、HikariCP
- MySQL 8
- Redis 7
- PostgreSQL 17、pgvector
- Ollama、bge-m3
- JSqlParser
- Flyway
- Maven
- Docker Compose

## 项目结构

```text
data-pilot
├── data-pilot-api          HTTP API、异常处理、异步任务和 SSE
├── data-pilot-core         数据源、问数流程、状态机和 RAG 编排
├── data-pilot-datasource   数据库连接、元数据、持久化和查询执行
├── data-pilot-ai           Spring AI、SQL 生成和向量存储
├── data-pilot-sql          SQL 解析与安全校验
├── data-pilot-common       通用响应和健康检查模型
├── database                演示数据库结构与固定数据
├── scripts                 开发、验证和评测脚本
└── docker-compose.middleware.yml
```

只有 `data-pilot-api` 会生成可直接运行的 Spring Boot Jar，其余模块作为普通依赖模块使用。

## 本地环境要求

- JDK 21
- Maven 3.9+
- Docker Desktop，并启用 Docker Compose

项目自带完整的本地中间件配置：

| 服务 | 容器名称 | 本地地址 |
| --- | --- | --- |
| MySQL 8 | `data-pilot-mysql` | `127.0.0.1:3307` |
| Redis 7 | `data-pilot-redis` | `127.0.0.1:6379` |
| PostgreSQL + pgvector | `data-pilot-postgres` | `127.0.0.1:5432` |
| Ollama | `data-pilot-ollama` | `127.0.0.1:11434` |

## 快速开始

复制环境变量模板，并填写仅供本地开发使用的密码：

```powershell
Copy-Item .env.example .env
```

`.env` 已被 Git 忽略，不要把数据库密码、加密密钥或模型 API Key 提交到仓库。

启动 MySQL、Redis 和 PostgreSQL：

```powershell
./scripts/start-middleware.ps1
```

初始化管理库、演示业务库、最小权限账号和固定演示数据：

```powershell
./scripts/bootstrap-dev.ps1
./scripts/verify-dev.ps1
```

启动应用：

```powershell
./scripts/run-dev.ps1
```

默认服务地址为 `http://127.0.0.1:8080`。可以在另一个终端验证健康状态：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
./scripts/verify-dev.ps1 -CheckHttp
```

如果本地端口已经被其他容器占用，需要先停止冲突容器，再启动 Data Pilot 中间件。

## 数据源与元数据

数据源相关接口：

```text
POST /api/datasources/test-connection
POST /api/datasources
GET  /api/datasources
GET  /api/datasources/{id}
POST /api/datasources/{id}/sync
GET  /api/datasources/{id}/schema
POST /api/datasources/{id}/rag-index
```

注册并重复验证演示数据源：

```powershell
./scripts/register-demo-datasource.ps1 -VerifyIdempotency
```

数据源密码使用 AES-256-GCM 加密。`bootstrap-dev.ps1` 只会在本地密钥仍为模板占位符时生成一次随机密钥，不会覆盖已经存在的密钥。

## Text-to-SQL 流程

问数流程由普通 Java 代码和状态机编排，不依赖多 Agent 框架：

```text
接收问题
→ 准备或检索 Schema
→ 调用模型生成结构化 SQL
→ JSqlParser 安全校验
→ EXPLAIN 检查
→ 只读执行
→ 返回表格结果和解释
```

当 SQL 校验或执行失败时，系统会把脱敏后的反馈交给模型修复，最多重试两次，不会无限循环。

在 `.env` 中配置 OpenAI-compatible 模型：

```properties
DATA_PILOT_AI_ENABLED=true
DATA_PILOT_AI_PROVIDER=openai
DATA_PILOT_AI_BASE_URL=https://api.example.com
DATA_PILOT_AI_API_KEY=replace-with-local-secret
DATA_PILOT_AI_MODEL=your-model-name
```

没有启用模型时应用仍然可以启动，但创建问数任务会返回 HTTP 503。模型密钥不会进入 Prompt、日志或管理数据库。

同步数据源后可以执行演示查询：

```powershell
./scripts/query-demo.ps1 -DatasourceId 1
```

## SQL 安全机制

模型生成的 SQL 不会直接执行，必须通过以下检查：

- 只允许一条 `SELECT` 查询
- 禁止 INSERT、UPDATE、DELETE 和 DDL
- 禁止访问未同步或未授权的表
- 禁止危险函数、系统库和高风险语法
- 自动限制最大返回行数
- 设置 JDBC 查询超时
- 查询执行使用独立线程和只读连接
- 数据库业务账号只授予 `SELECT` 权限

应用层校验和数据库最小权限共同构成最终安全边界。

## Schema RAG

当数据源表数量超过 10 张时，Data Pilot 会使用 RAG 缩小发送给大模型的 Schema 范围：

```text
用户问题
→ Ollama bge-m3 生成问题向量
→ pgvector 召回 TopK 表
→ 补充直接出现的表名
→ 补充一跳外键关联表
→ 最多向 Prompt 放入 12 张表
```

启动 RAG 环境并初始化 50 张表的评测数据库：

```powershell
./scripts/bootstrap-rag.ps1
```

该脚本会启动 Data Pilot MySQL、PostgreSQL 和 Ollama，显式下载 `bge-m3`，创建 pgvector 数据库，并初始化包含 5 张业务表和 45 张干扰表的 `ecommerce_rag_demo`。

向量索引采用版本切换机制：新版本完整写入后才会成为活动版本；索引失败会保留上一个成功版本。Ollama、PostgreSQL 或向量检索不可用时，问数流程会自动回退完整 Schema，SQL 授权仍始终使用全部已同步元数据。

运行完整 Schema 与 RAG 的对照评测：

```powershell
./scripts/evaluate-rag.ps1
```

评测报告写入 `data-pilot-api/target/text-to-sql-evaluation/`，不会提交到 Git。

## 异步查询与 SSE

同步和异步问数接口：

```text
POST /api/datasources/{datasourceId}/queries
POST /api/datasources/{datasourceId}/queries/async
GET  /api/queries/{queryId}
GET  /api/queries/{queryId}/events
GET  /api/queries/{queryId}/result
POST /api/queries/{queryId}/cancel
GET  /api/datasources/{datasourceId}/queries
```

异步提交立即返回 HTTP 202。任务状态持久化在 MySQL，事件通过 Redis Pub/Sub 广播，业务查询结果只在 Redis DB 1 中按任务 ID 保存 15 分钟，不会写入管理数据库，也不会被其他任务复用。

SSE 断线重连时会先从 MySQL 返回当前任务快照，因此浏览器刷新后不依赖完整的历史事件也能恢复显示。查询取消时，SQL 执行阶段会调用 `Statement.cancel()`；模型请求阶段会在进入下一步流程前响应取消状态。

运行异步查询演示：

```powershell
./scripts/query-async-demo.ps1 -DatasourceId 1 -Question '查询订单数量'
```

## 前端项目

Vue 3 + TypeScript 前端位于独立的同级仓库 `data-pilot-web`：

```powershell
# 终端 1：启动 Java API
./scripts/run-dev.ps1

# 终端 2：启动前端
cd ..\data-pilot-web
./scripts/run-dev.ps1
```

打开 `http://127.0.0.1:5173`。开发环境下 Vite 会把 `/api` 代理到 `http://127.0.0.1:8080`。

前端覆盖数据源注册、Schema 浏览、异步问数、SSE 进度恢复、结果表格、RAG 指标、任务取消和查询历史。浏览器只在 `sessionStorage` 保存恢复任务需要的 `queryId` 和 `datasourceId`，不会持久化数据库密码、模型密钥和业务查询结果。

## 构建与测试

后端：

```powershell
mvn clean package
```

前端：

```powershell
cd ..\data-pilot-web
npm run test:run
npm run build
```

默认测试不会连接 Docker，也不会调用 Ollama 或外部大模型。真实模型评测需要通过开发脚本显式执行。

## 许可证

Data Pilot 使用 [Apache License 2.0](LICENSE) 开源。
