from pathlib import Path
import re

ROOT = Path(r"D:\BiliAgent4J")

JAVA_OVERRIDES = {
    ("Agent4jApplication", "main"): (
        "Spring Boot 应用的启动入口。"
        " 该方法负责将命令行参数交给 Spring 容器，并完成整个项目的启动初始化。"
    ),
    ("CliCommandRunner", "run"): (
        "读取命令行参数并分发到对应的子命令处理逻辑。"
        " 当存在可执行命令时，会调用具体的主题、文案、运营、优化或流水线流程，并在执行结束后退出进程。"
    ),
    ("CliCommandRunner", "runTopic"): (
        "执行命令行选题流程并输出格式化结果。"
        " 该方法会从参数中读取分区和种子主题，调用工作台服务生成候选选题，并将结果打印到控制台。"
    ),
    ("CliCommandRunner", "runCopy"): (
        "执行命令行文案生成流程并输出标题、脚本和说明。"
        " 该方法会根据传入主题和风格调用文案服务，便于在终端中快速预览生成结果。"
    ),
    ("CliCommandRunner", "runOperate"): (
        "执行命令行互动运营流程。"
        " 该方法会基于指定 BV 号生成评论回复、点赞、删除和关注建议，并输出汇总信息。"
    ),
    ("CliCommandRunner", "runOptimize"): (
        "执行命令行优化流程并输出优化建议。"
        " 该方法会分析目标视频的当前表现，生成标题、封面和内容优化方向。"
    ),
    ("CliCommandRunner", "runPipeline"): (
        "执行命令行端到端流水线流程。"
        " 该方法会串联选题、文案、运营和优化步骤，便于快速查看完整的工作台输出。"
    ),
    ("ApiController", "runtimeInfo"): (
        "返回当前运行模式、LLM 可用状态等运行时信息。"
        " 前端会使用该结果决定界面展示、聊天入口可用性以及模式提示文案。"
    ),
    ("ApiController", "resolveBiliLink"): (
        "校验并解析前端提交的 Bilibili 视频链接。"
        " 如果链接有效，会返回标准化的视频基础信息，供后续分析、运营和参考视频检索复用。"
    ),
    ("ApiController", "moduleCreate"): (
        "处理创作模块请求。"
        " 该接口会根据创作者画像、内容方向和想法生成选题与文案结果，并返回工作台统一结构。"
    ),
    ("ApiController", "moduleAnalyze"): (
        "处理视频分析模块请求。"
        " 该接口会解析视频、拉取市场样本并生成热度判断、优化建议和后续选题结果。"
    ),
    ("ApiController", "chat"): (
        "处理智能助手对话请求。"
        " 该接口会结合当前页面上下文与历史消息生成回复，并返回建议操作和可参考的视频链接。"
    ),
    ("ApiController", "handle"): (
        "将控制层异常统一转换为标准接口响应。"
        " 该方法会根据 LLM 错误类型推断状态码和用户可读错误信息，避免将底层异常直接暴露给前端。"
    ),
    ("VideoMetricRepository", "initialize"): (
        "初始化视频指标表结构。"
        " 当数据库中不存在目标表时，该方法会自动创建表，确保后续指标写入和历史查询可以正常执行。"
    ),
    ("VideoMetricRepository", "saveVideoMetrics"): (
        "保存一次视频指标快照。"
        " 该方法会将当前视频的主要公开指标和来源信息持久化，供后续趋势分析和优化判断使用。"
    ),
    ("VideoMetricRepository", "getHistory"): (
        "查询指定视频的历史指标记录。"
        " 返回结果按最新记录优先排序，可用于展示最近一段时间的视频表现变化。"
    ),
    ("BilibiliHttpSupport", "fetchText"): (
        "向指定地址发送请求并获取文本响应。"
        " 该方法统一补充常见浏览器请求头，降低公开接口和页面解析时的返回差异。"
    ),
    ("BilibiliHttpSupport", "fetchJson"): (
        "获取并解析指定地址返回的 JSON 数据。"
        " 如果响应内容不是有效对象结构，会直接抛出异常，避免上层逻辑继续处理无效数据。"
    ),
    ("BilibiliHttpSupport", "resolveShortLink"): (
        "解析 Bilibili 短链接的最终跳转地址。"
        " 当链接来自 b23.tv 等短链域名时，该方法会跟随重定向，拿到可继续解析的完整视频地址。"
    ),
    ("BilibiliHttpSupport", "extractInitialState"): (
        "从 Bilibili 页面 HTML 中提取 __INITIAL_STATE__ 数据块。"
        " 该结果可作为公开接口失败时的页面级回退数据源。"
    ),
    ("CopywritingService", "run"): (
        "根据主题、选题信息和风格生成完整文案结果。"
        " 该方法会先准备回退文案，再尝试调用 LLM 生成结构化结果，最后将标题、脚本、简介和标签整理为统一对象。"
    ),
    ("CopywritingService", "fallback"): (
        "构建无模型依赖的回退文案结果。"
        " 当 LLM 不可用或返回内容不完整时，会使用规则生成的标题、脚本、简介和标签保证流程可继续执行。"
    ),
    ("InteractionService", "processVideoInteractions"): (
        "分析目标视频的评论列表并生成运营动作建议。"
        " 该方法会识别垃圾评论、生成回复建议、构建点赞与关注候选，并最终汇总成统一运营结果。"
    ),
    ("InteractionService", "fetchComments"): (
        "获取目标视频的评论数据。"
        " 当真实评论接口不可用或视频信息缺失时，会自动回退到演示评论，避免整个运营流程中断。"
    ),
    ("InteractionService", "generateReply"): (
        "根据评论内容生成一条简短回复。"
        " 该方法会先给出规则回退文本，再尝试通过 LLM 输出更自然的中文回复。"
    ),
    ("LlmClientService", "invokeJson"): (
        "调用 LLM 生成 JSON 结果，并在失败时回退到默认值。"
        " 该方法适合对结构化输出要求较高但又需要保证流程稳定性的场景。"
    ),
    ("LlmClientService", "invokeJsonRequired"): (
        "调用 LLM 并强制将响应解析为 JSON。"
        " 如果模型没有返回有效 JSON，方法会抛出异常，由上层决定是否回退。"
    ),
    ("LlmClientService", "invokeTextRequired"): (
        "调用 LLM 生成文本结果，并按配置执行重试。"
        " 该方法会根据错误类别判断是否需要重试，避免瞬时网络或上游服务波动直接导致流程失败。"
    ),
    ("LlmClientService", "formatLlmError"): (
        "将底层 LLM 异常转换为用户可读的错误说明。"
        " 这样前端或上层流程可以直接展示统一、可理解的错误信息。"
    ),
    ("LlmWorkspaceAgentService", "runStructured"): (
        "执行多轮、工具驱动的结构化 Agent 流程。"
        " 方法会让模型在限定工具集合内逐步决策、记录中间观察结果，并在满足约束后产出最终 JSON。"
    ),
    ("LlmWorkspaceAgentService", "buildAgentPrompt"): (
        "构建下一轮 Agent 决策所需的提示词。"
        " 提示词中会包含任务目标、用户输入、可用工具、已用工具和历史观察结果，帮助模型做出稳定决策。"
    ),
    ("OptimizationService", "run"): (
        "围绕目标视频生成可执行的优化建议。"
        " 该方法会综合当前指标、历史记录和对标样本，输出诊断结论、标题优化、封面建议和内容方向。"
    ),
    ("OptimizationService", "fetchVideoMetrics"): (
        "获取或估算目标视频的当前指标。"
        " 方法优先读取真实视频信息构建指标对象，在失败时会生成回退数据以保持优化流程可继续运行。"
    ),
    ("ReferenceVideoService", "selectReferenceVideos"): (
        "从候选样本中筛选最值得参考的视频。"
        " 该方法会融合搜索结果、相关视频与市场样本，并按照标题相关性、来源优先级和指标表现进行排序。"
    ),
    ("ReferenceVideoService", "buildRank"): (
        "为参考视频候选项构建排序依据。"
        " 排序会综合关键词重合度、同作者关系、来源类型和公开指标，尽量把更贴近当前主题的视频放在前面。"
    ),
    ("RuntimeInfoService", "buildRuntimePayload"): (
        "构建前端所需的运行时状态信息。"
        " 返回内容包含当前模式、LLM 可用性、聊天能力和界面提示文案，便于前端统一展示。"
    ),
    ("TopicDataService", "fetchHotVideos"): (
        "抓取 Bilibili 全站热门视频样本。"
        " 返回结果会被统一转换为视频指标对象，供后续选题判断和市场快照使用。"
    ),
    ("TopicDataService", "fetchPartitionVideos"): (
        "抓取指定分区的热门或榜单视频样本。"
        " 该方法会先规范化分区，再根据分区 tid 请求榜单数据并转换为统一指标结构。"
    ),
    ("TopicDataService", "fetchPeerUpVideos"): (
        "抓取对标 UP 主的近期视频样本。"
        " 这些样本会作为同类内容对照，用于补充分区榜单之外的竞争环境信息。"
    ),
    ("TopicService", "run"): (
        "执行完整的选题生成流程。"
        " 该方法会汇总全站热门、分区样本和对标 UP 视频，再结合种子主题生成最终的候选选题结果。"
    ),
    ("TopicService", "generateTrendingTopics"): (
        "根据高分视频样本生成趋势型选题。"
        " 方法会先计算竞争度和综合得分，再从高表现视频标题中提炼关键词和选题方向。"
    ),
    ("VideoResolverService", "resolveVideoPayload"): (
        "将视频链接解析成工作台统一使用的标准载荷。"
        " 该过程会提取 BV 号、抓取视频信息并整理为分区、风格、主题和统计数据等字段。"
    ),
    ("VideoResolverService", "fetchVideoInfo"): (
        "获取目标视频的原始信息。"
        " 方法会优先使用公开接口，在失败时回退到 HTML 页面解析，尽可能保证视频信息可用。"
    ),
    ("WorkspaceService", "moduleCreate"): (
        "构建创作模块的完整结果。"
        " 该方法会根据运行模式选择 Agent、直接 LLM 或规则回退逻辑，并统一返回选题、文案和创作者画像信息。"
    ),
    ("WorkspaceService", "moduleAnalyze"): (
        "构建分析模块的完整结果。"
        " 该方法会解析当前视频、构建市场快照、判断视频表现，并输出分析结论、优化建议和参考视频。"
    ),
    ("WorkspaceService", "chat"): (
        "处理工作台智能助手对话。"
        " 该方法会优先走 Agent 对话流程，在必要时回退到直接文本生成，同时补充建议操作和参考链接。"
    ),
}

JS_OVERRIDES = {
    "$": "返回与选择器匹配的第一个元素。该函数用于减少页面中重复的单元素查询代码。",
    "$$": "以数组形式返回与选择器匹配的所有元素。该函数适合需要直接进行 forEach、map 等数组操作的场景。",
    "renderCoverMedia": "渲染卡片或预览区域使用的封面媒体结构。方法会根据封面地址是否可用决定显示加载态还是回退态。",
    "bindCoverImage": "为封面图片绑定加载成功和失败后的处理逻辑。这样页面在封面加载异常时可以自动重试，并在最终失败时切换到回退展示。",
    "initCoverMedia": "初始化封面媒体相关行为。该方法会先处理当前页面中已有的封面节点，再通过 MutationObserver 监听后续动态插入的内容。",
    "requestJson": "向后端发送 JSON POST 请求并返回业务数据。若请求失败或后端返回错误响应，会统一抛出可读错误信息。",
    "requestGetJson": "向后端发送 GET 请求并返回业务数据。该方法会统一处理接口错误与网络错误，避免调用方重复编写异常逻辑。",
    "copyResult": "渲染文案生成结果区域。输出内容包括标题、脚本、简介、标签和置顶评论，并为可复制内容自动绑定复制按钮。",
    "creatorResult": "渲染创作模块的完整结果。该结果通常会组合创作者画像、选题建议和文案内容，供用户直接查看与复制。",
    "videoResult": "渲染视频分析模块的完整结果。展示内容会根据视频表现不同，组合分析结论、优化建议、参考视频和后续选题。",
    "renderAssistant": "渲染智能助手对话面板。该方法会根据聊天记录、等待状态和参考链接，统一生成当前对话区的显示结果。",
    "loadRuntimeInfo": "从后端加载运行时信息并更新页面状态。结果会影响页面上的模式说明、智能助手可用性和按钮交互。",
    "resolveVideoLink": "解析输入框中的视频链接并刷新预览区。该方法还会维护本地缓存，避免同一个链接被重复解析。",
    "runCreatorModule": "提交创作模块请求并渲染返回结果。流程中会同步驱动按钮状态、顶部状态提示和进度展示。",
    "runAnalyzeModule": "提交分析模块请求并渲染返回结果。方法会确保视频链接已经完成解析，再请求后端生成完整分析内容。",
    "sendAssistantMessage": "发送智能助手消息并处理回复展示。该方法会维护聊天记录、等待状态、打字效果以及错误提示逻辑。",
    "typeAssistantReply": "以逐字输出的方式展示智能助手回复。这样可以在界面上模拟实时生成效果，提升对话反馈的可感知性。",
    "videoPreview": "渲染视频链接解析后的预览信息。内容包括标题、分区、UP 主、BV 号以及主要公开指标，便于用户确认分析对象。",
    "init": "初始化整个页面。该方法会建立模块状态、绑定事件、加载运行时信息，并启动封面与语音等辅助能力。",
}

RESERVED = {"class", "record", "interface", "enum", "new", "return", "throw", "catch", "if", "for", "while", "switch", "try", "do"}


def words(name: str) -> list[str]:
    if name == "$":
        return ["selector"]
    if name == "$$":
        return ["selectors"]
    return [part.lower() for part in re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", name).replace("_", " ").split() if part]


def zh_token(token: str) -> str:
    mapping = {
        "topic": "选题", "copy": "文案", "copywriting": "文案生成", "operate": "运营", "optimization": "优化", "optimize": "优化",
        "service": "服务", "controller": "控制器", "runner": "运行器", "workspace": "工作台", "video": "视频", "videos": "视频",
        "metric": "指标", "metrics": "指标", "result": "结果", "results": "结果", "action": "动作", "actions": "动作列表",
        "payload": "载荷", "data": "数据", "info": "信息", "runtime": "运行时", "market": "市场", "snapshot": "快照",
        "briefing": "简报", "module": "模块", "chat": "对话", "agent": "代理", "prompt": "提示词", "history": "历史记录",
        "comment": "评论", "comments": "评论列表", "reply": "回复", "replies": "回复列表", "fallback": "回退结果",
        "query": "查询", "queries": "查询条件", "reference": "参考", "references": "参考结果", "search": "搜索",
        "rank": "排序", "title": "标题", "titles": "标题列表", "script": "脚本", "tags": "标签", "description": "描述",
        "style": "风格", "summary": "摘要", "profile": "画像", "seed": "种子", "idea": "创意", "ideas": "创意列表",
        "partition": "分区", "label": "标签", "stats": "统计信息", "status": "状态", "message": "消息", "messages": "消息列表",
        "list": "列表", "lists": "列表", "string": "字符串", "strings": "字符串列表", "map": "映射", "json": "JSON",
        "text": "文本", "url": "地址", "links": "链接列表", "link": "链接", "cover": "封面", "image": "图片",
        "images": "图片列表", "button": "按钮", "buttons": "按钮集合", "outline": "大纲", "voice": "语音", "speech": "语音",
        "recognition": "识别", "progress": "进度", "card": "卡片", "cards": "卡片列表", "preview": "预览", "empty": "空状态",
        "pending": "等待状态", "hover": "悬停", "scene": "场景", "global": "全局", "scroll": "滚动",
        "client": "客户端", "resolver": "解析器", "field": "领域", "direction": "方向", "subject": "主体", "raw": "原始值",
        "value": "值", "node": "节点", "item": "条目", "items": "条目列表", "option": "选项", "options": "选项集合",
        "container": "容器", "forced": "强制内容", "supplier": "回调", "exception": "异常对象", "error": "错误信息",
        "interaction": "互动",
    }
    return mapping.get(token, token.upper() if token in {"api", "llm", "json", "html", "url"} else token)


def zh_words(name: str) -> str:
    return "".join(zh_token(token) for token in words(name))


def split_params(raw: str) -> list[tuple[str, str]]:
    if not raw.strip():
        return []
    parts = []
    depth = 0
    current = []
    for ch in raw:
        if ch == "," and depth == 0:
            parts.append("".join(current).strip())
            current = []
            continue
        current.append(ch)
        if ch in "<([":
            depth += 1
        elif ch in ">)]" and depth > 0:
            depth -= 1
    if current:
        parts.append("".join(current).strip())

    result = []
    for part in parts:
        cleaned = re.sub(r"@\w+(?:\([^)]*\))?\s*", "", part).strip()
        if not cleaned:
            continue
        name_match = re.search(r"([A-Za-z_$][\w$]*)\s*(?:=\s*.+)?$", cleaned)
        type_text = cleaned[:name_match.start()].strip() if name_match else ""
        name = name_match.group(1) if name_match else cleaned
        result.append((type_text, name))
    return result


def param_desc(name: str) -> str:
    text = zh_words(name)
    if text == "":  # pragma: no cover
        text = name
    if name in {"args", "argv"}:
        return "启动参数"
    if name in {"body", "payload", "data"}:
        return "请求体数据"
    if name in {"field"}:
        return "创作领域"
    if name in {"direction"}:
        return "内容方向"
    if name in {"idea"}:
        return "补充想法"
    if name in {"selector"}:
        return "CSS选择器"
    if name in {"url", "src"}:
        return "目标链接地址"
    if name in {"text", "message"}:
        return "文本内容"
    if name in {"title", "label"}:
        return "标题文本"
    if name in {"style", "variant", "type"}:
        return "处理风格或类型"
    if name in {"fallback"}:
        return "回退值"
    if name in {"resolved"}:
        return "已解析的视频数据"
    if name in {"marketSnapshot"}:
        return "市场样本快照"
    if name in {"raw"}:
        return "原始数据"
    if name in {"value"}:
        return "待处理值"
    if name in {"node"}:
        return "节点对象"
    if name in {"exception", "error"}:
        return "异常信息"
    if name in {"supplier"}:
        return "数据获取回调"
    if name in {"items"}:
        return "待处理条目列表"
    if name in {"options"}:
        return "附加配置项"
    if name in {"containerId"}:
        return "目标容器ID"
    if name in {"forced"}:
        return "强制消息内容"
    if name in {"partitionName", "partition"}:
        return "目标分区名称"
    if name in {"upIds"}:
        return "参考UP主ID列表"
    if name in {"seedTopic", "topic"}:
        return "种子选题"
    return text


def return_desc(name: str, return_type: str, class_name: str) -> str:
    if name.startswith("get") and len(name) > 3:
        return f"{zh_words(name[3:])}值"
    if name.startswith("is") and len(name) > 2:
        return "布尔结果"
    if name.startswith("has") and len(name) > 3:
        return "布尔结果"
    if return_type in {"void", ""}:
        return ""
    mapping = {
        "String": "处理后的字符串结果",
        "int": "计算结果",
        "long": "计算结果",
        "double": "计算结果",
        "boolean": "布尔结果",
    }
    if return_type in mapping:
        return mapping[return_type]
    if return_type.startswith("Map<"):
        return "结构化结果"
    if return_type.startswith("List<"):
        return "结果列表"
    if return_type.startswith("ResponseEntity<"):
        return "HTTP响应结果"
    if "ApiResponse" in return_type:
        return "标准接口响应"
    if return_type.endswith("Result") or return_type.endswith("Suggestion"):
        return "结构化结果对象"
    return "处理结果"


def generic_java_desc(class_name: str, name: str, return_type: str, params: list[tuple[str, str]]) -> str:
    if name == class_name:
        return f"创建新的{zh_words(class_name)}实例。该构造方法会接收当前类运行所需的依赖项，并完成对象的基础初始化工作。"
    if (class_name, name) in JAVA_OVERRIDES:
        return JAVA_OVERRIDES[(class_name, name)]
    if name.startswith("get") and len(name) > 3:
        field = zh_words(name[3:])
        return f"获取当前对象中的{field}。该方法用于对外暴露标准读取入口，避免调用方直接依赖内部字段实现。"
    if name.startswith("set") and len(name) > 3:
        field = zh_words(name[3:])
        return f"设置当前对象中的{field}。该方法会写入新的字段值，使后续流程始终基于最新状态继续执行。"
    if name.startswith("is") and len(name) > 2:
        field = zh_words(name[2:])
        return f"判断当前状态是否满足{field}。该结果通常会被上层流程用于分支判断、能力开关或结果筛选。"
    if name.startswith("has") and len(name) > 3:
        field = zh_words(name[3:])
        return f"判断当前对象是否包含{field}。该布尔结果可帮助调用方快速确认后续逻辑所需的数据是否已经准备完成。"
    verb_map = {
        "build": "构建", "create": "创建", "fetch": "获取", "resolve": "解析", "extract": "提取", "parse": "解析",
        "read": "读取", "pick": "选择", "find": "查找", "clean": "清理", "normalize": "规范化", "estimate": "估算",
        "score": "计算评分", "fill": "填充", "process": "处理", "classify": "判断", "format": "格式化", "map": "转换",
        "run": "执行", "load": "加载", "merge": "合并", "dedupe": "去重", "guess": "推断", "inspect": "检查",
        "serialize": "序列化", "handle": "处理", "append": "追加", "cast": "转换",
    }
    items = words(name)
    if not items:
        return "说明当前方法的用途。该方法承担当前类中的一个独立处理步骤，用于支撑完整业务流程运行。"
    action = verb_map.get(items[0], "处理")
    obj = "".join(zh_token(token) for token in items[1:]) or zh_words(class_name)
    sentence = f"{action}{obj}。"
    if params:
        param_text = "、".join(zh_words(param_name) or param_name for _, param_name in params[:3])
        sentence += f" 该方法会结合传入的{param_text}等信息完成当前步骤所需的核心处理。"
    if return_type and return_type != "void":
        sentence += " 处理完成后会返回统一结果，方便调用方直接衔接后续业务逻辑。"
    else:
        sentence += " 执行完成后不会直接返回结果，而是通过更新状态、写入数据或推动流程继续向前。"
    return sentence


def java_extra_sentence(name: str, return_type: str, params: list[tuple[str, str]]) -> str:
    if name.startswith("get") and len(name) > 3:
        return "它本身不负责复杂运算，重点是以稳定的接口形式对外暴露当前字段状态。"
    if name.startswith("set") and len(name) > 3:
        return "这样调用方可以在不关心内部实现细节的情况下，安全地完成字段更新。"
    if name.startswith("is") and len(name) > 2:
        return "返回值通常会被用于条件分支、状态判断或界面展示上的能力控制。"
    if name.startswith("has") and len(name) > 3:
        return "当上层流程需要先做前置校验时，可以直接利用该结果快速决定是否继续执行。"
    if return_type and return_type != "void":
        return "返回值通常已经过当前方法整理，可直接用于展示、组合数据或继续交给其他服务处理。"
    if params:
        return "方法本身更偏向过程型处理，重点在于按约定步骤消费输入参数并完成当前动作。"
    return "整体逻辑相对收敛，主要职责是作为当前类中的基础能力供其他方法复用。"


def js_param_type(type_text: str, name: str) -> str:
    cleaned = type_text.strip()
    if cleaned.endswith("[]") or "Array" in cleaned:
        return "Array"
    if cleaned in {"number", "Number", "int", "long", "double", "float"}:
        return "number"
    if cleaned in {"boolean", "Boolean"}:
        return "boolean"
    if cleaned in {"string", "String"}:
        return "string"
    if "Map" in cleaned or cleaned in {"Object", "JsonNode"}:
        return "Object"
    if name in {"selector", "title", "label", "text", "message", "url", "src", "key", "id", "module", "field", "direction", "idea", "partition", "style"}:
        return "string"
    if name in {"loading", "compact", "silent", "focus", "dryRun"}:
        return "boolean"
    if name in {"items", "steps", "actions", "references", "titles"}:
        return "Array"
    if name in {"options", "payload", "data", "context", "resolved", "stats"}:
        return "Object"
    return "*"


def js_return_type(name: str, params: list[tuple[str, str]], is_async: bool, body: str, expression: str | None) -> str:
    source = expression if expression is not None else body
    if is_async:
        return "Promise<*>"
    if name == "$":
        return "Element|null"
    if name == "$$":
        return "Element[]"
    if expression is not None:
        expression_text = expression.strip()
        if expression_text.startswith(("'", '"', "`")):
            return "string"
        if expression_text.startswith("["):
            return "Array"
        if expression_text.startswith("{"):
            return "Object"
        if expression_text.startswith(("true", "false")):
            return "boolean"
        if "document." in expression_text:
            return "Element|null"
        return "*"
    if re.search(r"\breturn\s+`", source):
        return "string"
    if re.search(r"\breturn\s+new\s+Promise\b", source):
        return "Promise<*>"
    if re.search(r"\breturn\s+(true|false)\b", source):
        return "boolean"
    if re.search(r"\breturn\s+document\.", source):
        return "Element|null"
    if re.search(r"\breturn\s+\[", source):
        return "Array"
    if re.search(r"\breturn\s+\{", source):
        return "Object"
    if re.search(r"\breturn\s+['\"`]", source):
        return "string"
    if re.search(r"\breturn\s+Number\b|\breturn\s+Math\.", source):
        return "number"
    if re.search(r"\breturn\s+(?!;)(.+?);", source, re.S):
        return "*"
    return ""


def generic_js_desc(name: str, params: list[tuple[str, str]], returns: str) -> str:
    if name in JS_OVERRIDES:
        return JS_OVERRIDES[name]
    if name.startswith("render"):
        target = zh_words(name[6:]) or "结果区域"
        return f"渲染{target}。该函数会根据当前输入数据拼接出界面所需的展示结构，并统一处理空态、状态文案或附加信息。"
    if name.startswith("init"):
        target = zh_words(name[4:]) or "页面能力"
        return f"初始化{target}。该函数会完成启动阶段所需的状态准备、事件绑定或观察器设置，确保后续交互可以正常工作。"
    if name.startswith("update"):
        target = zh_words(name[6:]) or "界面状态"
        return f"更新{target}。该函数负责把最新状态同步到页面或组件上，避免界面显示与内部数据出现偏差。"
    if name.startswith("set"):
        target = zh_words(name[3:]) or "状态"
        return f"设置{target}。该函数会按约定写入新的状态或样式值，使当前交互结果能够及时反映到界面。"
    if name.startswith("load"):
        target = zh_words(name[4:]) or "数据"
        return f"加载{target}。该函数会拉取当前步骤需要的数据，并在拿到结果后同步更新页面或本地状态。"
    if name.startswith("request"):
        target = zh_words(name[7:]) or "接口数据"
        return f"请求{target}。该函数统一封装网络调用过程，减少调用方重复编写请求配置和错误处理逻辑。"
    if name.startswith("bind"):
        target = zh_words(name[4:]) or "交互能力"
        return f"绑定{target}。该函数会把对应节点与事件处理逻辑连接起来，确保后续用户操作能够被正确响应。"
    if name.startswith("schedule"):
        target = zh_words(name[8:]) or "后续任务"
        return f"调度{target}。该函数用于延后或节流某个处理动作，避免高频操作导致重复计算或重复请求。"
    if name.startswith("toggle"):
        target = zh_words(name[6:]) or "状态"
        return f"切换{target}。该函数会根据当前状态选择打开或关闭对应能力，并同步更新界面反馈。"
    if name.startswith("clear"):
        target = zh_words(name[5:]) or "结果数据"
        return f"清空{target}。该函数会重置界面和内存中的相关状态，方便用户开始下一轮操作。"
    if name.startswith("format"):
        target = zh_words(name[6:]) or "输出内容"
        return f"格式化{target}。该函数会把原始值转换成更适合展示或传递的结果形式。"
    if name.startswith("clamp"):
        target = zh_words(name[5:]) or "数值范围"
        return f"约束{target}。该函数用于把输入值控制在安全范围内，避免界面或计算逻辑出现越界。"
    if name.startswith("copy"):
        target = zh_words(name[4:]) or "内容"
        return f"处理{target}复制。该函数会围绕复制动作补齐浏览器调用、结果提示和失败兜底逻辑。"
    if name.startswith("type"):
        target = zh_words(name[4:]) or "文本输出"
        return f"逐步输出{target}。该函数会按照预设节奏更新内容，营造更接近实时生成的交互反馈。"
    action = {
        "escape": "转义",
        "rich": "转换",
        "num": "格式化",
        "pct": "格式化",
        "video": "处理",
        "assistant": "处理",
    }.get(words(name)[0] if words(name) else "", "处理")
    target = zh_words(name) or "当前逻辑"
    sentence = f"{action}{target}。该函数围绕当前页面流程中的一个明确步骤展开，用于补齐数据处理、状态流转或界面输出。"
    if returns:
        sentence += " 调用方可以直接消费其返回值，而不需要再次做额外转换。"
    else:
        sentence += " 它更偏向过程型逻辑，执行完成后主要通过副作用推动后续流程继续。"
    return sentence


def js_extra_sentence(name: str, returns: str, params: list[tuple[str, str]]) -> str:
    if name in {"$", "$$"}:
        return "统一封装这一层之后，页面其他逻辑在查询节点时可以保持更简洁的写法。"
    if name.startswith("render"):
        return "这样界面层只需要传入必要数据，就可以得到结构一致、便于直接插入页面的渲染结果。"
    if name.startswith("request"):
        return "调用方因此可以把精力集中在业务处理上，而不必在每个请求点重复管理异常分支。"
    if name.startswith("run") or name.startswith("send"):
        return "函数内部通常会串联状态更新、异步调用和结果回填，是当前交互流程中的核心入口之一。"
    if returns:
        return "最终返回值会直接参与后续渲染、判断或数据传递，因此注重输出结构的稳定性。"
    if params:
        return "执行过程中会消费传入参数并联动页面状态，使当前操作能够完整闭环。"
    return "它通常作为辅助能力被其他函数复用，用于降低重复代码和提升整体可维护性。"


def format_java_doc(indent: str, desc: str, params: list[tuple[str, str]], return_text: str) -> str:
    summary = summary_sentence(desc)
    lines = [f"{indent}/**"]
    lines.append(f"{indent} * {summary}")
    for _, param_name in params:
        lines.append(f"{indent} * @param {param_name} {param_desc(param_name)}")
    if return_text:
        lines.append(f"{indent} * @return {return_text}")
    lines.append(f"{indent} */")
    return "\n".join(lines)


def format_js_doc(indent: str, desc: str, params: list[tuple[str, str]], return_text: str, return_type: str) -> str:
    summary = summary_sentence(desc)
    lines = [f"{indent}/**"]
    lines.append(f"{indent} * {summary}")
    for type_text, param_name in params:
        lines.append(f"{indent} * @param {{{js_param_type(type_text, param_name)}}} {param_name} {param_desc(param_name)}")
    if return_text:
        lines.append(f"{indent} * @returns {{{return_type or '*'}}} {return_text}")
    lines.append(f"{indent} */")
    return "\n".join(lines)


def split_sentences(text: str) -> list[str]:
    parts = [part.strip() for part in re.split(r"(?<=[。！？])\s*", text.strip()) if part.strip()]
    return parts or [text.strip()]


def summary_sentence(text: str) -> str:
    return split_sentences(text)[0]


def detect_newline(text: str) -> str:
    return "\r\n" if "\r\n" in text else "\n"


def find_java_class_name(text: str) -> str:
    match = re.search(r"\b(?:class|record|interface|enum)\s+([A-Za-z_][\w$]*)", text)
    return match.group(1) if match else "UnknownClass"


def strip_leading_modifiers(prefix: str) -> str:
    text = prefix.strip()
    while True:
        previous = text
        text = re.sub(r"^(?:public|protected|private|static|final|abstract|synchronized|default|native|strictfp)\b\s*", "", text)
        text = re.sub(r"^<[^>]+>\s*", "", text)
        if text == previous:
            break
    return text.strip()


def parse_java_signature(signature: str, class_name: str) -> dict | None:
    compact = " ".join(part.strip() for part in signature.splitlines())
    compact = re.sub(r"\s+", " ", compact).strip()
    if not compact.endswith("{"):
        return None
    compact = compact[:-1].strip()
    if "(" not in compact:
        return None
    before_paren, after_paren = compact.split("(", 1)
    if any(keyword in before_paren.split() for keyword in RESERVED):
        return None
    if any(token in before_paren for token in (" class ", " record ", " interface ", " enum ")):
        return None
    if before_paren.startswith("class ") or before_paren.startswith("record ") or before_paren.startswith("interface ") or before_paren.startswith("enum "):
        return None
    name_match = re.search(r"([A-Za-z_$][\w$]*)\s*$", before_paren)
    if not name_match:
        return None
    name = name_match.group(1)
    params_text = after_paren.rsplit(")", 1)[0]
    prefix = before_paren[:name_match.start()].strip()
    return_type = strip_leading_modifiers(prefix)
    if not return_type and name != class_name:
        return None
    return {
        "name": name,
        "params": split_params(params_text),
        "return_type": "" if name == class_name else return_type,
    }


def find_java_replacements(text: str) -> list[dict]:
    lines = text.splitlines()
    class_name = find_java_class_name(text)
    replacements = []
    i = 0
    while i < len(lines):
        stripped = lines[i].strip()
        if not re.match(r"(public|protected|private|static|final|default|synchronized|abstract)\b", stripped):
            i += 1
            continue
        j = i
        signature_lines = [lines[j]]
        while j + 1 < len(lines):
            joined = " ".join(part.strip() for part in signature_lines)
            if "{" in joined or ";" in joined:
                break
            j += 1
            signature_lines.append(lines[j])
        signature = "\n".join(signature_lines)
        info = parse_java_signature(signature, class_name)
        if not info:
            i += 1
            continue
        doc_start = i
        while doc_start > 0 and lines[doc_start - 1].strip().startswith("@"):
            doc_start -= 1
        replace_start = doc_start
        if doc_start > 0 and lines[doc_start - 1].strip() == "*/":
            comment_start = doc_start - 1
            while comment_start >= 0 and not lines[comment_start].strip().startswith("/**"):
                comment_start -= 1
            if comment_start >= 0:
                replace_start = comment_start
        indent = re.match(r"\s*", lines[doc_start]).group(0)
        desc = generic_java_desc(class_name, info["name"], info["return_type"], info["params"])
        desc = f"{desc} {java_extra_sentence(info['name'], info['return_type'], info['params'])}".strip()
        doc = format_java_doc(indent, desc, info["params"], return_desc(info["name"], info["return_type"], class_name))
        replacements.append({
            "start": replace_start,
            "end": doc_start,
            "doc": doc,
        })
        i = j + 1
    return replacements


def apply_line_replacements(lines: list[str], replacements: list[dict]) -> list[str]:
    for item in reversed(replacements):
        block = item["doc"].split("\n")
        lines[item["start"]:item["end"]] = block
    return lines


def rewrite_java_file(path: Path) -> bool:
    original = path.read_text(encoding="utf-8")
    newline = detect_newline(original)
    normalized = original.replace("\r\n", "\n")
    lines = normalized.split("\n")
    replacements = find_java_replacements(normalized)
    if not replacements:
        return False
    updated = "\n".join(apply_line_replacements(lines, replacements))
    if not updated.endswith("\n"):
        updated += "\n"
    if updated == normalized:
        return False
    path.write_text(updated.replace("\n", newline), encoding="utf-8")
    return True


def extract_js_block(text: str, start_brace: int) -> tuple[str, int]:
    depth = 0
    i = start_brace
    in_string = ""
    escape = False
    while i < len(text):
        ch = text[i]
        if in_string:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == in_string:
                in_string = ""
            i += 1
            continue
        if ch in {'"', "'", "`"}:
            in_string = ch
            i += 1
            continue
        if text.startswith("//", i):
            newline_index = text.find("\n", i)
            if newline_index == -1:
                return text[start_brace:], len(text)
            i = newline_index + 1
            continue
        if text.startswith("/*", i):
            end_index = text.find("*/", i + 2)
            if end_index == -1:
                return text[start_brace:], len(text)
            i = end_index + 2
            continue
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return text[start_brace:i + 1], i + 1
        i += 1
    return text[start_brace:], len(text)


def parse_js_params(raw: str) -> list[tuple[str, str]]:
    result = []
    for item in split_params(raw):
        type_text, name = item
        clean_name = name.lstrip("...").strip()
        clean_name = clean_name.split("=", 1)[0].strip()
        if clean_name:
            result.append((type_text, clean_name))
    return result


def find_js_replacements(text: str) -> list[dict]:
    replacements = []
    patterns = [
        re.compile(r"^(?P<indent>\s*)(?P<async>async\s+)?function\s+(?P<name>[A-Za-z_$][\w$]*)\s*\((?P<params>.*?)\)\s*\{", re.MULTILINE),
        re.compile(r"^(?P<indent>\s*)const\s+(?P<name>[A-Za-z_$][\w$]*)\s*=\s*(?P<async>async\s+)?\((?P<params>.*?)\)\s*=>", re.MULTILINE),
        re.compile(r"^(?P<indent>\s*)const\s+(?P<name>[A-Za-z_$][\w$]*)\s*=\s*(?P<async>async\s+)?(?P<single>[A-Za-z_$][\w$]*)\s*=>", re.MULTILINE),
    ]
    seen = set()
    for pattern in patterns:
        for match in pattern.finditer(text):
            start = match.start()
            if start in seen:
                continue
            seen.add(start)
            indent = match.group("indent")
            name = match.group("name")
            params_raw = match.groupdict().get("params")
            if params_raw is None:
                params_raw = match.groupdict().get("single", "")
            params = parse_js_params(params_raw)
            async_flag = bool(match.groupdict().get("async"))
            arrow_index = text.find("=>", match.start(), match.end() + 4)
            expression = None
            body = ""
            end = match.end()
            if arrow_index != -1 and arrow_index < match.end() + 4:
                tail = text[arrow_index + 2:].lstrip()
                if tail.startswith("{"):
                    brace_index = text.find("{", arrow_index)
                    body, end = extract_js_block(text, brace_index)
                else:
                    expr_start = arrow_index + 2
                    while expr_start < len(text) and text[expr_start].isspace():
                        expr_start += 1
                    expr_end = text.find("\n", expr_start)
                    if expr_end == -1:
                        expr_end = len(text)
                    expression = text[expr_start:expr_end].rstrip(" ;")
                    end = expr_end
            else:
                brace_index = text.find("{", match.start(), match.end() + 2)
                if brace_index == -1:
                    continue
                body, end = extract_js_block(text, brace_index)
            doc_end = start
            comment_start = start
            prefix = text[:start]
            if prefix.rstrip().endswith("*/"):
                scan = prefix.rstrip()
                comment_start = scan.rfind("/**")
                if comment_start != -1:
                    comment_end = prefix.rfind("*/") + 2
                    between = prefix[comment_end:start]
                    if between.strip() == "":
                        doc_end = start
                    else:
                        comment_start = start
            params_desc = params
            return_type = js_return_type(name, params_desc, async_flag, body, expression)
            return_text = ""
            if return_type:
                if return_type == "Promise<*>":
                    return_text = "异步处理完成后返回的结果，调用方可以通过 await 继续获取后续数据。"
                elif return_type == "string":
                    return_text = "整理后的字符串结果，可直接用于插入页面或继续拼接。"
                elif return_type == "boolean":
                    return_text = "表示当前判断是否成立的布尔结果。"
                elif return_type == "Element|null":
                    return_text = "匹配到的元素节点；当页面上不存在目标节点时返回 null。"
                elif return_type == "Element[]":
                    return_text = "匹配到的元素数组，便于后续直接执行批量遍历或映射处理。"
                elif return_type == "Array":
                    return_text = "整理后的数组结果，便于后续遍历、筛选或渲染。"
                elif return_type == "Object":
                    return_text = "整理后的对象结果，便于后续按字段继续读取和处理。"
                else:
                    return_text = "当前函数处理后返回的结果，供调用方直接继续使用。"
            desc = generic_js_desc(name, params_desc, return_type)
            desc = f"{desc} {js_extra_sentence(name, return_type, params_desc)}".strip()
            doc = format_js_doc(indent, desc, params_desc, return_text, return_type or "*")
            replacements.append({
                "start": comment_start,
                "end": doc_end,
                "doc": doc,
            })
    replacements.sort(key=lambda item: item["start"])
    filtered = []
    last_end = -1
    for item in replacements:
        if item["start"] < last_end:
            continue
        filtered.append(item)
        last_end = item["end"]
    return filtered


def rewrite_js_file(path: Path) -> bool:
    original = path.read_text(encoding="utf-8")
    newline = detect_newline(original)
    normalized = original.replace("\r\n", "\n")
    replacements = find_js_replacements(normalized)
    if not replacements:
        return False
    updated = normalized
    for item in reversed(replacements):
        updated = updated[:item["start"]] + item["doc"] + "\n" + updated[item["end"]:]
    if updated == normalized:
        return False
    path.write_text(updated.replace("\n", newline), encoding="utf-8")
    return True


def main() -> None:
    changed = []
    java_root = ROOT / "src/main/java"
    for path in java_root.rglob("*.java"):
        if rewrite_java_file(path):
            changed.append(path)
    js_path = ROOT / "src/main/resources/static/app.js"
    if js_path.exists() and rewrite_js_file(js_path):
        changed.append(js_path)
    for path in changed:
        print(path.relative_to(ROOT))


if __name__ == "__main__":
    main()
