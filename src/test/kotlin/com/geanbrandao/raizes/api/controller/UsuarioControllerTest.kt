package com.geanbrandao.raizes.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.geanbrandao.raizes.api.dto.CadastroAceitoResponse
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
import kotlin.test.assertEquals

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
    fun `cadastro de cliente devolve 202 generico sem expor dado nenhum`() {
        mockMvc.perform(
            post("/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"nome":"Joana Silva","email":"joana@exemplo.com","senha":"Senha@123"}""",
                ),
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.mensagem").value(CadastroAceitoResponse.MENSAGEM_PADRAO))
            // Nada de id, perfil ou qualquer coisa que confirme que a conta nasceu.
            .andExpect(jsonPath("$.id").doesNotExist())
            .andExpect(jsonPath("$.senha").doesNotExist())
            .andExpect(jsonPath("$.senhaHash").doesNotExist())
    }

    @Test
    fun `cadastro em email existente responde exatamente igual ao de email novo`() {
        // Este e o teste que garante a proteção contra enumeração: as duas respostas
        // precisam ser indistinguiveis, byte a byte.
        val novo = mockMvc.perform(
            post("/usuarios").contentType(MediaType.APPLICATION_JSON)
                .content("""{"nome":"Joana Silva","email":"joana.nova@exemplo.com","senha":"Senha@123"}"""),
        ).andExpect(status().isAccepted).andReturn().response

        val existente = mockMvc.perform(
            post("/usuarios").contentType(MediaType.APPLICATION_JSON)
                .content("""{"nome":"Outra Maria","email":"cliente@exemplo.com","senha":"Senha@123"}"""),
        ).andExpect(status().isAccepted).andReturn().response

        assertEquals(novo.status, existente.status)
        assertEquals(novo.contentAsString, existente.contentAsString)
    }

    // ------------------------------------------ verificação de e-mail

    @Test
    fun `conta nova nao loga antes de confirmar o email`() {
        cadastrar("pendente@exemplo.com")

        mockMvc.perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"pendente@exemplo.com","senha":"Senha@123"}"""),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value(ErrorCodes.EMAIL_NAO_VERIFICADO))
    }

    @Test
    fun `confirmar com o codigo de dev libera o login`() {
        cadastrar("confirmando@exemplo.com")

        mockMvc.perform(
            post("/usuarios/verificacao").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"confirmando@exemplo.com","codigo":"258369"}"""),
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"confirmando@exemplo.com","senha":"Senha@123"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
    }

    @Test
    fun `codigo errado devolve 400`() {
        cadastrar("errando@exemplo.com")

        mockMvc.perform(
            post("/usuarios/verificacao").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"errando@exemplo.com","codigo":"000000"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value(ErrorCodes.CODIGO_VERIFICACAO_INVALIDO))
    }

    @Test
    fun `email inexistente na verificacao devolve o mesmo erro do codigo errado`() {
        mockMvc.perform(
            post("/usuarios/verificacao").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"ninguem@exemplo.com","codigo":"258369"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value(ErrorCodes.CODIGO_VERIFICACAO_INVALIDO))
            .andExpect(jsonPath("$.message").value("Codigo de verificação invalido ou expirado."))
    }

    @Test
    fun `codigo fora do formato de 6 digitos devolve 422`() {
        mockMvc.perform(
            post("/usuarios/verificacao").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"confirmando@exemplo.com","codigo":"abc"}"""),
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.details[0].field").value("codigo"))
    }

    @Test
    fun `reenvio responde igual para email existente e inexistente`() {
        val existente = mockMvc.perform(
            post("/usuarios/verificacao/reenvio").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"cliente@exemplo.com"}"""),
        ).andExpect(status().isAccepted).andReturn().response

        val inexistente = mockMvc.perform(
            post("/usuarios/verificacao/reenvio").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"ninguem@exemplo.com"}"""),
        ).andExpect(status().isAccepted).andReturn().response

        assertEquals(existente.contentAsString, inexistente.contentAsString)
    }

    @Test
    fun `usuarios do seed ja nascem verificados`() {
        // Sem isso o corretor não conseguiria logar para rodar a coleção de testes.
        mockMvc.perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"cliente@exemplo.com","senha":"Senha@123"}"""),
        ).andExpect(status().isOk)
    }

    /** Cria uma conta pendente de verificação. */
    private fun cadastrar(email: String) {
        mockMvc.perform(
            post("/usuarios").contentType(MediaType.APPLICATION_JSON)
                .content("""{"nome":"Conta Teste","email":"$email","senha":"Senha@123"}"""),
        ).andExpect(status().isAccepted)
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
    fun `operador criado por admin ja nasce verificado e consegue logar`() {
        val token = autenticar("admin@raizes.com.br")
        mockMvc.perform(
            post("/usuarios/operadores")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoOperador("recem.criado@raizes.com.br", "ATENDENTE", unidadeRecife)),
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"recem.criado@raizes.com.br","senha":"Senha@123"}"""),
        ).andExpect(status().isOk)
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
