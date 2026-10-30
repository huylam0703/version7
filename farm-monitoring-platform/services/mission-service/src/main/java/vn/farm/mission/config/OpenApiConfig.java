package vn.farm.mission.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {
    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .servers(List.of(
                        new Server()
                                .url("/api/v1/mission")
                                .description("Mission Service - Local API")
                ))
                .info(new Info()
                        .title("Mission Service API")
                        .description(
                                "REST API for UAV-Assisted IoT Farm Monitoring Platform. "
                                        + "Supports mission creation, planning, execution, "
                                        + "mission status management, waypoint management, "
                                        + "sensor node collection results, "
                                        + "and mission status history."
                        )
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Huy Lam Nhat")
                                .email("huylamnhat244@gmail.com")
                                .url("https://github.com/huylam0703")
                        )
                )
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList(SECURITY_SCHEME_NAME)
                )
                .components(new Components()
                        .addSecuritySchemes(
                                SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        )
                );
    }
}
