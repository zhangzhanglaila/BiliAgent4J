package com.agent4j.bilibili.cli;

import com.agent4j.bilibili.model.CopywritingResult;
import com.agent4j.bilibili.model.OperationResult;
import com.agent4j.bilibili.model.OptimizationSuggestion;
import com.agent4j.bilibili.model.TopicIdea;
import com.agent4j.bilibili.service.WorkspaceService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class CliCommandRunner implements ApplicationRunner {

    private final WorkspaceService workspaceService;

    /**
     * 创建命令行运行器。
     *
     * @param workspaceService 工作台服务
     */
    public CliCommandRunner(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    /**
     * 读取命令行参数并分发到对应子命令。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        List<String> commands = args.getNonOptionArgs();
        if (commands.isEmpty()) {
            return;
        }

        String command = commands.get(0);
        switch (command) {
            case "topic" -> runTopic(args);
            case "copy" -> runCopy(args);
            case "operate" -> runOperate(args);
            case "optimize" -> runOptimize(args);
            case "pipeline" -> runPipeline(args);
            default -> {
                return;
            }
        }

        System.exit(0);
    }

    /**
     * 执行命令行选题流程并输出结果。
     *
     * @param args 启动参数
     */
    private void runTopic(ApplicationArguments args) {
        Map<String, Object> result = workspaceService.runTopic(
                option(args, "partition", "knowledge"),
                List.of(),
                option(args, "topic", "")
        );
        System.out.println("\n=== 选题结果 ===");
        for (TopicIdea idea : readIdeas(result.get("ideas"))) {
            System.out.println("- 选题：" + idea.getTopic());
            System.out.println("  类型：" + idea.getVideoType());
            System.out.println("  理由：" + idea.getReason());
            System.out.println("  关键词：" + String.join(", ", idea.getKeywords()));
        }
    }

    /**
     * 执行命令行文案生成流程并输出结果。
     *
     * @param args 启动参数
     */
    private void runCopy(ApplicationArguments args) {
        CopywritingResult result = workspaceService.runCopy(
                option(args, "topic", "B站内容提纲"),
                option(args, "style", "干货")
        );
        System.out.println("\n=== 文案结果 ===");
        result.getTitles().forEach(title -> System.out.println("- " + title));
        result.getScript().forEach(item -> System.out.println("[" + item.get("duration") + "] " + item.get("section") + ": " + item.get("content")));
        System.out.println("简介：" + result.getDescription());
        System.out.println("标签：" + String.join(", ", result.getTags()));
        System.out.println("置顶评论：" + result.getPinnedComment());
    }

    /**
     * 执行命令行互动运营流程。
     *
     * @param args 启动参数
     */
    private void runOperate(ApplicationArguments args) {
        OperationResult result = workspaceService.runOperate(
                option(args, "bv", "BV1Demo411111"),
                args.containsOption("dry-run")
        );
        System.out.println("\n=== 运营结果 ===");
        System.out.println(result.getSummary());
    }

    /**
     * 执行命令行优化流程并输出建议。
     *
     * @param args 启动参数
     */
    private void runOptimize(ApplicationArguments args) {
        OptimizationSuggestion result = workspaceService.runOptimize(option(args, "bv", "BV1Demo411111"));
        System.out.println("\n=== 优化结果 ===");
        System.out.println("诊断：" + result.getDiagnosis());
        result.getOptimizedTitles().forEach(title -> System.out.println("- " + title));
        System.out.println("封面建议：" + result.getCoverSuggestion());
        result.getContentSuggestions().forEach(item -> System.out.println("- " + item));
    }

    /**
     * 执行命令行端到端流水线流程。
     *
     * @param args 启动参数
     */
    private void runPipeline(ApplicationArguments args) {
        Map<String, Object> result = workspaceService.runPipeline(
                option(args, "bv", "BV1Demo411111"),
                option(args, "partition", "knowledge"),
                List.of(),
                option(args, "style", "干货"),
                option(args, "topic", "")
        );
        System.out.println("\n=== 全流程结果 ===");
        System.out.println(result);
    }

    /**
     * 读取命令行选项值，不存在时返回默认值。
     *
     * @param args 启动参数
     * @param name 选项名称
     * @param fallback 默认值
     * @return 解析后的参数值
     */
    private String option(ApplicationArguments args, String name, String fallback) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        String value = values.get(0);
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * 将原始对象安全转换为选题列表。
     *
     * @param raw 原始结果对象
     * @return 选题列表
     */
    @SuppressWarnings("unchecked")
    private List<TopicIdea> readIdeas(Object raw) {
        if (raw instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof TopicIdea) {
            return list.stream().map(item -> (TopicIdea) item).collect(Collectors.toList());
        }
        return List.of();
    }
}
