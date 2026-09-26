package com.geanbrandao.raizes.api.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.geanbrandao.raizes.api.exception.ErrorCodes
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * Testes do fluxo de autenticação contra o banco com o seed aplicado.
 *
 * Cobre os cenarios de autenticação e autorização exigidos no plano de testes:
 * login valido, acesso sem token (401) e acesso com perfil sem permissão (403).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integracao")
@Transactional
class AuthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    // ------------------------------------------------------------ login

    @Test
    fun `T01 - login valido devolve accessToken e dados do usuario`() {
        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"cliente@exemplo.com","senha":"Senha@123"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(jsonPath("$.usuario.perfil").value("CLIENTE"))
            // O hash de senha nunca pode aparecer em resposta nenhuma.
            .andExpect(jsonPath("$.usuario.senhaHash").doesNotExist())
    }

    @Test
    fun `login com senha errada devolve 401`() {
        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"cliente@exemplo.com","senha":"SenhaErrada1"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value(ErrorCodes.CREDENCIAIS_INVALIDAS))
    }

    @Test
    fun `login com email inexistente devolve o mesmo erro da senha errada`() {
        // Se a API diferenciasse os dois casos, daria para descobrir quem tem conta
        // na rede so testando enderecos de e-mail.
        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"naoexiste@exemplo.com","senha":"Senha@123"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value(ErrorCodes.CREDENCIAIS_INVALIDAS))
            .andExpect(jsonPath("$.message").value("E-mail ou senha invalidos."))
    }

    @Test
    fun `login sem email devolve 400 apontando o campo que faltou`() {
        // Em Kotlin, propriedade não anulavel ausente no JSON estoura no Jackson
        // antes do Bean Validation, entao vira 400 e nao 422. O importante e a
        // resposta dizer qual campo faltou.
        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"senha":"Senha@123"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value(ErrorCodes.REQUISICAO_INVALIDA))
            .andExpect(jsonPath("$.details[0].field").value("email"))
            .andExpect(jsonPath("$.details[0].issue").value("campo obrigatorio"))
    }

    @Test
    fun `login com email em branco devolve 422`() {
        // Campo presente mas vazio ja chega no Bean Validation, entao aqui e 422.
        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"","senha":"Senha@123"}"""),
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.error").value(ErrorCodes.VALIDACAO))
            .andExpect(jsonPath("$.details[0].field").value("email"))
    }

    // ------------------------------------------------- 401, 403 e formato

    @Test
    fun `T02 - acessar rota protegida sem token devolve 401 no formato padrao`() {
        mockMvc.perform(get("/usuarios/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value(ErrorCodes.NAO_AUTENTICADO))
            .andExpect(jsonPath("$.message").isNotEmpty)
            .andExpect(jsonPath("$.details").isArray)
            .andExpect(jsonPath("$.timestamp").isNotEmpty)
            .andExpect(jsonPath("$.path").value("/usuarios/me"))
            .andExpect(jsonPath("$.requestId").isNotEmpty)
    }

    @Test
    fun `token invalido nao autentica e cai em 401`() {
        mockMvc.perform(get("/usuarios/me").header("Authorization", "Bearer token.que.nao.vale"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value(ErrorCodes.NAO_AUTENTICADO))
    }

    @Test
    fun `T03 - cliente acessando rota de admin devolve 403`() {
        val token = autenticar("cliente@exemplo.com")

        mockMvc.perform(
            post("/usuarios/operadores")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nome":"Novo Atendente","email":"novo@raizes.com.br",
                     "senha":"Senha@123","perfil":"ATENDENTE",
                     "unidadeId":"10000000-0000-0000-0000-000000000001"}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value(ErrorCodes.SEM_PERMISSAO))
    }

    @Test
    fun `usuario autenticado consulta o proprio perfil`() {
        val token = autenticar("gerente.recife@raizes.com.br")

        mockMvc.perform(get("/usuarios/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("gerente.recife@raizes.com.br"))
            .andExpect(jsonPath("$.perfil").value("GERENTE"))
            .andExpect(jsonPath("$.unidadeId").value("10000000-0000-0000-0000-000000000001"))
    }

    @Test
    fun `toda resposta traz o header de request id`() {
        mockMvc.perform(get("/usuarios/me"))
            .andExpect(header().exists("X-Request-Id"))
    }

    @Test
    fun `request id enviado pelo cliente e reaproveitado`() {
        val meuId = "id-vindo-do-app-123"
        mockMvc.perform(get("/usuarios/me").header("X-Request-Id", meuId))
            .andExpect(header().string("X-Request-Id", meuId))
            .andExpect(jsonPath("$.requestId").value(meuId))
    }

    @Test
    fun `rota inexistente sem token devolve 401 e nao 404`() {
        // De proposito: a regra anyRequest().authenticated() pega rota desconhecida
        // antes do dispatcher. Se devolvesse 404, qualquer um conseguiria mapear
        // quais rotas existem na API sem ter credencial nenhuma.
        mockMvc.perform(get("/rota/que/nao/existe"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value(ErrorCodes.NAO_AUTENTICADO))
    }

    @Test
    fun `rota inexistente com token devolve 404 no formato padrao`() {
        val token = autenticar("cliente@exemplo.com")
        mockMvc.perform(
            get("/rota/que/nao/existe").header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value(ErrorCodes.NAO_ENCONTRADO))
            .andExpect(jsonPath("$.requestId").isNotEmpty)
    }

    // ---------------------------------------------------------- refresh

    @Test
    fun `refresh troca o token e invalida o antigo`() {
        val login = mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"cliente@exemplo.com","senha":"Senha@123"}"""),
        ).andReturn().response.contentAsString

        val refreshAntigo = objectMapper.readTree(login).get("refreshToken").asText()

        mockMvc.perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$refreshAntigo"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)

        // Rotação: reusar o refresh antigo tem que falhar.
        mockMvc.perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$refreshAntigo"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value(ErrorCodes.TOKEN_INVALIDO))
    }

    @Test
    fun `refresh com token inventado devolve 401`() {
        mockMvc.perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"nao-existe"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value(ErrorCodes.TOKEN_INVALIDO))
    }

    @Test
    fun `logout responde 204 e revoga o refresh`() {
        val login = mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"cliente@exemplo.com","senha":"Senha@123"}"""),
        ).andReturn().response.contentAsString

        val token = objectMapper.readTree(login).get("accessToken").asText()
        val refresh = objectMapper.readTree(login).get("refreshToken").asText()

        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        mockMvc.perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$refresh"}"""),
        ).andExpect(status().isUnauthorized)
    }

    /** Faz login com a senha padrão do seed e devolve o access token. */
    private fun autenticar(email: String): String {
        val corpo = mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","senha":"Senha@123"}"""),
        ).andReturn().response.contentAsString
        return objectMapper.readTree(corpo).get("accessToken").asText()
    }
}
