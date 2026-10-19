package com.pagoclaro.support.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.AuthenticationEntryPoint;
import java.io.IOException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, exception) -> {
            response.setHeader("WWW-Authenticate", "Basic realm=\"PagoClaro\"");
            problem(response, mapper, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Se requieren credenciales válidas.");
        };
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/support/session").hasRole("SUPPORT")
                        .requestMatchers(HttpMethod.POST, "/api/support/payments/*/reconcile").hasRole("SUPPORT")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler((request, response, exception) -> problem(response, mapper,
                                HttpStatus.FORBIDDEN, "SUPPORT_ACCESS_REQUIRED", "Se requiere el rol de soporte.")))
                .httpBasic(basic -> basic.authenticationEntryPoint(unauthorized)).build();
    }

    private static void problem(HttpServletResponse response, ObjectMapper mapper, HttpStatus status,
                                String title, String detail) throws IOException {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
