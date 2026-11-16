package com.agrigenius.integration.ml;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wires the HTTP client for the ML service with explicit connect and read timeouts.
 *
 * <p>Uses {@link SimpleClientHttpRequestFactory} ({@code HttpURLConnection}) rather than the JDK
 * {@code HttpClient}: it needs no NIO selector or Unix-domain socket files, which some Windows
 * setups block under {@code AppData\Local} (see docs/engineering/DEVELOPMENT.md).
 */
@Configuration
public class MlServiceClientConfig {

    @Bean
    RestClient mlServiceRestClient(RestClient.Builder builder, MlServiceProperties properties) {
        return builder.clone()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory(properties))
                .build();
    }

    @Bean
    MlServiceClient mlServiceClient(RestClient mlServiceRestClient) {
        return new RestMlServiceClient(mlServiceRestClient);
    }

    static SimpleClientHttpRequestFactory requestFactory(MlServiceProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }
}
