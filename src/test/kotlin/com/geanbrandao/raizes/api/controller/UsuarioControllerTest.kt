package com.geanbrandao.raizes.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.geanbrandao.raizes.api.exception.ErrorCodes
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * Testes de cadastro de cliente e de operador.
 *
 * Alem do caminho feliz, cobre validação de campo (422), conflito de e-mail (409)
 * e as duas regras de permissão do cadastro de operador.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integracao")
@Transactional
class UsuarioControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private val unidadeRecife = "10000000-0000-0000-0000-000000000001"
    private val unidadeCaruaru = "10000000-0000-0000-0000-000000000002"

    // -------------------------------------------------- cadastro cliente

    @Test
    fun `cadastro de cliente devolve 201 sem expor a senha`() {
        mockMvc.perform(
            post("/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"nome":"Joana Silva","email":"joana@exemplo.com","senha":"Senha@123"}""",
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNotEmpty)
            .andExpect(jsonPath("$.perfil").value("CLIENTE"))
            .andExpect(jsonPath("$.unidadeId").doesNotExist())
            .andExpect(jsonPath("$.senha").doesNotExist())
            .andExpect(jsonPath("$.senhaHash").doesNotExist())
    }

    @Test
    fun `cadastro com email ja existente devolve 409`() {
        mockMvc.perform(
            post("/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"nome":"Outra Maria","email":"cliente@exemplo.com","senha":"Senha@123"}""",
                ),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value(ErrorCodes.EMAIL_JA_CADASTRADO))
            .andExpect(jsonPath("$.details[0].field").value("email"))
    }

    @Test
    fun `senha fraca devolve 422 explicando o que falta`() {
        mockMvc.perform(
            post("/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nome":"Joana Silva","email":"joana2@exemplo.com","senha":"12345678"}"""),
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.error").value(ErrorCodes.VALIDACAO))
            .andExpect(jsonPath("$.details[0].field").value("senha"))
    }

    @Test
    fun `email mal formatado devolve 422`() {
        mockMvc.perform(
            post("/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nome":"Joana Silva","email":"nao-e-email","senha":"Senha@123"}"""),
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.details[0].field").value("email"))
    }

    @Test
    fun `data de nascimento no futuro devolve 422`() {
        mockMvc.perform(
            post("/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nome":"Joana Silva","email":"joana3@exemplo.com",
                     "senha":"Senha@123","dataNascimento":"2099-01-01"}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.details[0].field").value("dataNascimento"))
    }

    // ------------------------------------------------- cadastro operador

    @Test
    fun `admin cadastra operador em qualquer unidade`() {
        val token = autenticar("admin@raizes.com.br")

        mockMvc.perform(
            post("/usuarios/operadores")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoOperador("novo.atendente@raizes.com.br", "ATENDENTE", unidadeCaruaru)),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.perfil").value("ATENDENTE"))
            .andExpect(jsonPath("$.unidadeId").value(unidadeCaruaru))
    }

    @Test
    fun `gerente cadastra operador na propria unidade`() {
        val token = autenticar("gerente.recife@raizes.com.br")

        mockMvc.perform(
            post("/usuarios/operadores")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoOperador("cozinha2@raizes.com.br", "COZINHA", unidadeRecife)),
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `gerente nao cadastra operador em unidade que nao e a dele`() {
        val token = autenticar("gerente.recife@raizes.com.br")

        mockMvc.perform(
            post("/usuarios/operadores")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoOperador("intruso@raizes.com.br", "ATENDENTE", unidadeCaruaru)),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value(ErrorCodes.SEM_PERMISSAO))
    }

    @Test
    fun `nao da para criar admin por esta rota`() {
        val token = autenticar("admin@raizes.com.br")

        mockMvc.perform(
            post("/usuarios/operadores")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoOperador("outro.admin@raizes.com.br", "ADMIN", unidadeRecife)),
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.details[0].field").value("perfil"))
    }

    @Test
    fun `unidade inexistente devolve 404 e nao 403`() {
        // Admin pode acessar qualquer unidade, entao o que falta aqui e o recurso,
        // nao a permissao. Confundir os dois esconde bug de rota.
        val token = autenticar("admin@raizes.com.br")

        mockMvc.perform(
            post("/usuarios/operadores")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    corpoOperador(
                        "fantasma@raizes.com.br",
                        "ATENDENTE",
                        "10000000-0000-0000-0000-0000000000ff",
                    ),
                ),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value(ErrorCodes.UNIDADE_NAO_ENCONTRADA))
    }

    @Test
    fun `perfil invalido no json devolve 400 listando os aceitos`() {
        val token = autenticar("admin@raizes.com.br")

        mockMvc.perform(
            post("/usuarios/operadores")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoOperador("x@raizes.com.br", "CHEFE_SUPREMO", unidadeRecife)),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value(ErrorCodes.REQUISICAO_INVALIDA))
            .andExpect(jsonPath("$.details[0].field").value("perfil"))
    }

    private fun corpoOperador(email: String, perfil: String, unidadeId: String) = """
        {"nome":"Operador Teste","email":"$email","senha":"Senha@123",
         "perfil":"$perfil","unidadeId":"$unidadeId"}
    """.trimIndent()

    private fun autenticar(email: String): String {
        val corpo = mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","senha":"Senha@123"}"""),
        ).andReturn().response.contentAsString
        return objectMapper.readTree(corpo).get("accessToken").asText()
    }
}
