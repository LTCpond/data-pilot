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

    /** 创建 SQL 生成器适配器；未注入 ChatModel 时由运行时可用性检查返回稳定错误。 */
    @Bean
    public SqlGenerator sqlGenerator(
            DataPilotAiProperties properties,
            ObjectProvider<ChatModel> chatModelProvider) {
        return new SpringAiSqlGenerator(properties, chatModelProvider.getIfAvailable());
    }
}
