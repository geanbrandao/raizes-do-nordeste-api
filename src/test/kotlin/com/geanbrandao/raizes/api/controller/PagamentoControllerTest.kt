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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

/**
 * Testes do pagamento mock.
 *
 * Cobre os tres desfechos do gateway, a idempotencia, o callback e as regras de quem
 * pode pagar o que. E aqui que vivem os cenarios T09 e T10 do plano de testes:
 * pagamento aprovado move o pedido, pagamento recusado tambem, com mensagem coerente.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integracao")
@Transactional
class PagamentoControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private val recife = "10000000-0000-0000-0000-000000000001"
    private val tapioca = "30000000-0000-0000-0000-000000000001"
    private val assinatura = "segredo-de-teste"

    // ---------------------------------------------------------- aprovado

    @Test
    fun `T09 - pagamento aprovado move o pedido para PAGO`() {
        val token = autenticar("cliente@exemplo.com")
        val pedidoId = criarPedido(token)

        mockMvc.perform(
            post("/pedidos/$pedidoId/pagamentos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"metodo":"PIX","tokenPagamento":"tok_ok_123"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("APROVADO"))
            .andExpect(jsonPath("$.statusPedido").value("PAGO"))
            .andExpect(jsonPath("$.valor").value(25.80))
            .andExpect(jsonPath("$.idTransacaoExterna").isNotEmpty)
            .andExpect(jsonPath("$.mensagem").value("Pagamento aprovado."))

        mockMvc.perform(get("/pedidos/$pedidoId").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.status").value("PAGO"))
    }

    @Test
    fun `sem token o gateway aprova, que e o caminho feliz padrao`() {
        val token = autenticar("cliente@exemplo.com")
        val pedidoId = criarPedido(token)

        mockMvc.perform(
            post("/pedidos/$pedidoId/pagamentos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content("""{"metodo":"DINHEIRO"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.statusPedido").value("PAGO"))
    }

    @Test
    fun `o valor cobrado e o total do pedido e nao o que o cliente mandar`() {
        val token = autenticar("cliente@exemplo.com")
        val pedidoId = criarPedido(token)

        mockMvc.perform(
            post("/pedidos/$pedidoId/pagamentos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"metodo":"PIX","valor":0.01}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.valor").value(25.80))
    }

    // ---------------------------------------------------------- recusado

    @Test
    fun `T10 - pagamento recusado move o pedido para PAGAMENTO_RECUSADO`() {
        val token = autenticar("cliente@exemplo.com")
        val pedidoId = criarPedido(token)

        mockMvc.perform(
            post("/pedidos/$pedidoId/pagamentos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"metodo":"CARTAO_CREDITO","tokenPagamento":"tok_recusa_01"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("RECUSADO"))
            .andExpect(jsonPath("$.statusPedido").value("PAGAMENTO_RECUSADO"))
            .andExpect(jsonPath("$.mensagem").value("Pagamento recusado pela operadora."))
    }

    @Test
    fun `depois de recusado o cliente pode tentar de novo e aprovar`() {
        val token = autenticar("cliente@exemplo.com")
        val pedidoId = criarPedido(token)

        pagar(pedidoId, token, "tok_recusa_01")
        val segunda = pagar(pedidoId, token, "tok_ok_999")

        assertEquals("APROVADO", segunda.get("status").asText())
        assertEquals("PAGO", segunda.get("statusPedido").asText())
        // A segunda tentativa e contada.
        assertEquals(2, segunda.get("tentativas").asInt())
    }

    // ------------------------------------------- gateway sem resposta

    @Test
    fun `gateway sem resposta deixa o pagamento pendente e o pedido parado`() {
        val token = autenticar("cliente@exemplo.com")
        val pedidoId = criarPedido(token)

        mockMvc.perform(
            post("/pedidos/$pedidoId/pagamentos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"metodo":"PIX","tokenPagamento":"tok_timeout_01"}"""),
        )
            .andExpect(status().isCreated)
            // Pendente, nao recusado: pode ter sido cobrado do outro lado.
            .andExpect(jsonPath("$.status").value("PENDENTE"))
            .andExpect(jsonPath("$.statusPedido").value("AGUARDANDO_PAGAMENTO"))
    }

    @Test
    fun `callback resolve o pagamento que ficou pendente`() {
        val token = autenticar("cliente@exemplo.com")
        val pedidoId = criarPedido(token)
        val pagamentoId = pagar(pedidoId, token, "tok_timeout_01").get("id").asText()

        mockMvc.perform(
            post("/pagamentos/callback").header("X-Gateway-Assinatura", assinatura)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"pagamentoId":"$pagamentoId","resultado":"APROVADO","idTransacaoExterna":"ext_777"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("APROVADO"))
            .andExpect(jsonPath("$.statusPedido").value("PAGO"))

        mockMvc.perform(get("/pedidos/$pedidoId").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.status").value("PAGO"))
    }

    @Test
    fun `callback repetido nao processa duas vezes`() {
        val token = autenticar("cliente@exemplo.com")
        val pedidoId = criarPedido(token)
        val pagamentoId = pagar(pedidoId, token, "tok_timeout_01").get("id").asText()

        val corpo = """{"pagamentoId":"$pagamentoId","resultado":"APROVADO"}"""
        enviarCallback(corpo).andExpect(status().isOk)

        // O gateway reenvia webhook quando nao recebe confirmacao. O segundo nao pode
        // mexer no pedido de novo.
        enviarCallback("""{"pagamentoId":"$pagamentoId","resultado":"RECUSADO"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("APROVADO"))
            .andExpect(jsonPath("$.statusPedido").value("PAGO"))
    }

    @Test
    fun `callback sem assinatura devolve 401`() {
        mockMvc.perform(
            post("/pagamentos/callback").contentType(MediaType.APPLICATION_JSON)
                .content("""{"pagamentoId":"00000000-0000-0000-0000-000000000001","resultado":"APROVADO"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value(ErrorCodes.NAO_AUTENTICADO))
    }

    @Test
    fun `callback com assinatura errada devolve 401`() {
        mockMvc.perform(
            post("/pagamentos/callback").header("X-Gateway-Assinatura", "chute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"pagamentoId":"00000000-0000-0000-0000-000000000001","resultado":"APROVADO"}"""),
        ).andExpect(status().isUnauthorized)
    }

    // ------------------------------------------------------ idempotencia

    @Test
    fun `mesma chave de idempotencia nao cobra duas vezes`() {
        val token = autenticar("cliente@exemplo.com")
        val pedidoId = criarPedido(token)
        val corpo = """{"metodo":"PIX","tokenPagamento":"tok_ok","chaveIdempotencia":"minha-chave-1"}"""

        val primeira = objectMapper.readTree(
            mockMvc.perform(
                post("/pedidos/$pedidoId/pagamentos").header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON).content(corpo),
            ).andExpect(status().isCreated).andReturn().response.contentAsString,
        )

        val segunda = objectMapper.readTree(
            mockMvc.perform(
                post("/pedidos/$pedidoId/pagamentos").header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON).content(corpo),
            ).andExpect(status().isCreated).andReturn().response.contentAsString,
        )

        // Mesmo pagamento devolvido, nao um novo.
        assertEquals(primeira.get("id").asText(), segunda.get("id").asText())
        assertEquals(1, segunda.get("tentativas").asInt())
    }

    // -------------------------------------------------------- permissões

    @Test
    fun `pedido ja pago nao aceita novo pagamento`() {
        val token = autenticar("cliente@exemplo.com")
        val pedidoId = criarPedido(token)
        pagar(pedidoId, token, "tok_ok")

        mockMvc.perform(
            post("/pedidos/$pedidoId/pagamentos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content("""{"metodo":"PIX"}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value(ErrorCodes.PEDIDO_JA_PAGO))
    }

    @Test
    fun `pedido cancelado nao pode ser pago`() {
        val token = autenticar("cliente@exemplo.com")
        val pedidoId = criarPedido(token)
        mockMvc.perform(
            post("/pedidos/$pedidoId/cancelamento").header("Authorization", "Bearer $token"),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/pedidos/$pedidoId/pagamentos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content("""{"metodo":"PIX"}"""),
        ).andExpect(status().isConflict)
    }

    @Test
    fun `nao da para pagar pedido de outra pessoa`() {
        val cliente = autenticar("cliente@exemplo.com")
        val pedidoId = criarPedido(cliente)

        val gerenteCaruaru = autenticar("gerente.caruaru@raizes.com.br")
        mockMvc.perform(
            post("/pedidos/$pedidoId/pagamentos").header("Authorization", "Bearer $gerenteCaruaru")
                .contentType(MediaType.APPLICATION_JSON).content("""{"metodo":"PIX"}"""),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value(ErrorCodes.PEDIDO_NAO_ENCONTRADO))
    }

    @Test
    fun `metodo invalido devolve 400 listando os aceitos`() {
        val token = autenticar("cliente@exemplo.com")
        val pedidoId = criarPedido(token)

        mockMvc.perform(
            post("/pedidos/$pedidoId/pagamentos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content("""{"metodo":"BITCOIN"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.details[0].field").value("metodo"))
    }

    @Test
    fun `consultar pagamento de outra pessoa devolve 404`() {
        val cliente = autenticar("cliente@exemplo.com")
        val pedidoId = criarPedido(cliente)
        val pagamentoId = pagar(pedidoId, cliente, "tok_ok").get("id").asText()

        val outro = autenticar("gerente.caruaru@raizes.com.br")
        mockMvc.perform(get("/pagamentos/$pagamentoId").header("Authorization", "Bearer $outro"))
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------ apoio

    private fun enviarCallback(corpo: String) = mockMvc.perform(
        post("/pagamentos/callback").header("X-Gateway-Assinatura", assinatura)
            .contentType(MediaType.APPLICATION_JSON).content(corpo),
    )

    private fun pagar(pedidoId: String, token: String, tokenPagamento: String) =
        objectMapper.readTree(
            mockMvc.perform(
                post("/pedidos/$pedidoId/pagamentos").header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"metodo":"PIX","tokenPagamento":"$tokenPagamento"}"""),
            ).andExpect(status().isCreated).andReturn().response.contentAsString,
        )

    private fun criarPedido(token: String): String {
        val corpo = mockMvc.perform(
            post("/pedidos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"unidadeId":"$recife","canalPedido":"TOTEM",
                        "itens":[{"produtoId":"$tapioca","quantidade":2}]}""",
                ),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return objectMapper.readTree(corpo).get("id").asText()
    }

    private fun autenticar(email: String): String {
        val corpo = mockMvc.perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","senha":"Senha@123"}"""),
        ).andReturn().response.contentAsString
        return objectMapper.readTree(corpo).get("accessToken").asText()
    }
}
