# B站内容策划与视频分析工作台 Java 版

本项目位于 `D:\BiliAgent4J`，目标是保留现有 Java/Maven 工程结构，用 Java 重新实现并持续对齐 `D:\agent` 的最新功能、流程、界面与文档，而不是把当前项目替换成 Python 项目。

当前版本已经完成以下核心能力：

- 视频分析：解析 B 站视频链接，输出表现判断、原因分析、优化建议与参考样本。
- 内容创作：根据领域、方向、想法、分区、风格生成选题与完整文案。
- 智能助手：在 `LLM Agent` 模式下按任务自动调用工具，支持连续对话。
- 知识库管理：支持上传 `.txt/.md/.docx/.pdf`，支持热门样本同步、样本文档浏览、关键词检索。
- 长期记忆：对模块执行与助手结果做异步沉淀，供后续任务检索复用。
- 工具链：已对齐 Java 版 `retrieval`、`web_search`、`code_interpreter`、热门榜快照、创作摘要、视频摘要等能力。

## 技术栈

- `Java 17`
- `Spring Boot 3.5.0`
- `LangChain4j 1.12.2`
- `SQLite`
- `Apache POI` / `PDFBox`
- 静态前端：`index.html + app.js + style.css`

## 运行方式

### 1. 环境变量

参考 [`.env.example`](D:/BiliAgent4J/.env.example)：

```env
LLM_PROVIDER=openai
LLM_API_KEY=
LLM_BASE_URL=https://zapi.aicc0.com/v1
LLM_MODEL=gpt-5.4
LLM_REASONING_EFFORT=
LLM_DISABLE_RESPONSE_STORAGE=false
LANGCHAIN_TRACING_V2=false
LANGSMITH_TRACING=false
LANGSMITH_API_KEY=
LANGCHAIN_API_KEY=
LANGSMITH_PROJECT=bilibili-hot-rag
LANGSMITH_ENDPOINT=
LANGCHAIN_ENDPOINT=
SERPAPI_API_KEY=
VECTOR_DB_PATH=./vector_db
EMBEDDING_MODEL_NAME=BAAI/bge-small-zh-v1.5
EMBEDDING_CACHE_DIR=./model_cache
DB_PATH=bilibili_agents.db
REQUEST_INTERVAL=1.2
DEFAULT_PARTITION=knowledge
DEFAULT_PEER_UPS=546195,15263701,777536
```

### 2. 启动 Web

```bash
mvn spring-boot:run
```

启动后访问：

```text
http://127.0.0.1:8000
```

如果当前机器默认 Maven 本地仓库没有权限，可临时使用：

```powershell
$env:MAVEN_OPTS='-Dmaven.repo.local=D:\BiliAgent4J\.m2\repository'
mvn spring-boot:run
```

### 3. 测试

```bash
mvn test
```

### 4. 打包

```bash
mvn -DskipTests package
```

## 主要接口

### 运行模式

- `GET /api/runtime-info`
- `POST /api/runtime-mode`
- `POST /api/runtime-llm-config`

### 核心业务

- `POST /api/resolve-bili-link`
- `POST /api/module-create`
- `POST /api/module-analyze`
- `POST /api/chat`
- `POST /api/topic`
- `POST /api/copy`
- `POST /api/operate`
- `POST /api/optimize`
- `POST /api/pipeline`

### 知识库

- `GET /api/knowledge/status`
- `GET /api/knowledge/sample`
- `GET /api/knowledge/search`
- `POST /api/knowledge/upload`
- `POST /api/knowledge/update`
- `POST /api/knowledge/update/start`
- `GET /api/knowledge/update/{jobId}`

## 当前实现说明

- 当前知识库后端为 Java 本地 JSON 向量存储，不直接复制 Python 的 Chroma 进程方案。
- 当前嵌入实现为 Java 本地确定性向量，用于保证纯 Java 环境可运行。
- 前端已对齐 `D:\agent` 的三模块布局：视频分析、内容创作、知识库管理。
- 智能助手已接入知识检索、长期记忆、联网搜索、代码解释器等工具。

## 文档目录

- [项目说明](D:/BiliAgent4J/docs/01_项目说明.md)
- [完整部署文档](D:/BiliAgent4J/docs/02_完整部署文档.md)
- [Java 开发者学习教程](D:/BiliAgent4J/docs/03_Java开发者学习教程.md)
- [前端说明](D:/BiliAgent4J/docs/04_前端说明.md)
- [前端使用手册](D:/BiliAgent4J/docs/05_前端使用手册.md)
- [Token 消耗与 AI 调用说明](D:/BiliAgent4J/docs/06_Token消耗与AI调用说明.md)
- [运行模式切换说明](D:/BiliAgent4J/docs/07_运行模式切换说明.md)
- [当前项目功能实现原理](D:/BiliAgent4J/docs/08_当前项目功能实现原理.md)
