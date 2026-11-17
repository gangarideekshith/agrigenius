package com.agrigenius.integration.ml.crop;

import com.agrigenius.integration.ml.MlServiceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for the crop recommendation model: same base URL and timeouts as the other ML
 * calls, plus the internal API key header required by the ML service (ADR-0004).
 */
@Configuration
public class CropModelClientConfig {

    public static final String API_KEY_HEADER = "X-Internal-Api-Key";

    @Bean
    RestClient cropModelRestClient(RestClient.Builder builder, MlServiceProperties properties) {
        return builder.clone()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory(properties))
                .defaultHeader(API_KEY_HEADER, properties.apiKey())
                .build();
    }

    /** HttpURLConnection-based (no NIO socket files), same timeouts as MlServiceClientConfig. */
    static SimpleClientHttpRequestFactory requestFactory(MlServiceProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }

    @Bean
    CropModelClient cropModelClient(RestClient cropModelRestClient) {
        return new RestCropModelClient(cropModelRestClient);
    }
}
