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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * Testes do cardapio por unidade.
 *
 * Esta e a parte do caso que diz que nem toda loja da rede e igual: o cardapio e por
 * unidade e o preço pode variar de uma para outra. Os testes cobrem justamente isso,
 * mais a regra de que gerente so mexe na propria loja.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integracao")
@Transactional
class CardapioControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private val recife = "10000000-0000-0000-0000-000000000001"
    private val caruaru = "10000000-0000-0000-0000-000000000002"
    private val tapiocaQueijo = "30000000-0000-0000-0000-000000000001"
    private val boloDeRolo = "30000000-0000-0000-0000-000000000006"

    // ------------------------------------------------------- consulta

    @Test
    fun `cardapio e publico e nao exige token`() {
        mockMvc.perform(get("/unidades/$recife/cardapio"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[0].nome").isNotEmpty)
            .andExpect(jsonPath("$[0].preco").isNotEmpty)
    }

    @Test
    fun `unidade completa vende mais itens que a reduzida`() {
        val itensRecife = contarItens(recife)
        val itensCaruaru = contarItens(caruaru)

        assert(itensRecife == 10) { "Recife deveria ter 10 itens, veio $itensRecife" }
        assert(itensCaruaru == 6) { "Caruaru deveria ter 6 itens, veio $itensCaruaru" }
    }

    @Test
    fun `mesmo produto tem preco diferente em cada unidade`() {
        val precoRecife = precoDoProduto(recife, "Tapioca de queijo coalho")
        val precoCaruaru = precoDoProduto(caruaru, "Tapioca de queijo coalho")

        assert(precoCaruaru < precoRecife) {
            "Caruaru ($precoCaruaru) deveria ser mais barato que Recife ($precoRecife)"
        }
    }

    @Test
    fun `cardapio de unidade inexistente devolve 404`() {
        mockMvc.perform(get("/unidades/10000000-0000-0000-0000-0000000000ff/cardapio"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value(ErrorCodes.UNIDADE_NAO_ENCONTRADA))
    }

    // ------------------------------------------------------ alteração

    @Test
    fun `gerente ajusta o preco da propria unidade`() {
        val token = autenticar("gerente.recife@raizes.com.br")

        mockMvc.perform(
            put("/unidades/$recife/cardapio/$tapiocaQueijo")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"preco":15.50,"disponivel":true}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.preco").value(15.50))
            .andExpect(jsonPath("$.nome").value("Tapioca de queijo coalho"))
    }

    @Test
    fun `gerente nao mexe no cardapio de outra unidade`() {
        val token = autenticar("gerente.recife@raizes.com.br")

        mockMvc.perform(
            put("/unidades/$caruaru/cardapio/$tapiocaQueijo")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"preco":1.00,"disponivel":true}"""),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value(ErrorCodes.SEM_PERMISSAO))
    }

    @Test
    fun `admin mexe no cardapio de qualquer unidade`() {
        val token = autenticar("admin@raizes.com.br")

        mockMvc.perform(
            put("/unidades/$caruaru/cardapio/$boloDeRolo")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"preco":9.00,"disponivel":true}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.preco").value(9.00))
    }

    @Test
    fun `incluir produto novo no cardapio aumenta a lista da unidade`() {
        val token = autenticar("admin@raizes.com.br")
        val antes = contarItens(caruaru)

        // Bolo de rolo nao estava no cardapio de Caruaru no seed.
        mockMvc.perform(
            put("/unidades/$caruaru/cardapio/$boloDeRolo")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"preco":9.00,"disponivel":true}"""),
        ).andExpect(status().isOk)

        assert(contarItens(caruaru) == antes + 1)
    }

    @Test
    fun `item marcado como indisponivel some da consulta publica`() {
        val token = autenticar("gerente.recife@raizes.com.br")
        val antes = contarItens(recife)

        mockMvc.perform(
            put("/unidades/$recife/cardapio/$tapiocaQueijo")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"preco":12.90,"disponivel":false}"""),
        ).andExpect(status().isOk)

        assert(contarItens(recife) == antes - 1) { "item fora do ar deveria sumir do cardapio" }

        // Mas a operação da loja ainda consegue ver.
        val corpo = mockMvc.perform(get("/unidades/$recife/cardapio?incluirIndisponiveis=true"))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assert(objectMapper.readTree(corpo).size() == antes)
    }

    @Test
    fun `cliente nao altera cardapio`() {
        val token = autenticar("cliente@exemplo.com")

        mockMvc.perform(
            put("/unidades/$recife/cardapio/$tapiocaQueijo")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"preco":0.01,"disponivel":true}"""),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `alterar cardapio sem token devolve 401`() {
        mockMvc.perform(
            put("/unidades/$recife/cardapio/$tapiocaQueijo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"preco":0.01,"disponivel":true}"""),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `preco negativo devolve 422`() {
        val token = autenticar("admin@raizes.com.br")

        mockMvc.perform(
            put("/unidades/$recife/cardapio/$tapiocaQueijo")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"preco":-5.00,"disponivel":true}"""),
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.details[0].field").value("preco"))
    }

    @Test
    fun `produto inexistente no cardapio devolve 404`() {
        val token = autenticar("admin@raizes.com.br")

        mockMvc.perform(
            put("/unidades/$recife/cardapio/30000000-0000-0000-0000-0000000000ff")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"preco":10.00,"disponivel":true}"""),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value(ErrorCodes.PRODUTO_NAO_ENCONTRADO))
    }

    @Test
    fun `remover item tira o produto do cardapio`() {
        val token = autenticar("gerente.recife@raizes.com.br")
        val antes = contarItens(recife)

        mockMvc.perform(
            delete("/unidades/$recife/cardapio/$tapiocaQueijo")
                .header("Authorization", "Bearer $token"),
        ).andExpect(status().isNoContent)

        assert(contarItens(recife) == antes - 1)
    }

    // ------------------------------------------------------- apoio

    private fun contarItens(unidadeId: String): Int {
        val corpo = mockMvc.perform(get("/unidades/$unidadeId/cardapio"))
            .andExpect(status().isOk).andReturn().response.contentAsString
        return objectMapper.readTree(corpo).size()
    }

    private fun precoDoProduto(unidadeId: String, nome: String): Double {
        val corpo = mockMvc.perform(get("/unidades/$unidadeId/cardapio"))
            .andReturn().response.contentAsString
        return objectMapper.readTree(corpo).first { it.get("nome").asText() == nome }
            .get("preco").asDouble()
    }

    private fun autenticar(email: String): String {
        val corpo = mockMvc.perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","senha":"Senha@123"}"""),
        ).andReturn().response.contentAsString
        return objectMapper.readTree(corpo).get("accessToken").asText()
    }
}
