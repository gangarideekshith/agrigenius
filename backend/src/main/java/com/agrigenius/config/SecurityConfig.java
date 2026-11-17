package com.agrigenius.config;

import com.agrigenius.common.security.ProblemDetailsSecurityHandler;
import com.agrigenius.common.web.RequestIdFilter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stateless, deny-by-default security with JWT bearer authentication (ADR-0003).
 *
 * <p>Public: register, login, health/info probes and (local profile) API docs.
 * {@code /api/farmers/**} and {@code /api/recommendations/**} require the FARMER role; everything else
 * requires a valid token.
 * 401/403 are problem+json without revealing why authentication failed.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final String[] PUBLIC_ACTUATOR = {
        "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness", "/actuator/info"
    };
    static final String[] API_DOCS = {"/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"};
    static final String[] PUBLIC_AUTH = {"/api/auth/register", "/api/auth/login"};

    /**
     * Ignores bearer tokens on the public auth endpoints, so a stale or expired token that a
     * client still sends cannot make login or registration fail with 401.
     */
    static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> request.getRequestURI().startsWith(request.getContextPath() + "/api/auth/")
                ? null
                : delegate.resolve(request);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JsonMapper jsonMapper,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled) throws Exception {
        ProblemDetailsSecurityHandler problemHandler = new ProblemDetailsSecurityHandler(jsonMapper);

        http
                .csrf(AbstractHttpConfigurer::disable)            // stateless API, no cookies
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(bearerTokenResolver())
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(PUBLIC_ACTUATOR).permitAll();
                    auth.requestMatchers("/error").permitAll();
                    auth.requestMatchers(HttpMethod.POST, PUBLIC_AUTH).permitAll();
                    if (apiDocsEnabled) {
                        auth.requestMatchers(API_DOCS).permitAll();
                    }
                    auth.requestMatchers("/api/farmers/**").hasRole("FARMER");
                    auth.requestMatchers("/api/recommendations", "/api/recommendations/**").hasRole("FARMER");
                    auth.anyRequest().authenticated();
                });
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", RequestIdFilter.HEADER));
        cors.setExposedHeaders(List.of(RequestIdFilter.HEADER));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
