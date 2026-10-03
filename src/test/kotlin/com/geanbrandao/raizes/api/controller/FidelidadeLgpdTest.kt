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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

/**
 * Testes de fidelidade e consentimento.
 *
 * O que mais importa aqui não e o saldo, e a base legal: pontuar depende de saber
 * quem e o cliente e do que ele consome, e sem consentimento ativo isso não pode
 * acontecer. Os testes exercitam os dois lados — com e sem consentimento — e a
 * revogação no meio do caminho.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integracao")
@Transactional
class FidelidadeLgpdTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private val recife = "10000000-0000-0000-0000-000000000001"
    private val tapioca = "30000000-0000-0000-0000-000000000001"

    // ---------------------------------------------------- consentimento

    @Test
    fun `cliente do seed ja tem consentimento de fidelidade`() {
        val token = autenticar()
        mockMvc.perform(get("/consentimentos").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].finalidade").value("FIDELIDADE"))
            .andExpect(jsonPath("$[0].ativo").value(true))
    }

    @Test
    fun `registrar consentimento de perfilamento`() {
        val token = autenticar()
        mockMvc.perform(
            post("/consentimentos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"finalidade":"PERFILAMENTO","versaoDocumento":"1.0"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.finalidade").value("PERFILAMENTO"))
            .andExpect(jsonPath("$.ativo").value(true))
            .andExpect(jsonPath("$.revogadoEm").doesNotExist())
    }

    @Test
    fun `aceitar de novo o que ja vale nao duplica`() {
        val token = autenticar()
        val corpo = """{"finalidade":"MARKETING","versaoDocumento":"1.0"}"""

        val id1 = registrarConsentimento(token, corpo)
        val id2 = registrarConsentimento(token, corpo)
        assertEquals(id1, id2)
    }

    @Test
    fun `finalidade invalida devolve 422`() {
        val token = autenticar()
        mockMvc.perform(
            post("/consentimentos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"finalidade":"VENDER_PARA_TERCEIROS","versaoDocumento":"1.0"}"""),
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.details[0].field").value("finalidade"))
    }

    @Test
    fun `revogar nao apaga a linha, so marca a data`() {
        val token = autenticar()
        val id = idDoConsentimentoDeFidelidade(token)

        mockMvc.perform(
            delete("/consentimentos/$id").header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ativo").value(false))
            .andExpect(jsonPath("$.revogadoEm").isNotEmpty)

        // O registro continua no historico: e a prova de que o tratamento foi
        // legitimo enquanto durou.
        mockMvc.perform(get("/consentimentos").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].ativo").value(false))
    }

    @Test
    fun `nao da para revogar consentimento de outra pessoa`() {
        val token = autenticar()
        mockMvc.perform(
            delete("/consentimentos/00000000-0000-0000-0000-0000000000ff")
                .header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value(ErrorCodes.CONSENTIMENTO_NAO_ENCONTRADO))
    }

    @Test
    fun `operador nao acessa fidelidade nem consentimento`() {
        val gerente = autenticar("gerente.recife@raizes.com.br")
        mockMvc.perform(get("/fidelidade/saldo").header("Authorization", "Bearer $gerente"))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/consentimentos").header("Authorization", "Bearer $gerente"))
            .andExpect(status().isForbidden)
    }

    // ------------------------------------------------------- pontuação

    @Test
    fun `pedido pago credita pontos quando ha consentimento`() {
        val token = autenticar()
        val saldoAntes = saldo(token)

        val pedidoId = criarPedido(token)
        pagar(pedidoId, token)

        // Total 25.80, 1 ponto por real, truncado para baixo = 25.
        assertEquals(saldoAntes + 25, saldo(token))
    }

    @Test
    fun `sem consentimento o pedido pago nao credita nada`() {
        val token = autenticar()
        val id = idDoConsentimentoDeFidelidade(token)
        mockMvc.perform(delete("/consentimentos/$id").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)

        val saldoAntes = saldo(token)
        val pedidoId = criarPedido(token)
        pagar(pedidoId, token)

        // O pagamento funciona normalmente; so a pontuação não acontece.
        mockMvc.perform(get("/pedidos/$pedidoId").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.status").value("PAGO"))
        assertEquals(saldoAntes, saldo(token))
    }

    @Test
    fun `revogar consentimento desliga a conta de fidelidade`() {
        val token = autenticar()
        val id = idDoConsentimentoDeFidelidade(token)

        mockMvc.perform(delete("/consentimentos/$id").header("Authorization", "Bearer $token"))

        mockMvc.perform(get("/fidelidade/saldo").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ativa").value(false))
    }

    @Test
    fun `mesmo pedido nao credita pontos duas vezes`() {
        val token = autenticar()
        val pedidoId = criarPedido(token)
        pagar(pedidoId, token)
        val depoisDoPrimeiro = saldo(token)

        // Pagar de novo e recusado, mas mesmo que o fluxo fosse reexecutado o
        // extrato nao pode dobrar.
        mockMvc.perform(
            post("/pedidos/$pedidoId/pagamentos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content("""{"metodo":"PIX"}"""),
        ).andExpect(status().isConflict)

        assertEquals(depoisDoPrimeiro, saldo(token))
    }

    @Test
    fun `extrato mostra o lancamento com o saldo resultante`() {
        val token = autenticar()
        pagar(criarPedido(token), token)

        mockMvc.perform(get("/fidelidade/extrato").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItens").value(1))
            .andExpect(jsonPath("$.conteudo[0].tipo").value("ACUMULO"))
            .andExpect(jsonPath("$.conteudo[0].pontos").value(25))
            .andExpect(jsonPath("$.conteudo[0].saldoApos").value(25))
            .andExpect(jsonPath("$.conteudo[0].pedidoId").isNotEmpty)
    }

    // --------------------------------------------------------- resgate

    @Test
    fun `resgate desconta do saldo`() {
        val token = autenticar()
        pagar(criarPedido(token), token)

        mockMvc.perform(
            post("/fidelidade/resgates").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"pontos":10,"descricao":"Troca por cuscuz"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.saldoPontos").value(15))
    }

    @Test
    fun `resgate maior que o saldo devolve 409 dizendo quanto tem`() {
        val token = autenticar()

        mockMvc.perform(
            post("/fidelidade/resgates").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content("""{"pontos":500}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value(ErrorCodes.PONTOS_INSUFICIENTES))
            .andExpect(jsonPath("$.details[0].issue").value(org.hamcrest.Matchers.containsString("disponivel: 0")))
    }

    @Test
    fun `resgate de zero pontos devolve 422`() {
        val token = autenticar()
        mockMvc.perform(
            post("/fidelidade/resgates").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content("""{"pontos":0}"""),
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.details[0].field").value("pontos"))
    }

    // ------------------------------------------------------------ apoio

    private fun saldo(token: String): Int = objectMapper.readTree(
        mockMvc.perform(get("/fidelidade/saldo").header("Authorization", "Bearer $token"))
            .andReturn().response.contentAsString,
    ).get("saldoPontos").asInt()

    private fun idDoConsentimentoDeFidelidade(token: String): String = objectMapper.readTree(
        mockMvc.perform(get("/consentimentos").header("Authorization", "Bearer $token"))
            .andReturn().response.contentAsString,
    ).first { it.get("finalidade").asText() == "FIDELIDADE" }.get("id").asText()

    private fun registrarConsentimento(token: String, corpo: String): String = objectMapper.readTree(
        mockMvc.perform(
            post("/consentimentos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content(corpo),
        ).andReturn().response.contentAsString,
    ).get("id").asText()

    private fun pagar(pedidoId: String, token: String) {
        mockMvc.perform(
            post("/pedidos/$pedidoId/pagamentos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"metodo":"PIX","tokenPagamento":"tok_ok"}"""),
        ).andExpect(status().isCreated)
    }

    private fun criarPedido(token: String): String = objectMapper.readTree(
        mockMvc.perform(
            post("/pedidos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"unidadeId":"$recife","canalPedido":"TOTEM",
                        "itens":[{"produtoId":"$tapioca","quantidade":2}]}""",
                ),
        ).andExpect(status().isCreated).andReturn().response.contentAsString,
    ).get("id").asText()

    private fun autenticar(email: String = "cliente@exemplo.com"): String = objectMapper.readTree(
        mockMvc.perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","senha":"Senha@123"}"""),
        ).andReturn().response.contentAsString,
    ).get("accessToken").asText()
}
