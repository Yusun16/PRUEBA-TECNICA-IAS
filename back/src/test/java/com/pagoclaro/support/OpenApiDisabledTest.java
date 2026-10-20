package com.pagoclaro.support;

import org.junit.jupiter.api.Test;
import org.springdoc.webmvc.api.OpenApiWebMvcResource;
import org.springdoc.webmvc.ui.SwaggerWelcomeWebMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:openapi-disabled;MODE=Oracle;DB_CLOSE_DELAY=-1",
        "springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
class OpenApiDisabledTest {
    @Autowired ApplicationContext context;

    @Test void documentationCanBeDisabledWithoutExposingItsControllers() {
        assertThat(context.getBeansOfType(OpenApiWebMvcResource.class)).isEmpty();
        assertThat(context.getBeansOfType(SwaggerWelcomeWebMvc.class)).isEmpty();
    }
}
