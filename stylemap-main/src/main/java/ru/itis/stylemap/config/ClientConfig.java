package ru.itis.stylemap.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ClientConfig {

    @Value("${app.client.currency-api.url}")
    private String currencyApiUrl;

    @Bean
    public RestClient restCurrencyClient(){
        return RestClient.builder()
                .baseUrl(currencyApiUrl)
                .build();
    }
}
