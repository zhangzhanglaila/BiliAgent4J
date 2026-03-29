package com.agent4j.bilibili;

import com.agent4j.bilibili.config.AppProperties;
import com.agent4j.bilibili.config.DotenvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class Agent4jApplication {

    public static void main(String[] args) {
        DotenvLoader.load();
        SpringApplication.run(Agent4jApplication.class, args);
    }
}
