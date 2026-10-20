package com.pagoclaro.support.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "PagoClaro - API de soporte", version = "1.0.0",
        description = "Acceso y conciliación de pagos para soporte N3. Las operaciones requieren HTTP Basic y rol SUPPORT. "
                + "Conciliar puede modificar pagos y crear auditoría o movimientos compensatorios. "
                + "MANUAL_REVIEW es un resultado HTTP 200 y no implica corrección del pago."),
        security = @SecurityRequirement(name = "supportBasic"))
@SecurityScheme(name = "supportBasic", type = SecuritySchemeType.HTTP, scheme = "basic",
        description = "Credenciales de soporte. No se genera token ni sesión de servidor.")
public class OpenApiConfiguration {}
