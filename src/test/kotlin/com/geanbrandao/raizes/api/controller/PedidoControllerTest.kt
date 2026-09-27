package com.geanbrandao.raizes.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.geanbrandao.raizes.api.exception.ErrorCodes
import com.geanbrandao.raizes.api.repository.EstoqueRepository
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
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Testes do fluxo critico do pedido.
 *
 * Cobre a criação com multicanalidade, o congelamento de preço, a baixa de estoque,
 * a maquina de estados, o cancelamento com devolução e as regras de visibilidade.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integracao")
@Transactional
class PedidoControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var estoqueRepository: EstoqueRepository

    private val recife = "10000000-0000-0000-0000-000000000001"
    private val caruaru = "10000000-0000-0000-0000-000000000002"
    private val tapioca = "30000000-0000-0000-0000-000000000001"
    private val cuscuzFrango = "30000000-0000-0000-0000-000000000003"
    private val boloDeRolo = "30000000-0000-0000-0000-000000000006"

    // -------------------------------------------------------- criação

    @Test
    fun `T06 - cliente cria pedido com itens validos`() {
        val token = autenticar("cliente@exemplo.com")

        mockMvc.perform(
            post("/pedidos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoPedido(recife, "TOTEM", tapioca to 2)),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("AGUARDANDO_PAGAMENTO"))
            .andExpect(jsonPath("$.canalPedido").value("TOTEM"))
            .andExpect(jsonPath("$.itens.length()").value(1))
            .andExpect(jsonPath("$.itens[0].precoUnitario").value(12.90))
            .andExpect(jsonPath("$.subtotal").value(25.80))
            .andExpect(jsonPath("$.total").value(25.80))
            .andExpect(jsonPath("$.proximosStatus").isArray)
    }

    @Test
    fun `preco vem do cardapio da unidade e nao do request`() {
        val token = autenticar("cliente@exemplo.com")

        // Mesmo mandando preço no corpo, o servidor ignora: ele nem le esse campo.
        val corpo = """
            {"unidadeId":"$recife","canalPedido":"APP",
             "itens":[{"produtoId":"$tapioca","quantidade":1,"precoUnitario":0.01}]}
        """.trimIndent()

        mockMvc.perform(
            post("/pedidos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content(corpo),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.itens[0].precoUnitario").value(12.90))
    }

    @Test
    fun `mesma tapioca custa menos em Caruaru`() {
        val token = autenticar("cliente@exemplo.com")

        val emRecife = criarPedido(token, recife, "APP", tapioca to 1)
        val emCaruaru = criarPedido(token, caruaru, "APP", tapioca to 1)

        val precoRecife = emRecife.get("itens")[0].get("precoUnitario").asDouble()
        val precoCaruaru = emCaruaru.get("itens")[0].get("precoUnitario").asDouble()
        assert(precoCaruaru < precoRecife) { "$precoCaruaru deveria ser menor que $precoRecife" }
    }

    @Test
    fun `criacao baixa o estoque`() {
        val token = autenticar("cliente@exemplo.com")
        val antes = saldo(tapioca)

        criarPedido(token, recife, "WEB", tapioca to 3)

        assertEquals(antes - 3, saldo(tapioca))
    }

    @Test
    fun `T08 - pedido sem estoque devolve 409 e nao baixa nada`() {
        val token = autenticar("cliente@exemplo.com")
        val antesTapioca = saldo(tapioca)

        mockMvc.perform(
            post("/pedidos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoPedido(recife, "APP", tapioca to 1, boloDeRolo to 10)),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value(ErrorCodes.ESTOQUE_INSUFICIENTE))
            .andExpect(jsonPath("$.details[0].issue").value(org.hamcrest.Matchers.containsString("disponivel: 2")))

        // O item que tinha saldo tambem nao pode ter sido baixado.
        assertEquals(antesTapioca, saldo(tapioca))
    }

    @Test
    fun `T04 - pedido sem canalPedido devolve 400 apontando o campo`() {
        val token = autenticar("cliente@exemplo.com")

        mockMvc.perform(
            post("/pedidos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"unidadeId":"$recife","itens":[{"produtoId":"$tapioca","quantidade":1}]}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value(ErrorCodes.REQUISICAO_INVALIDA))
            .andExpect(jsonPath("$.details[0].field").value("canalPedido"))
    }

    @Test
    fun `canalPedido invalido devolve 400 listando os aceitos`() {
        val token = autenticar("cliente@exemplo.com")

        mockMvc.perform(
            post("/pedidos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoPedido(recife, "IFOOD", tapioca to 1)),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.details[0].field").value("canalPedido"))
            .andExpect(jsonPath("$.details[0].issue").value(org.hamcrest.Matchers.containsString("TOTEM")))
    }

    @Test
    fun `T05 - quantidade zero devolve 422`() {
        val token = autenticar("cliente@exemplo.com")

        mockMvc.perform(
            post("/pedidos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoPedido(recife, "APP", tapioca to 0)),
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.details[0].field").value("itens[0].quantidade"))
    }

    @Test
    fun `pedido sem itens devolve 422`() {
        val token = autenticar("cliente@exemplo.com")

        mockMvc.perform(
            post("/pedidos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"unidadeId":"$recife","canalPedido":"APP","itens":[]}"""),
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.details[0].field").value("itens"))
    }

    @Test
    fun `T07 - unidade inexistente devolve 404`() {
        val token = autenticar("cliente@exemplo.com")

        mockMvc.perform(
            post("/pedidos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoPedido("10000000-0000-0000-0000-0000000000ff", "APP", tapioca to 1)),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value(ErrorCodes.UNIDADE_NAO_ENCONTRADA))
    }

    @Test
    fun `produto fora do cardapio da unidade devolve 422 dizendo qual`() {
        val token = autenticar("cliente@exemplo.com")

        // Bolo de rolo nao esta no cardapio de Caruaru.
        mockMvc.perform(
            post("/pedidos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoPedido(caruaru, "APP", boloDeRolo to 1)),
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.error").value(ErrorCodes.PRODUTO_FORA_DO_CARDAPIO))
            .andExpect(jsonPath("$.details[0].issue").value(org.hamcrest.Matchers.containsString("cardapio")))
    }

    @Test
    fun `cliente nao consegue criar pedido no nome de outra pessoa`() {
        val token = autenticar("cliente@exemplo.com")
        val outroCliente = UUID.randomUUID()

        val corpo = """
            {"unidadeId":"$recife","canalPedido":"APP","clienteId":"$outroCliente",
             "itens":[{"produtoId":"$tapioca","quantidade":1}]}
        """.trimIndent()

        val resposta = mockMvc.perform(
            post("/pedidos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content(corpo),
        ).andExpect(status().isCreated).andReturn().response.contentAsString

        // O clienteId do corpo e ignorado: o pedido sai no nome de quem esta logado.
        val clienteIdDoPedido = objectMapper.readTree(resposta).get("clienteId").asText()
        assert(clienteIdDoPedido != outroCliente.toString())
    }

    @Test
    fun `atendente nao registra pedido de outra unidade`() {
        val token = autenticar("atendente.recife@raizes.com.br")

        mockMvc.perform(
            post("/pedidos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoPedido(caruaru, "BALCAO", tapioca to 1)),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value(ErrorCodes.SEM_PERMISSAO))
    }

    @Test
    fun `atendente registra pedido de balcao sem cliente identificado`() {
        val token = autenticar("atendente.recife@raizes.com.br")

        mockMvc.perform(
            post("/pedidos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoPedido(recife, "BALCAO", tapioca to 1)),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.canalPedido").value("BALCAO"))
            .andExpect(jsonPath("$.clienteId").doesNotExist())
    }

    // ------------------------------------------------- multicanalidade

    @Test
    fun `T12 - filtro por canal devolve so os pedidos daquele canal`() {
        val token = autenticar("cliente@exemplo.com")
        criarPedido(token, recife, "TOTEM", tapioca to 1)
        criarPedido(token, recife, "APP", cuscuzFrango to 1)

        mockMvc.perform(
            get("/pedidos?canalPedido=TOTEM").header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItens").value(1))
            .andExpect(jsonPath("$.conteudo[0].canalPedido").value("TOTEM"))
    }

    @Test
    fun `filtro por status funciona junto com o de canal`() {
        val token = autenticar("cliente@exemplo.com")
        criarPedido(token, recife, "APP", tapioca to 1)

        mockMvc.perform(
            get("/pedidos?canalPedido=APP&status=AGUARDANDO_PAGAMENTO")
                .header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItens").value(1))
    }

    // ---------------------------------------------------- visibilidade

    @Test
    fun `cliente so enxerga os proprios pedidos`() {
        val atendente = autenticar("atendente.recife@raizes.com.br")
        criarPedido(atendente, recife, "BALCAO", tapioca to 1)

        val cliente = autenticar("cliente@exemplo.com")
        mockMvc.perform(get("/pedidos").header("Authorization", "Bearer $cliente"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItens").value(0))
    }

    @Test
    fun `pedido de outra pessoa devolve 404 e nao 403`() {
        val atendente = autenticar("atendente.recife@raizes.com.br")
        val pedido = criarPedido(atendente, recife, "BALCAO", tapioca to 1)
        val pedidoId = pedido.get("id").asText()

        val cliente = autenticar("cliente@exemplo.com")
        mockMvc.perform(get("/pedidos/$pedidoId").header("Authorization", "Bearer $cliente"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value(ErrorCodes.PEDIDO_NAO_ENCONTRADO))
    }

    @Test
    fun `gerente enxerga os pedidos da propria unidade`() {
        val cliente = autenticar("cliente@exemplo.com")
        criarPedido(cliente, recife, "APP", tapioca to 1)

        val gerente = autenticar("gerente.recife@raizes.com.br")
        mockMvc.perform(get("/pedidos").header("Authorization", "Bearer $gerente"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItens").value(1))

        val gerenteCaruaru = autenticar("gerente.caruaru@raizes.com.br")
        mockMvc.perform(get("/pedidos").header("Authorization", "Bearer $gerenteCaruaru"))
            .andExpect(jsonPath("$.totalItens").value(0))
    }

    // -------------------------------------------------- maquina de estados

    @Test
    fun `fluxo completo do pedido ate entregue`() {
        val cliente = autenticar("cliente@exemplo.com")
        val pedidoId = criarPedido(cliente, recife, "APP", tapioca to 1).get("id").asText()
        val gerente = autenticar("gerente.recife@raizes.com.br")

        listOf("PAGO", "EM_PREPARO", "PRONTO", "ENTREGUE").forEach { destino ->
            mockMvc.perform(
                patch("/pedidos/$pedidoId/status").header("Authorization", "Bearer $gerente")
                    .contentType(MediaType.APPLICATION_JSON).content("""{"status":"$destino"}"""),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value(destino))
        }
    }

    @Test
    fun `T13 - transicao invalida devolve 409 dizendo o que e possivel`() {
        val cliente = autenticar("cliente@exemplo.com")
        val pedidoId = criarPedido(cliente, recife, "APP", tapioca to 1).get("id").asText()
        val gerente = autenticar("gerente.recife@raizes.com.br")

        // Pular de AGUARDANDO_PAGAMENTO direto para ENTREGUE nao existe no fluxo.
        mockMvc.perform(
            patch("/pedidos/$pedidoId/status").header("Authorization", "Bearer $gerente")
                .contentType(MediaType.APPLICATION_JSON).content("""{"status":"ENTREGUE"}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value(ErrorCodes.TRANSICAO_DE_STATUS_INVALIDA))
            .andExpect(jsonPath("$.details[0].issue").value(org.hamcrest.Matchers.containsString("PAGO")))
    }

    @Test
    fun `cozinha nao entrega pedido`() {
        val cliente = autenticar("cliente@exemplo.com")
        val pedidoId = criarPedido(cliente, recife, "APP", tapioca to 1).get("id").asText()
        val gerente = autenticar("gerente.recife@raizes.com.br")
        val cozinha = autenticar("cozinha.recife@raizes.com.br")

        avancar(pedidoId, gerente, "PAGO")
        avancar(pedidoId, cozinha, "EM_PREPARO")
        avancar(pedidoId, cozinha, "PRONTO")

        // PRONTO -> ENTREGUE e valido no fluxo, mas nao e trabalho da cozinha.
        mockMvc.perform(
            patch("/pedidos/$pedidoId/status").header("Authorization", "Bearer $cozinha")
                .contentType(MediaType.APPLICATION_JSON).content("""{"status":"ENTREGUE"}"""),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value(ErrorCodes.SEM_PERMISSAO))
    }

    @Test
    fun `cliente nao muda status de pedido`() {
        val cliente = autenticar("cliente@exemplo.com")
        val pedidoId = criarPedido(cliente, recife, "APP", tapioca to 1).get("id").asText()

        mockMvc.perform(
            patch("/pedidos/$pedidoId/status").header("Authorization", "Bearer $cliente")
                .contentType(MediaType.APPLICATION_JSON).content("""{"status":"PAGO"}"""),
        ).andExpect(status().isForbidden)
    }

    // ------------------------------------------------------ cancelamento

    @Test
    fun `cancelamento devolve o estoque`() {
        val cliente = autenticar("cliente@exemplo.com")
        val antes = saldo(tapioca)
        val pedidoId = criarPedido(cliente, recife, "APP", tapioca to 4).get("id").asText()
        assertEquals(antes - 4, saldo(tapioca))

        mockMvc.perform(
            post("/pedidos/$pedidoId/cancelamento").header("Authorization", "Bearer $cliente"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CANCELADO"))

        assertEquals(antes, saldo(tapioca))
    }

    @Test
    fun `pedido pronto nao pode mais ser cancelado`() {
        val cliente = autenticar("cliente@exemplo.com")
        val pedidoId = criarPedido(cliente, recife, "APP", tapioca to 1).get("id").asText()
        val gerente = autenticar("gerente.recife@raizes.com.br")

        avancar(pedidoId, gerente, "PAGO")
        avancar(pedidoId, gerente, "EM_PREPARO")
        avancar(pedidoId, gerente, "PRONTO")

        mockMvc.perform(
            post("/pedidos/$pedidoId/cancelamento").header("Authorization", "Bearer $gerente"),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value(ErrorCodes.PEDIDO_NAO_PODE_SER_CANCELADO))
    }

    // ------------------------------------------------------------ apoio

    private fun saldo(produtoId: String) = estoqueRepository
        .findByUnidadeIdAndProdutoId(UUID.fromString(recife), UUID.fromString(produtoId))!!
        .saldoAtual

    private fun avancar(pedidoId: String, token: String, destino: String) {
        mockMvc.perform(
            patch("/pedidos/$pedidoId/status").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content("""{"status":"$destino"}"""),
        ).andExpect(status().isOk)
    }

    private fun corpoPedido(
        unidadeId: String,
        canal: String,
        vararg itens: Pair<String, Int>,
    ): String {
        val lista = itens.joinToString(",") { (id, qtd) ->
            """{"produtoId":"$id","quantidade":$qtd}"""
        }
        return """{"unidadeId":"$unidadeId","canalPedido":"$canal","itens":[$lista]}"""
    }

    private fun criarPedido(
        token: String,
        unidadeId: String,
        canal: String,
        vararg itens: Pair<String, Int>,
    ) = objectMapper.readTree(
        mockMvc.perform(
            post("/pedidos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoPedido(unidadeId, canal, *itens)),
        ).andExpect(status().isCreated).andReturn().response.contentAsString,
    )

    private fun autenticar(email: String): String {
        val corpo = mockMvc.perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","senha":"Senha@123"}"""),
        ).andReturn().response.contentAsString
        return objectMapper.readTree(corpo).get("accessToken").asText()
    }
}
