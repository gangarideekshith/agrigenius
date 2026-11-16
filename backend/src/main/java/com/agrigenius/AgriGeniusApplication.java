package com.agrigenius;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * AgriGenius backend entry point.
 *
 * <p>The default in-memory user is disabled: there is no login until JWT authentication
 * is implemented, and protected endpoints must return 401 rather than accept a generated password.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class AgriGeniusApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgriGeniusApplication.class, args);
    }
}
