package com.geanbrandao.raizes.api.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Confere se a documentação OpenAPI esta no ar e refletindo as rotas de verdade.
 *
 * O roteiro exige que o Swagger mostre o contrato real, não um contrato escrito a
 * mão que envelhece. Como o springdoc gera a partir dos controllers, basta garantir
 * que o documento sobe, esta publico e lista as rotas que existem.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integracao")
class OpenApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `documento openapi e publico e nao exige token`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.info.title").value("API Raizes do Nordeste"))
    }

    @Test
    fun `documento lista as rotas implementadas`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paths['/auth/login']").exists())
            .andExpect(jsonPath("$.paths['/auth/refresh']").exists())
            .andExpect(jsonPath("$.paths['/auth/logout']").exists())
            .andExpect(jsonPath("$.paths['/usuarios']").exists())
            .andExpect(jsonPath("$.paths['/usuarios/me']").exists())
            .andExpect(jsonPath("$.paths['/usuarios/operadores']").exists())
    }

    @Test
    fun `esquema de bearer auth esta declarado para o botao Authorize aparecer`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.scheme").value("bearer"))
            .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.bearerFormat").value("JWT"))
            // O nome do esquema nao pode ter espaco: a especificacao exige ^[a-zA-Z0-9._-]+$,
            // e validador de OpenAPI recusa o documento inteiro por causa disso.
            .andExpect(jsonPath("$.components.securitySchemes['Bearer Auth']").doesNotExist())
            // "name" so vale em esquema do tipo apiKey. Em http, e propriedade invalida.
            .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.name").doesNotExist())
            // Servidor absoluto: sem isso, quem importa o openapi.json num cliente externo
            // resolve os caminhos contra a origem do proprio cliente.
            .andExpect(jsonPath("$.servers[0].url").value("http://localhost:8080"))
    }

    @Test
    fun `formato padrao de erro esta documentado nos schemas`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists())
            .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.error").exists())
            .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.details").exists())
            .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.requestId").exists())
    }

    @Test
    fun `login documenta os status de erro possiveis`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paths['/auth/login'].post.responses.200").exists())
            .andExpect(jsonPath("$.paths['/auth/login'].post.responses.401").exists())
            .andExpect(jsonPath("$.paths['/auth/login'].post.responses.422").exists())
    }
}
