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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes da trilha de auditoria. E o cenario T14 do plano de testes.
 *
 * O roteiro pede ao menos um teste evidenciando que uma ação sensivel gera registro.
 * Aqui são cobertas as principais: criação e cancelamento de pedido, mudança de
 * status, movimentação de estoque e resgate de pontos — mais a regra de que so a
 * matriz le a trilha.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integracao")
@Transactional
class AuditoriaControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private val recife = "10000000-0000-0000-0000-000000000001"
    private val tapioca = "30000000-0000-0000-0000-000000000001"

    // --------------------------------------------------------- acesso

    @Test
    fun `admin consulta a trilha`() {
        mockMvc.perform(get("/auditoria").header("Authorization", "Bearer ${admin()}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.conteudo").isArray)
    }

    @Test
    fun `gerente nao le a trilha`() {
        val gerente = autenticar("gerente.recife@raizes.com.br")
        mockMvc.perform(get("/auditoria").header("Authorization", "Bearer $gerente"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value(ErrorCodes.SEM_PERMISSAO))
    }

    @Test
    fun `cliente nao le a trilha`() {
        mockMvc.perform(get("/auditoria").header("Authorization", "Bearer ${cliente()}"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `trilha sem token devolve 401`() {
        mockMvc.perform(get("/auditoria")).andExpect(status().isUnauthorized)
    }

    // ------------------------------------------------- ações rastreadas

    @Test
    fun `T14 - criar pedido gera registro na trilha`() {
        val token = cliente()
        val antes = totalDeRegistros("PEDIDO")

        val pedidoId = criarPedido(token)

        assertEquals(antes + 1, totalDeRegistros("PEDIDO"))

        val registro = primeiroRegistro("PEDIDO")
        assertEquals("PEDIDO_CRIADO", registro.get("acao").asText())
        assertEquals(pedidoId, registro.get("entidadeId").asText())
        assertTrue(registro.get("dadosNovos").asText().contains("canalPedido"))
        assertTrue(registro.get("usuarioId").asText().isNotBlank())
    }

    @Test
    fun `mudanca de status registra o antes e o depois`() {
        val pedidoId = criarPedido(cliente())
        val gerente = autenticar("gerente.recife@raizes.com.br")

        mockMvc.perform(
            patch("/pedidos/$pedidoId/status").header("Authorization", "Bearer $gerente")
                .contentType(MediaType.APPLICATION_JSON).content("""{"status":"PAGO"}"""),
        ).andExpect(status().isOk)

        val registro = primeiroRegistro("PEDIDO")
        assertEquals("STATUS_ALTERADO", registro.get("acao").asText())
        assertTrue(registro.get("dadosAnteriores").asText().contains("AGUARDANDO_PAGAMENTO"))
        assertTrue(registro.get("dadosNovos").asText().contains("PAGO"))
    }

    @Test
    fun `cancelamento fica rastreado`() {
        val token = cliente()
        val pedidoId = criarPedido(token)

        mockMvc.perform(
            post("/pedidos/$pedidoId/cancelamento").header("Authorization", "Bearer $token"),
        ).andExpect(status().isOk)

        assertEquals("PEDIDO_CANCELADO", primeiroRegistro("PEDIDO").get("acao").asText())
    }

    @Test
    fun `movimentacao de estoque fica rastreada com o saldo antes e depois`() {
        val gerente = autenticar("gerente.recife@raizes.com.br")

        mockMvc.perform(
            post("/unidades/$recife/estoque/movimentacoes")
                .header("Authorization", "Bearer $gerente")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"produtoId":"$tapioca","tipo":"ENTRADA","quantidade":10}"""),
        ).andExpect(status().isCreated)

        val registro = primeiroRegistro("ESTOQUE")
        assertEquals("ESTOQUE_MOVIMENTADO", registro.get("acao").asText())
        assertTrue(registro.get("dadosAnteriores").asText().contains("50"))
        assertTrue(registro.get("dadosNovos").asText().contains("60"))
    }

    @Test
    fun `pagamento e acumulo de pontos ficam rastreados`() {
        val token = cliente()
        val pedidoId = criarPedido(token)

        mockMvc.perform(
            post("/pedidos/$pedidoId/pagamentos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"metodo":"PIX","tokenPagamento":"tok_ok"}"""),
        ).andExpect(status().isCreated)

        assertEquals("PAGAMENTO_SOLICITADO", primeiroRegistro("PAGAMENTO").get("acao").asText())
        assertEquals("PONTOS_ACUMULADOS", primeiroRegistro("FIDELIDADE").get("acao").asText())
    }

    @Test
    fun `resgate de pontos fica rastreado`() {
        val token = cliente()
        val pedidoId = criarPedido(token)
        mockMvc.perform(
            post("/pedidos/$pedidoId/pagamentos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"metodo":"PIX","tokenPagamento":"tok_ok"}"""),
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/fidelidade/resgates").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content("""{"pontos":5}"""),
        ).andExpect(status().isOk)

        assertEquals("PONTOS_RESGATADOS", primeiroRegistro("FIDELIDADE").get("acao").asText())
    }

    @Test
    fun `revogacao de consentimento fica rastreada`() {
        val token = cliente()
        val consentimentos = objectMapper.readTree(
            mockMvc.perform(get("/consentimentos").header("Authorization", "Bearer $token"))
                .andReturn().response.contentAsString,
        )
        val id = consentimentos.first().get("id").asText()

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/consentimentos/$id").header("Authorization", "Bearer $token"),
        ).andExpect(status().isOk)

        assertEquals("CONSENTIMENTO_REVOGADO", primeiroRegistro("CONSENTIMENTO").get("acao").asText())
    }

    @Test
    fun `filtro por entidade separa as acoes`() {
        criarPedido(cliente())

        mockMvc.perform(
            get("/auditoria?entidade=pedido").header("Authorization", "Bearer ${admin()}"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.conteudo[0].entidade").value("PEDIDO"))
    }

    @Test
    fun `a trilha nao expoe endpoint de escrita nem de exclusao`() {
        val token = admin()
        // Sem rota de POST nem DELETE, nao ha como adulterar a prova pela API.
        mockMvc.perform(
            post("/auditoria").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content("{}"),
        ).andExpect(status().isMethodNotAllowed)
    }

    // ------------------------------------------------------------ apoio

    private fun totalDeRegistros(entidade: String): Int = objectMapper.readTree(
        mockMvc.perform(
            get("/auditoria?entidade=$entidade&limit=100").header("Authorization", "Bearer ${admin()}"),
        ).andReturn().response.contentAsString,
    ).get("totalItens").asInt()

    private fun primeiroRegistro(entidade: String) = objectMapper.readTree(
        mockMvc.perform(
            get("/auditoria?entidade=$entidade").header("Authorization", "Bearer ${admin()}"),
        ).andReturn().response.contentAsString,
    ).get("conteudo").first()

    private fun criarPedido(token: String): String = objectMapper.readTree(
        mockMvc.perform(
            post("/pedidos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"unidadeId":"$recife","canalPedido":"APP",
                        "itens":[{"produtoId":"$tapioca","quantidade":2}]}""",
                ),
        ).andExpect(status().isCreated).andReturn().response.contentAsString,
    ).get("id").asText()

    private fun admin() = autenticar("admin@raizes.com.br")
    private fun cliente() = autenticar("cliente@exemplo.com")

    private fun autenticar(email: String): String = objectMapper.readTree(
        mockMvc.perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","senha":"Senha@123"}"""),
        ).andReturn().response.contentAsString,
    ).get("accessToken").asText()
}
