package com.agent4j.bilibili.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    /**
     * 转发首页请求到静态页面。
     *
     * @return 首页静态资源路径
     */
    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }
}
