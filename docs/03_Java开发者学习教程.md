# Java 开发者学习教程

## 1. 先理解这个项目在做什么

这个项目不是“一个聊天机器人”，而是围绕 B 站创作者工作流做了两段明确链路：

1. 发布前：选题 + 文案
2. 发布后：视频解析 + 表现判断 + 优化建议

在 Java 版里，这两段链路由 `WorkspaceService` 做总编排。

## 2. 你应该先看哪些类

建议顺序：

1. `controller/ApiController.java`
2. `service/WorkspaceService.java`
3. `service/VideoResolverService.java`
4. `service/TopicService.java`
5. `service/CopywritingService.java`
6. `service/OptimizationService.java`
7. `service/ReferenceVideoService.java`
8. `service/LlmClientService.java`

## 3. 这个项目是怎么分层的

### Controller 层

只负责：

- 收请求
- 做少量入参校验
- 把请求转给 `WorkspaceService`

### Service 层

这里是核心：

- B 站视频解析
- 热点抓取
- 规则链路
- LangChain4j LLM 调用
- 模块级编排

### Repository 层

只负责 SQLite 持久化视频历史指标。

## 4. 规则模式和 LLM 模式怎么切

切换逻辑在：

```java
AppProperties.llmEnabled()
```

只要 `.env` 里有 `LLM_API_KEY`，系统就会进入 LLM 模式。

## 5. 如果你想新增一个接口

推荐做法：

1. 先在 `WorkspaceService` 里定义编排逻辑
2. 如果有独立业务能力，再拆单独 service
3. 最后在 `ApiController` 暴露接口

不要把复杂业务直接写进 controller。

## 6. 如果你想替换模型提供方

重点改这里：

- `application.yml`
- `.env`
- `LlmClientService`

Java 版目前走的是 `OpenAI Compatible` 风格的 LangChain4j 模型调用。

## 7. 如果你想扩展规则链路

最常改的是：

- `TopicService`
- `CopywritingService`
- `OptimizationService`
- `ReferenceVideoService`

建议保持：

- 输入输出字段不变
- 前端依赖的 JSON 结构不变

## 8. 最后再看前端

前端资源在：

```text
src/main/resources/static
```

当前 Java 版沿用了原项目的页面结构和交互逻辑，所以后端接口契约必须尽量对齐。
