package com.geanbrandao.raizes.api.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuração do Swagger/OpenAPI.
 *
 * Alem dos dados da API, registra o esquema de Bearer Auth. E isso que faz aparecer
 * o botão Authorize na interface do Swagger, onde da para colar o token do login e
 * sair testando as rotas protegidas sem precisar de Postman.
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("API Raizes do Nordeste")
                .version("0.1.0")
                .description(
                    """
                    Back-end da rede de lanchonetes Raizes do Nordeste.

                    Atende varias unidades da franquia e varios canais de venda
                    (APP, TOTEM, BALCAO, PICKUP e WEB).

                    Como testar:
                    1. Chame POST /auth/login com um usuario do seed.
                    2. Clique em Authorize aqui em cima e cole o accessToken.
                    3. As rotas protegidas passam a funcionar.

                    Usuarios do seed (senha Senha@123):
                    - admin@raizes.com.br (ADMIN)
                    - gerente.recife@raizes.com.br (GERENTE)
                    - atendente.recife@raizes.com.br (ATENDENTE)
                    - cozinha.recife@raizes.com.br (COZINHA)
                    - cliente@exemplo.com (CLIENTE)
                    """.trimIndent(),
                )
                .contact(Contact().name("Gean Brandao")),
        )
        .addServersItem(Server().url("/").description("Servidor atual"))
        .addSecurityItem(SecurityRequirement().addList(ESQUEMA_BEARER))
        .components(
            Components().addSecuritySchemes(
                ESQUEMA_BEARER,
                SecurityScheme()
                    .name(ESQUEMA_BEARER)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Cole aqui o accessToken devolvido por POST /auth/login"),
            ),
        )

    companion object {
        const val ESQUEMA_BEARER = "Bearer Auth"
    }
}
