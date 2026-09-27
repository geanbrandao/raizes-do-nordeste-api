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

/**
 * Testes de estoque por unidade.
 *
 * Cobre o saldo, os tres tipos de movimentação e a regra que mais importa para o
 * fluxo de pedido: saida maior que o saldo e recusada com 409.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integracao")
@Transactional
class EstoqueControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private val recife = "10000000-0000-0000-0000-000000000001"
    private val caruaru = "10000000-0000-0000-0000-000000000002"
    private val tapioca = "30000000-0000-0000-0000-000000000001"
    private val boloDeRolo = "30000000-0000-0000-0000-000000000006"

    // ---------------------------------------------------------- consulta

    @Test
    fun `gerente consulta o saldo da propria unidade`() {
        val token = autenticar("gerente.recife@raizes.com.br")

        mockMvc.perform(get("/unidades/$recife/estoque").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItens").value(10))
            .andExpect(jsonPath("$.conteudo[0].nome").isNotEmpty)
            .andExpect(jsonPath("$.conteudo[0].saldoAtual").isNumber)
    }

    @Test
    fun `cozinha consulta saldo mas nao movimenta`() {
        val token = autenticar("cozinha.recife@raizes.com.br")

        mockMvc.perform(get("/unidades/$recife/estoque").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)

        mockMvc.perform(
            post("/unidades/$recife/estoque/movimentacoes")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo(tapioca, "ENTRADA", 10)),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `cliente nao acessa estoque`() {
        val token = autenticar("cliente@exemplo.com")

        mockMvc.perform(get("/unidades/$recife/estoque").header("Authorization", "Bearer $token"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value(ErrorCodes.SEM_PERMISSAO))
    }

    @Test
    fun `estoque sem token devolve 401`() {
        mockMvc.perform(get("/unidades/$recife/estoque"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `gerente nao enxerga o estoque de outra unidade`() {
        val token = autenticar("gerente.recife@raizes.com.br")

        mockMvc.perform(get("/unidades/$caruaru/estoque").header("Authorization", "Bearer $token"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value(ErrorCodes.SEM_PERMISSAO))
    }

    @Test
    fun `admin enxerga o estoque de qualquer unidade`() {
        val token = autenticar("admin@raizes.com.br")

        mockMvc.perform(get("/unidades/$caruaru/estoque").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItens").value(6))
    }

    @Test
    fun `saldo baixo do seed vem marcado como abaixo do minimo`() {
        val token = autenticar("gerente.recife@raizes.com.br")

        val corpo = mockMvc.perform(
            get("/unidades/$recife/estoque?limit=100").header("Authorization", "Bearer $token"),
        ).andExpect(status().isOk).andReturn().response.contentAsString

        val bolo = objectMapper.readTree(corpo).get("conteudo")
            .first { it.get("produtoId").asText() == boloDeRolo }
        assert(bolo.get("saldoAtual").asInt() == 2)
        assert(bolo.get("abaixoDoMinimo").asBoolean()) { "saldo 2 com minimo 10 deveria acusar" }
    }

    // ----------------------------------------------------- movimentação

    @Test
    fun `entrada soma ao saldo`() {
        val token = autenticar("gerente.recife@raizes.com.br")

        mockMvc.perform(
            post("/unidades/$recife/estoque/movimentacoes")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo(boloDeRolo, "ENTRADA", 20, "Recebimento do fornecedor")),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.tipo").value("ENTRADA"))
            .andExpect(jsonPath("$.quantidade").value(20))
            .andExpect(jsonPath("$.saldoApos").value(22))
            .andExpect(jsonPath("$.motivo").value("Recebimento do fornecedor"))
    }

    @Test
    fun `saida subtrai do saldo`() {
        val token = autenticar("atendente.recife@raizes.com.br")

        mockMvc.perform(
            post("/unidades/$recife/estoque/movimentacoes")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo(tapioca, "SAIDA", 15)),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.saldoApos").value(35))
    }

    @Test
    fun `ajuste define o saldo absoluto`() {
        val token = autenticar("gerente.recife@raizes.com.br")

        // Contagem de inventario: a loja contou 7 na prateleira.
        mockMvc.perform(
            post("/unidades/$recife/estoque/movimentacoes")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo(tapioca, "AJUSTE", 7, "Contagem de inventario")),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.saldoApos").value(7))
    }

    @Test
    fun `saida maior que o saldo devolve 409 dizendo quanto tem`() {
        val token = autenticar("gerente.recife@raizes.com.br")

        mockMvc.perform(
            post("/unidades/$recife/estoque/movimentacoes")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo(boloDeRolo, "SAIDA", 50)),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value(ErrorCodes.ESTOQUE_INSUFICIENTE))
            .andExpect(jsonPath("$.details[0].issue").value(org.hamcrest.Matchers.containsString("disponivel: 2")))
    }

    @Test
    fun `entrada de quantidade zero devolve 422`() {
        val token = autenticar("gerente.recife@raizes.com.br")

        mockMvc.perform(
            post("/unidades/$recife/estoque/movimentacoes")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo(tapioca, "ENTRADA", 0)),
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.details[0].field").value("quantidade"))
    }

    @Test
    fun `quantidade negativa devolve 422`() {
        val token = autenticar("gerente.recife@raizes.com.br")

        mockMvc.perform(
            post("/unidades/$recife/estoque/movimentacoes")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo(tapioca, "ENTRADA", -5)),
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.details[0].field").value("quantidade"))
    }

    @Test
    fun `ajuste para zero e aceito porque contagem pode dar zero`() {
        val token = autenticar("gerente.recife@raizes.com.br")

        mockMvc.perform(
            post("/unidades/$recife/estoque/movimentacoes")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo(tapioca, "AJUSTE", 0, "Contagem: prateleira vazia")),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.saldoApos").value(0))
    }

    @Test
    fun `tipo invalido devolve 400 listando os aceitos`() {
        val token = autenticar("gerente.recife@raizes.com.br")

        mockMvc.perform(
            post("/unidades/$recife/estoque/movimentacoes")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo(tapioca, "SUMICO", 5)),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.details[0].field").value("tipo"))
    }

    @Test
    fun `produto inexistente devolve 404`() {
        val token = autenticar("gerente.recife@raizes.com.br")

        mockMvc.perform(
            post("/unidades/$recife/estoque/movimentacoes")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo("30000000-0000-0000-0000-0000000000ff", "ENTRADA", 5)),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value(ErrorCodes.PRODUTO_NAO_ENCONTRADO))
    }

    @Test
    fun `produto novo na unidade abre saldo na primeira entrada`() {
        val token = autenticar("admin@raizes.com.br")
        // Bolo de rolo nao tem estoque em Caruaru: nao esta no cardapio de la.
        mockMvc.perform(
            post("/unidades/$caruaru/estoque/movimentacoes")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo(boloDeRolo, "ENTRADA", 30)),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.saldoApos").value(30))
    }

    // -------------------------------------------------------- histórico

    @Test
    fun `historico registra cada movimentacao com o saldo resultante`() {
        val token = autenticar("gerente.recife@raizes.com.br")

        listOf(10, 5).forEach { qtd ->
            mockMvc.perform(
                post("/unidades/$recife/estoque/movimentacoes")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(corpo(tapioca, "ENTRADA", qtd)),
            ).andExpect(status().isCreated)
        }

        mockMvc.perform(
            get("/unidades/$recife/estoque/$tapioca/movimentacoes")
                .header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItens").value(2))
            // Mais recente primeiro: 50 + 10 = 60, depois 60 + 5 = 65.
            .andExpect(jsonPath("$.conteudo[0].saldoApos").value(65))
            .andExpect(jsonPath("$.conteudo[1].saldoApos").value(60))
    }

    @Test
    fun `atendente nao ve o historico`() {
        val token = autenticar("atendente.recife@raizes.com.br")

        mockMvc.perform(
            get("/unidades/$recife/estoque/$tapioca/movimentacoes")
                .header("Authorization", "Bearer $token"),
        ).andExpect(status().isForbidden)
    }

    // ------------------------------------------------------------ apoio

    private fun corpo(produtoId: String, tipo: String, quantidade: Int, motivo: String? = null): String {
        val campoMotivo = motivo?.let { ""","motivo":"$it"""" } ?: ""
        return """{"produtoId":"$produtoId","tipo":"$tipo","quantidade":$quantidade$campoMotivo}"""
    }

    private fun autenticar(email: String): String {
        val corpo = mockMvc.perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","senha":"Senha@123"}"""),
        ).andReturn().response.contentAsString
        return objectMapper.readTree(corpo).get("accessToken").asText()
    }
}
