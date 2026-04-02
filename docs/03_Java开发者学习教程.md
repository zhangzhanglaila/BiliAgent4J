# Java 开发者学习教程

## 1. 建议阅读顺序

1. [AppProperties.java](D:/BiliAgent4J/src/main/java/com/agent4j/bilibili/config/AppProperties.java)
2. [ApiController.java](D:/BiliAgent4J/src/main/java/com/agent4j/bilibili/controller/ApiController.java)
3. [WorkspaceService.java](D:/BiliAgent4J/src/main/java/com/agent4j/bilibili/service/WorkspaceService.java)
4. [LlmWorkspaceAgentService.java](D:/BiliAgent4J/src/main/java/com/agent4j/bilibili/service/LlmWorkspaceAgentService.java)
5. 知识库相关服务

## 2. 代码分层

- `controller`：HTTP 接口入口
- `service`：业务实现
- `config`：配置和参数映射
- `model`：数据模型
- `repository`：持久化访问
- `resources/static`：前端静态资源

## 3. 你最需要理解的 Java 版新增模块

### 3.1 知识库

- [KnowledgeBaseService.java](D:/BiliAgent4J/src/main/java/com/agent4j/bilibili/service/KnowledgeBaseService.java)
- [KnowledgeSyncService.java](D:/BiliAgent4J/src/main/java/com/agent4j/bilibili/service/KnowledgeSyncService.java)
- [KnowledgeUpdateJobService.java](D:/BiliAgent4J/src/main/java/com/agent4j/bilibili/service/KnowledgeUpdateJobService.java)
- [KnowledgeFileTextExtractor.java](D:/BiliAgent4J/src/main/java/com/agent4j/bilibili/service/KnowledgeFileTextExtractor.java)

### 3.2 长期记忆

- [LongTermMemoryService.java](D:/BiliAgent4J/src/main/java/com/agent4j/bilibili/service/LongTermMemoryService.java)

### 3.3 工具链

- `retrieval`
- `web_search`
- `code_interpreter`
- `creator_briefing`
- `video_briefing`
- `hot_board_snapshot`

这些工具注册在：

- [LlmWorkspaceAgentService.java](D:/BiliAgent4J/src/main/java/com/agent4j/bilibili/service/LlmWorkspaceAgentService.java)

## 4. 常见扩展方式

### 4.1 新增一个工具

1. 在对应 `service` 中实现能力。
2. 在 `LlmWorkspaceAgentService.registeredTools()` 注册工具。
3. 在 `toolDescription()` 增加说明。
4. 在前端或模块调用路径里接入。

### 4.2 新增一个知识库来源

1. 在 `KnowledgeSyncService` 中增加数据抓取逻辑。
2. 设计文档 ID、metadata、结构化正文。
3. 保持结果结构兼容前端进度展示。

### 4.3 新增一个文件类型

1. 在 `KnowledgeFileTextExtractor` 中增加解析逻辑。
2. 在 `SUPPORTED_UPLOAD_TYPES` 中声明。
3. 增加测试。
