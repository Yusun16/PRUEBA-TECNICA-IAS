package com.pagoclaro.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:openapi-test;MODE=Oracle;DB_CLOSE_DELAY=-1")
@ActiveProfiles("local")
@AutoConfigureMockMvc
class OpenApiIntegrationTest {
    @Autowired MockMvc mvc;

    @Test @WithMockUser(roles = "SUPPORT")
    void specificationDocumentsOnlySupportOperationsAndBasicAuth() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("PagoClaro - API de soporte"))
                .andExpect(jsonPath("$.components.securitySchemes.supportBasic.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.supportBasic.scheme").value("basic"))
                .andExpect(jsonPath("$.security[0].supportBasic").isArray())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("demo-change-me"))))
                .andExpect(jsonPath("$.components.schemas.Result.properties.netDebitBefore").exists())
                .andExpect(jsonPath("$.components.schemas.Access.properties.username").exists())
                .andExpect(jsonPath("$.paths['/api/support/payments/{reference}/reconcile'].post.responses['500']").exists())
                .andExpect(jsonPath("$.paths.length()").value(2))
                .andExpect(jsonPath("$.paths['/api/support/session'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/support/payments/{reference}/reconcile'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/support/payments/{reference}/reconcile'].post.requestBody").doesNotExist());
    }

    @Test @WithMockUser(roles = "SUPPORT")
    void uiAndConfigurationAreAvailableWithoutPersistingCredentials() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.persistAuthorization").value(false));
    }

    @Test void documentationDoesNotRelaxApiAuthentication() throws Exception {
        for (String route : new String[]{"/swagger-ui.html", "/swagger-ui/index.html", "/swagger-ui/swagger-ui-bundle.js", "/v3/api-docs", "/v3/api-docs/swagger-config", "/v3/api-docs.yaml"}) {
            mvc.perform(get(route)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/support/session")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/support/payments/PAY-AX91/reconcile")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/support/session").with(httpBasic("n3", "demo-change-me")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("SUPPORT"));
    }

    @Test @WithMockUser(roles = "VIEWER")
    void documentationDoesNotRelaxSupportRole() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isForbidden());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isForbidden());
        mvc.perform(post("/api/support/payments/PAY-AX91/reconcile")).andExpect(status().isForbidden());
    }
}
