# Data Pilot

Data Pilot 是一个基于 Java 21、Spring Boot 和 Spring AI 构建的自然语言问数项目。它能够连接 MySQL 业务数据库、同步数据库结构，并通过受控的单 Agent 循环完成意图判断、Schema 检索、只读 SQL 生成、校验、执行和错误修复。

项目定位是一个小而完整、便于学习和演示的 Text-to-SQL Agent 工程。Agent 只拥有三个由应用执行的查询工具，不具备文件、网络、任意代码执行或数据库写入能力；所有成功结果都必须来自真实通过安全校验的 SQL 工具执行。

## 当前能力

- 管理和测试 MySQL 数据源连接
- AES-256-GCM 加密保存数据源密码
- 同步表、字段、主键、外键和中文注释
- 使用 Spring AI 2.0.1 调用 OpenAI-compatible 模型生成结构化 Agent 决策
- 识别 `FETCH`、`TREND`、`COMPARISON`、`RANKING`、`AMBIGUOUS` 和 `UNSUPPORTED` 意图
- 通过 `search_schema`、`get_schema` 和 `execute_readonly_sql` 三个受控工具完成查询
- 使用 JSqlParser 校验 SQL，只允许安全的只读查询
- 通过只读数据库账号、查询超时和最大行数构建多层安全边界
- 对 SQL 错误分类并提供脱敏修复提示，限制同类失败、总失败数、模型回合和总耗时
- 问题不明确时返回 `NEEDS_CLARIFICATION` 和澄清问题
- 使用 Ollama `bge-m3` 和 PostgreSQL pgvector 检索相关 Schema
- RAG 不可用时自动回退完整 Schema
- 使用 Redis 保存短期异步查询结果并广播任务与 Agent 步骤事件
- 将脱敏 Agent 轨迹持久化到 MySQL，并通过 SSE 支持实时展示和重连恢复
- 提供固定的 Text-to-SQL、Agent 轨迹与 RAG 评测数据集

## 技术栈

- Java 21
- Spring Boot 4.1.0
- Spring AI 2.0.1
- MyBatis-Plus
- Spring JDBC、HikariCP
- MySQL 8
- Redis 7
- PostgreSQL 17、pgvector
- Ollama、bge-m3
- JSqlParser 5.3
- Maven
- Docker Compose

## 项目结构

```text
data-pilot
├── data-pilot-api          HTTP API、异常处理、异步任务和 SSE
├── data-pilot-core         数据源、只读查询 Agent、状态机和 RAG 编排
├── data-pilot-datasource   数据库连接、元数据、持久化和查询执行
├── data-pilot-ai           Spring AI Agent 模型适配、离线 SQL 生成和向量存储
├── data-pilot-sql          SQL 解析与安全校验
├── data-pilot-common       通用响应和健康检查模型
├── database                演示数据库结构与固定数据
├── scripts                 开发、验证和评测脚本
└── docker-compose.middleware.yml
```

只有 `data-pilot-api` 会生成可直接运行的 Spring Boot Jar，其余模块作为普通依赖模块使用。

在线查询的唯一执行路径是 `QueryService → ReadOnlyQueryAgent`。旧的 `SqlGenerator.generate/repair` 不再参与在线查询，仅保留给现有离线评测使用。

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

启动中间件、初始化演示数据库并启动应用：

```powershell
./scripts/start-middleware.ps1
./scripts/bootstrap-dev.ps1
./scripts/verify-dev.ps1
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

## 受控只读查询 Agent

问数流程由普通 Java 代码和状态机编排。Spring AI 适配层只负责生成结构化决策，不启用框架自动工具执行；工具参数校验、执行、超时、取消、轨迹和最终结果均由应用控制。

```text
提交问题
→ CREATED
→ AGENT_ROUTING：首轮识别意图
   ├─ AMBIGUOUS   → NEEDS_CLARIFICATION
   ├─ UNSUPPORTED → FAILED / QUESTION_NOT_ANSWERABLE
   └─ 查询类意图  → AGENT_RUNNING
                      ↓
               Schema 检索 / 元数据读取 / SQL 执行
                      ↓
               根据脱敏错误修复或重新规划
                      ↓
               AGENT_FINALIZING → SUCCEEDED
```

| 意图 | 含义 |
| --- | --- |
| `FETCH` | 查询明细、汇总或单个指标 |
| `TREND` | 按时间观察指标变化 |
| `COMPARISON` | 比较对象、分组或时间范围 |
| `RANKING` | 排名、Top N 或 Bottom N |
| `AMBIGUOUS` | 查询条件不足，需要用户补充 |
| `UNSUPPORTED` | 当前只读数据源无法回答 |

Agent 只能调用以下工具，数据源由当前任务自动绑定，模型不能传入其他数据源：

| 工具 | 作用 | 主要约束 |
| --- | --- | --- |
| `search_schema(question, topK)` | 使用现有 Schema RAG 找到候选表 | 问题仍以当前任务为准，TopK 受应用限制 |
| `get_schema(tableNames)` | 读取列、主键、外键和注释 | 单次最多 6 张表，只允许已同步的真实表 |
| `execute_readonly_sql(sql)` | 校验并执行查询 | 必须先检索 Schema，并通过完整 Schema 白名单和 JSqlParser 校验 |

查询类任务只有在 `execute_readonly_sql` 至少成功一次后才能进入 `SUCCEEDED`。最终 SQL、列、行数和业务数据均以工具执行结果为准，模型不能自行声明查询成功。

### Agent 运行边界

默认限制位于 `data-pilot.ai` 配置下：

| 配置 | 默认值 | 说明 |
| --- | ---: | --- |
| `maximum-agent-turns` | 8 | 单个任务最多模型回合数 |
| `agent-timeout` | 120s | 包含模型与工具调用的总墙钟时间 |
| `maximum-total-tool-failures` | 4 | 所有工具累计失败上限 |
| `maximum-same-failure` | 3 | 同一 `toolName:errorKind` 第三次失败时终止 |
| `tool-observation-max-rows` | 20 | 返回模型的最大预览行数 |
| `tool-observation-max-chars` | 8000 | 返回模型的最大观察字符数 |
| `default-max-rows` | 100 | 默认查询最大行数 |
| `absolute-max-rows` | 200 | 客户端可请求的最大行数上限 |

单次模型请求超时为 60 秒。相同错误第一次出现时返回针对性提示，第二次额外写入 `REPLAN` 步骤并要求更换方案，第三次终止。模型调用和工具调用边界都会检查取消状态。

执行错误会归类为 `SYNTAX_ERROR`、`UNKNOWN_COLUMN`、`UNKNOWN_TABLE`、`QUERY_TIMEOUT`、`PERMISSION_DENIED`、`CONNECTION_ERROR`、`TRANSIENT_ERROR` 或 `OTHER`。权限和连接错误不能通过 SQL 修复，会直接终止；API、轨迹和模型上下文只接收脱敏类型与摘要。

### 模型配置

在 `.env` 中配置 OpenAI-compatible 模型：

```properties
DATA_PILOT_AI_ENABLED=true
DATA_PILOT_AI_PROVIDER=openai
DATA_PILOT_AI_BASE_URL=https://api.example.com
DATA_PILOT_AI_API_KEY=replace-with-local-secret
DATA_PILOT_AI_MODEL=your-model-name
```

模型需要可靠返回符合约定的结构化 JSON 决策。不支持该协议或返回无法解析的动作时，任务会以稳定细分错误码 `AI_TOOL_CALLING_UNSUPPORTED` 结束。

没有启用模型时应用仍然可以启动，但创建问数任务会返回 HTTP 503。模型密钥不会进入 Prompt、日志、Agent 轨迹或管理数据库。

同步数据源后可以提交演示查询：

```powershell
./scripts/query-async-demo.ps1 -DatasourceId 1 -Question '查询订单数量'
```

## SQL 安全机制

模型生成的 SQL 不会直接执行，必须通过以下检查：

- 只允许一条 `SELECT` 或 `WITH ... SELECT` 查询
- 禁止 INSERT、UPDATE、DELETE 和 DDL
- 使用完整的已同步 Schema 校验表名和列名，RAG 召回范围不改变授权边界
- 禁止访问未同步表、系统库、危险函数和高风险语法
- 自动限制最大返回行数
- 设置 5 秒 JDBC 查询超时
- 查询执行使用独立线程、临时连接池和只读连接
- 执行中的 `Statement` 按任务注册，以支持取消
- 数据库业务账号只授予 `SELECT` 权限

完整结果仅用于最终 API 返回并按任务存入 Redis。模型只能看到列名、行数以及最多 20 行、8 KB 的预览。应用层校验、执行时限制和数据库最小权限共同构成最终安全边界。

## Schema RAG

当数据源表数量超过 10 张时，Data Pilot 会使用 RAG 缩小 Agent 获取的 Schema 范围：

```text
Agent 调用 search_schema
→ Ollama bge-m3 生成问题向量
→ pgvector 召回 TopK 表
→ 补充直接出现的表名和一跳外键关联表
→ 最多返回 12 张候选表
→ Agent 按需调用 get_schema
```

启动 RAG 环境并初始化 50 张表的评测数据库：

```powershell
./scripts/bootstrap-rag.ps1
```

该脚本会初始化包含 5 张业务表和 45 张干扰表的 `ecommerce_rag_demo`。向量索引采用版本切换机制，新版本完整写入后才会成为活动版本；索引失败会保留上一个成功版本。

Ollama、PostgreSQL 或向量检索不可用时，问数流程会自动回退完整 Schema，SQL 授权仍始终使用全部已同步元数据。运行完整 Schema 与 RAG 的对照评测：

```powershell
./scripts/evaluate-rag.ps1
```

## 查询任务、Agent 轨迹与 SSE

问数任务接口：

```text
POST /api/datasources/{datasourceId}/queries
GET  /api/queries/{queryId}
GET  /api/queries/{queryId}/steps
GET  /api/queries/{queryId}/events
GET  /api/queries/{queryId}/result
POST /api/queries/{queryId}/cancel
GET  /api/datasources/{datasourceId}/queries
```

任务提交立即返回 HTTP 202。主状态流转为：

```text
CREATED → AGENT_ROUTING → AGENT_RUNNING → AGENT_FINALIZING → SUCCEEDED
```

同时支持 `NEEDS_CLARIFICATION`、`FAILED`、`CANCEL_REQUESTED` 和 `CANCELLED`。`NEEDS_CLARIFICATION` 是终态：结果接口返回 HTTP 200、空 `result` 和 `clarificationQuestion`，用户补充后需要提交独立的新任务。其他运行中状态的结果接口返回 HTTP 202。

`GET /api/queries/{queryId}/steps` 按 `stepNo` 返回持久化轨迹。步骤类型包括 `INTENT`、`TOOL`、`REPLAN` 和 `FINAL`，只包含工具名称、状态、安全摘要、错误分类、耗时和 token 用量，不保存模型思维链、原始 Prompt、业务结果行、凭据或未脱敏异常。

| SSE 事件 | 用途 |
| --- | --- |
| `agent-snapshot` | 建连或重连时返回全部已持久化 Agent 步骤 |
| `query-snapshot` | 建连时返回当前任务状态 |
| `agent-step` | 实时推送新 Agent 步骤 |
| `query-status` | 推送非终态状态变更 |
| `query-completed` | 推送成功、失败、取消或待澄清终态 |
| `heartbeat` | 保持连接活跃 |

前端应使用 `stepNo` 对快照和实时步骤去重。MySQL 中的任务和轨迹是恢复事实来源；事件通过 Redis Pub/Sub 广播。业务查询结果只在 Redis DB 1 中按任务 ID 保存 15 分钟，不写入管理数据库，也不会被其他任务复用。

查询取消时，SQL 执行阶段会调用 `Statement.cancel()`；模型请求阶段会在下一次受控边界检查取消状态。Flyway V8 会新增 `clarification_question` 和 `dp_agent_step`，并将升级时仍停留在旧固定阶段的任务安全标记为失败。

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

前端覆盖数据源注册、Schema 浏览、异步问数、Agent 时间线、SSE 重连去重、澄清后重新提问、结果表格、RAG 指标、任务取消和查询历史。浏览器只在 `sessionStorage` 保存恢复任务需要的 `queryId` 和 `datasourceId`，不会持久化数据库密码、模型密钥和业务查询结果。

## 构建、测试与评测

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

默认测试不会连接 Docker，也不会调用 Ollama 或外部大模型，覆盖 Agent 意图与工具协议、循环边界、取消传播、状态机、轨迹安全性、SQL 只读校验、SSE 快照和错误分类。

仓库保留 20 个固定 Text-to-SQL 回归用例，并增加模糊问题、不可回答问题、Schema 误判、SQL 修复和工具轨迹评测数据。真实模型评测需要配置指定模型并显式执行：

```powershell
./scripts/evaluate-text-to-sql.ps1 -Round baseline
./scripts/evaluate-rag.ps1
```

评测报告写入 `data-pilot-api/target/text-to-sql-evaluation/`，不会提交到 Git。

## 许可证

Data Pilot 使用 [Apache License 2.0](LICENSE) 开源。
