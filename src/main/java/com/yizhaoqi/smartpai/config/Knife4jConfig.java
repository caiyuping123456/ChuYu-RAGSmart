package com.yizhaoqi.smartpai.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartPai API Documentation")
                        .version("1.0.0")
                        .description("SmartPai智能派API文档")
                        .contact(new Contact()
                                .name("开发团队")
                                .url("https://github.com/itwanger/PaiSmart")
                                .email("contact@example.com")));
    }
}
