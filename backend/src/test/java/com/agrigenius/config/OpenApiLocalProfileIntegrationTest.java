package com.agrigenius.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrigenius.integration.ml.MlServiceClient;
import com.agrigenius.support.PostgresTestcontainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** With the local profile the OpenAPI document and Swagger UI are served without authentication. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
class OpenApiLocalProfileIntegrationTest implements PostgresTestcontainer {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MlServiceClient mlServiceClient;

    @Test
    void openApiDocumentIsServed() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("AgriGenius API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    @Test
    void swaggerUiIsReachable() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void apiItselfStillRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/anything")).andExpect(status().isUnauthorized());
    }
}
