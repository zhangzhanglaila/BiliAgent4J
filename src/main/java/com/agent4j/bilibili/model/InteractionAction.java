package com.agent4j.bilibili.model;

public class InteractionAction {

    private String action;
    private String target;
    private String message;
    private boolean dryRun = true;

    /**
     * 创建空的互动动作对象。
     */
    public InteractionAction() {
    }

    /**
     * 创建完整的互动动作对象。
     *
     * @param action 动作类型
     * @param target 目标对象
     * @param message 附带消息
     * @param dryRun 是否仅演练
     */
    public InteractionAction(String action, String target, String message, boolean dryRun) {
        this.action = action;
        this.target = target;
        this.message = message;
        this.dryRun = dryRun;
    }

    /**
     * 获取动作类型。
     *
     * @return 动作标识
     */
    public String getAction() {
        return action;
    }

    /**
     * 设置动作类型。
     *
     * @param action 动作标识
     */
    public void setAction(String action) {
        this.action = action;
    }

    /**
     * 获取动作目标。
     *
     * @return 目标标识
     */
    public String getTarget() {
        return target;
    }

    /**
     * 设置动作目标。
     *
     * @param target 目标标识
     */
    public void setTarget(String target) {
        this.target = target;
    }

    /**
     * 获取动作消息。
     *
     * @return 消息内容
     */
    public String getMessage() {
        return message;
    }

    /**
     * 设置动作消息。
     *
     * @param message 消息内容
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 判断是否为演练模式。
     *
     * @return 是否只生成动作而不执行
     */
    public boolean isDryRun() {
        return dryRun;
    }

    /**
     * 设置是否为演练模式。
     *
     * @param dryRun 是否只生成动作而不执行
     */
    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }
}
