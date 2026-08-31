package com.ltcpond.datapilot.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 创建可选的 Spring AI 适配器；未配置模型时不会影响应用其他功能启动。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DataPilotAiProperties.class)
public class AiConfiguration {

    /** 保留旧生成器供离线评测使用；在线查询由 QueryAgentModel 驱动。 */
    @Bean
    public SqlGenerator sqlGenerator(
            DataPilotAiProperties properties,
            ObjectProvider<ChatModel> chatModelProvider) {
        return new SpringAiSqlGenerator(properties, chatModelProvider.getIfAvailable());
    }

    /** 创建受控 Agent 模型适配器；工具调用由核心模块执行。 */
    @Bean
    public QueryAgentModel queryAgentModel(
            DataPilotAiProperties properties,
            ObjectProvider<ChatModel> chatModelProvider) {
        return new SpringAiQueryAgentModel(properties, chatModelProvider.getIfAvailable());
    }
}
