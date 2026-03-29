package com.agent4j.bilibili.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CopywritingResult {

    private String topic;
    private String style;
    private List<String> titles = new ArrayList<>();
    private List<Map<String, String>> script = new ArrayList<>();
    private String description;
    private List<String> tags = new ArrayList<>();
    private String pinnedComment;
    private String rawText = "";

    public CopywritingResult() {
    }

    public static Map<String, String> scriptItem(String section, String duration, String content) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("section", section);
        item.put("duration", duration);
        item.put("content", content);
        return item;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public List<String> getTitles() {
        return titles;
    }

    public void setTitles(List<String> titles) {
        this.titles = titles;
    }

    public List<Map<String, String>> getScript() {
        return script;
    }

    public void setScript(List<Map<String, String>> script) {
        this.script = script;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getPinnedComment() {
        return pinnedComment;
    }

    public void setPinnedComment(String pinnedComment) {
        this.pinnedComment = pinnedComment;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }
}
