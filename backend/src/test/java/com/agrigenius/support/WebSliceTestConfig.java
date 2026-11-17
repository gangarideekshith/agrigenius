package com.agrigenius.support;

import com.agrigenius.common.error.GlobalExceptionHandler;
import com.agrigenius.common.web.RequestIdFilter;
import com.agrigenius.config.ClockConfig;
import com.agrigenius.config.CorsProperties;
import com.agrigenius.config.SecurityConfig;
import com.agrigenius.identity.config.JwtConfig;
import com.agrigenius.identity.config.JwtProperties;
import com.agrigenius.identity.config.ThrottlingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Everything a {@code @WebMvcTest} needs to run with the real security chain (JWT decoding,
 * role rules, problem+json errors, request IDs) but without a database.
 * Use together with {@code @ActiveProfiles("test")}.
 */
@TestConfiguration
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RequestIdFilter.class, JwtConfig.class, ClockConfig.class})
@EnableConfigurationProperties({CorsProperties.class, JwtProperties.class, ThrottlingProperties.class})
public class WebSliceTestConfig {
}
