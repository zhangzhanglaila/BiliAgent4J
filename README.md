# B站内容策划与视频分析工作台 Java 版

这是对 `D:\agent` 现有 Python 项目的独立重构版本，当前工作目录是 `D:\BiliAgent4J`，目标是：

- 不改原项目
- 保持现有产品形态和接口契约
- 用 `Java 17 + Spring Boot + LangChain4j + SQLite` 重建后端

当前项目保留了原来的三块核心能力：

1. 内容创作模块
   输入领域、方向、想法、分区、风格，输出 3 个选题和一整套文案。
2. 视频分析模块
   输入 B 站视频链接，输出解析结果、表现判断、原因分析、优化建议和参考视频。
3. 智能对话助手
   在页面开启 `LLM Agent` 模式后可用，支持自然语言提问。

## 技术栈

- `Java 17`
- `Spring Boot 3.5.0`
- `LangChain4j 1.12.2`
- `SQLite`
- 前端静态资源沿用原项目页面结构和交互

## 目录结构

```text
D:\BiliAgent4J
├─ src/main/java/com/agent4j/bilibili
│  ├─ cli
│  ├─ config
│  ├─ controller
│  ├─ model
│  ├─ repository
│  ├─ service
│  └─ web
├─ src/main/resources
│  ├─ application.yml
│  └─ static
├─ docs
├─ pom.xml
└─ README.md
```

## 环境变量

参考 [`D:\BiliAgent4J\.env.example`](D:\BiliAgent4J\.env.example)：

```env
LLM_PROVIDER=openai
LLM_API_KEY=
LLM_BASE_URL=https://zapi.aicc0.com/v1
LLM_MODEL=gpt-5.4
DB_PATH=bilibili_agents.db
REQUEST_INTERVAL=1.2
DEFAULT_PARTITION=knowledge
DEFAULT_PEER_UPS=546195,15263701,777536
```

## 启动方式

### Web

```bash
mvn spring-boot:run
```

启动后访问：

```text
http://127.0.0.1:8000
```

### 打包

```bash
mvn -DskipTests package
```

### CLI

```bash
mvn spring-boot:run "-Dspring-boot.run.arguments=topic --partition=knowledge --topic=AI 剪辑效率"
mvn spring-boot:run "-Dspring-boot.run.arguments=copy --topic=AI 剪辑第一条视频先拍什么更容易起量 --style=干货"
mvn spring-boot:run "-Dspring-boot.run.arguments=optimize --bv=BV1xx411c7mD"
```

## Web 接口

- `GET /api/runtime-info`
- `POST /api/runtime-mode`
- `POST /api/runtime-llm-config`
- `POST /api/resolve-bili-link`
- `POST /api/module-create`
- `POST /api/module-analyze`
- `POST /api/chat`
- `POST /api/topic`
- `POST /api/copy`
- `POST /api/operate`
- `POST /api/optimize`
- `POST /api/pipeline`

## 运行模式

### 规则模式

- 当前未开启运行时 LLM 配置，或页面开关处于关闭状态
- 不消耗 token
- 聊天助手关闭
- 内容创作和视频分析走 Java 规则链路

### LLM Agent 模式

- 页面运行模式开关已开启，且当前存在可用的 LLM 配置
- 文案生成、聊天和模块级分析可以调用 LangChain4j
- 会消耗 token

### 配置来源

- `.env` 里的 `LLM_API_KEY / LLM_BASE_URL / LLM_MODEL` 仍然可以作为启动时的默认配置来源
- 页面顶部现在也支持直接填写 `URL / Key / provider / model`
- 页面填写后会立即切到 `LLM Agent` 模式，不需要重启服务
- 关闭开关只会切回规则模式，不会清掉当前已保存的运行时配置

## 文档

- [项目说明](D:\BiliAgent4J\docs\01_项目说明.md)
- [完整部署文档](D:\BiliAgent4J\docs\02_完整部署文档.md)
- [Java 开发者学习教程](D:\BiliAgent4J\docs\03_Java开发者学习教程.md)
- [前端说明](D:\BiliAgent4J\docs\04_前端说明.md)
- [前端使用手册](D:\BiliAgent4J\docs\05_前端使用手册.md)
- [Token 消耗与 AI 调用说明](D:\BiliAgent4J\docs\06_Token消耗与AI调用说明.md)
- [运行模式切换说明](D:\BiliAgent4J\docs\07_运行模式切换说明.md)
- [当前项目功能实现原理](D:\BiliAgent4J\docs\08_当前项目功能实现原理.md)
