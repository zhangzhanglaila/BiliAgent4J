# Token 消耗与 AI 调用说明

## 1. 两种运行模式

### 1.1 无 Key 逻辑模式

- 不调用外部 LLM
- 不消耗 token
- 智能助手关闭
- 视频分析和内容创作走 Java 规则链路

### 1.2 LLM Agent 模式

- 调用外部 LLM
- 会消耗 token
- 智能助手启用
- 模块任务会优先尝试 Agent 工具链

## 2. 哪些能力会触发 LLM

- 智能助手对话
- 模块级结构化创作
- 模块级结构化视频分析
- Agent 反思与最终结果整理

## 3. 哪些能力不一定触发 LLM

- 知识库上传
- 知识库样本浏览
- 知识库检索
- 热门榜同步
- 本地代码解释器执行

这些主要是 Java 本地能力。

## 4. 相关配置

- `LLM_API_KEY`
- `LLM_BASE_URL`
- `LLM_MODEL`
- `LLM_REASONING_EFFORT`
- `LLM_DISABLE_RESPONSE_STORAGE`
- `LANGSMITH_TRACING`

## 5. 联网搜索说明

- `web_search` 使用 `SERPAPI_API_KEY`
- 未配置时，联网搜索工具不可用或结果有限
