package com.agent4j.bilibili.model;

public class InteractionAction {

    private String action;
    private String target;
    private String message;
    private boolean dryRun = true;

    public InteractionAction() {
    }

    public InteractionAction(String action, String target, String message, boolean dryRun) {
        this.action = action;
        this.target = target;
        this.message = message;
        this.dryRun = dryRun;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }
}
