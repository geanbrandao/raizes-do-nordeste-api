package com.geanbrandao.raizes.api.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
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
class OpenApiConfig(
    @Value("\${app.openapi.servidor:http://localhost:8080}") private val servidorPadrao: String,
) {

    /**
     * Descreve a API para o Swagger UI.
     *
     * Declara o esquema `Bearer Auth` para o botão **Authorize** aparecer na tela: com
     * ele da para colar o token uma vez e testar as rotas protegidas pelo navegador,
     * sem Postman.
     *
     * @return Documento OpenAPI servido em `/v3/api-docs`.
     */
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
        // Precisa ser absoluto. Com "/" sozinho, quem importa o openapi.json num cliente
        // externo resolve o caminho contra a origem do proprio cliente e as chamadas vao
        // parar em qualquer lugar menos nesta API. O "/" fica como segunda opcao, para o
        // Swagger servido atras de outro host continuar funcionando.
        .addServersItem(Server().url(servidorPadrao).description("Ambiente local"))
        .addServersItem(Server().url("/").description("Mesma origem que serviu esta pagina"))
        .addSecurityItem(SecurityRequirement().addList(ESQUEMA_BEARER))
        .components(
            Components().addSecuritySchemes(
                ESQUEMA_BEARER,
                // Sem .name(): em esquema do tipo HTTP o campo "name" nao e permitido pela
                // especificacao — ele so vale para type apiKey, onde diz em qual header a
                // chave viaja. Declarado aqui, o documento fica invalido e os validadores
                // de OpenAPI acusam propriedade inesperada.
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Cole aqui o accessToken devolvido por POST /auth/login"),
            ),
        )

    companion object {
        const val ESQUEMA_BEARER = "BearerAuth"
    }
}
