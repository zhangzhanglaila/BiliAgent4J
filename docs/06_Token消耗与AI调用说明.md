# Token 消耗与 AI 调用说明

## 1. 什么时候不消耗 token

当当前没有可用运行时 LLM 配置，或者页面开关处于关闭状态时：

- 系统运行在规则模式
- 内容创作走 Java 规则链路
- 视频分析走 Java 规则链路
- 智能助手关闭
- 不消耗 token

## 2. 什么时候会消耗 token

当页面已经开启 `LLM Agent` 模式，且当前存在可用的 LLM 配置时：

- 文案生成可以调用 LangChain4j
- 聊天助手可用
- 模块级分析可走 LLM 回退链路
- 会消耗 token

## 3. 当前 AI 调用位置

Java 版里，和 AI 调用相关的核心位置是：

- `LlmClientService`
- `CopywritingService`
- `WorkspaceService.chat(...)`
- `WorkspaceService` 中的 LLM 回退逻辑

## 4. 为什么保留规则模式

因为当前项目要同时满足：

- 没 Key 也能运行
- 有 Key 时能增强

所以规则模式不是降级，而是正式运行模式之一。

## 5. 当前策略

### 文案生成

- 先有稳定 fallback
- 再让 LLM 做增强

### 视频分析

- 规则模式直接做判断
- LLM 模式可走 direct fallback

### 聊天助手

- 只有 LLM 模式开启

## 6. 成本控制建议

如果你只需要：

- 选题
- 视频解析
- 基础优化建议

且不依赖对话助手，那么可以不配置 `LLM_API_KEY`，或者仅保持页面运行在规则模式。
