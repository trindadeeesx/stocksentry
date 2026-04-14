package com.trindadeeesx.stocksentry.infraestructure.sms;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class SmsConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }
}