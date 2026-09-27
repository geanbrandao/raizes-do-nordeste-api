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
 * Testes de unidades e produtos: o cadastro base da rede.
 *
 * Cobre paginação, autorização por perfil e o comportamento de inativar produto em
 * vez de apagar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integracao")
@Transactional
class CatalogoControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private val boloDeRolo = "30000000-0000-0000-0000-000000000006"

    // ------------------------------------------------------ unidades

    @Test
    fun `listagem de unidades e publica e vem paginada`() {
        mockMvc.perform(get("/unidades"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.conteudo").isArray)
            .andExpect(jsonPath("$.totalItens").value(2))
            .andExpect(jsonPath("$.pagina").value(1))
            .andExpect(jsonPath("$.limite").value(10))
            .andExpect(jsonPath("$.primeira").value(true))
            .andExpect(jsonPath("$.ultima").value(true))
    }

    @Test
    fun `paginacao devolve uma unidade por pagina quando limit e 1`() {
        mockMvc.perform(get("/unidades?page=1&limit=1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.conteudo.length()").value(1))
            .andExpect(jsonPath("$.totalPaginas").value(2))
            .andExpect(jsonPath("$.ultima").value(false))

        mockMvc.perform(get("/unidades?page=2&limit=1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pagina").value(2))
            .andExpect(jsonPath("$.ultima").value(true))
    }

    @Test
    fun `pagina zero devolve 400 apontando o parametro`() {
        mockMvc.perform(get("/unidades?page=0"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value(ErrorCodes.REQUISICAO_INVALIDA))
            .andExpect(jsonPath("$.details[0].field").value("page"))
    }

    @Test
    fun `limite acima do teto devolve 400`() {
        mockMvc.perform(get("/unidades?limit=500"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.details[0].field").value("limit"))
    }

    @Test
    fun `unidade inexistente devolve 404`() {
        mockMvc.perform(get("/unidades/10000000-0000-0000-0000-0000000000ff"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value(ErrorCodes.UNIDADE_NAO_ENCONTRADA))
    }

    @Test
    fun `id mal formado devolve 400 e nao 500`() {
        mockMvc.perform(get("/unidades/isso-nao-e-uuid"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value(ErrorCodes.REQUISICAO_INVALIDA))
    }

    @Test
    fun `admin cadastra unidade`() {
        val token = autenticar("admin@raizes.com.br")
        mockMvc.perform(
            post("/unidades").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nome":"Raizes Olinda","cidade":"Olinda","uf":"pe","tipoOperacao":"REDUZIDA"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.uf").value("PE"))
            .andExpect(jsonPath("$.ativa").value(true))
    }

    @Test
    fun `gerente nao cadastra unidade`() {
        val token = autenticar("gerente.recife@raizes.com.br")
        mockMvc.perform(
            post("/unidades").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nome":"Loja Pirata","cidade":"Recife","uf":"PE","tipoOperacao":"COMPLETA"}"""),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value(ErrorCodes.SEM_PERMISSAO))
    }

    @Test
    fun `uf com mais de duas letras devolve 422`() {
        val token = autenticar("admin@raizes.com.br")
        mockMvc.perform(
            post("/unidades").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nome":"Raizes Teste","cidade":"Recife","uf":"PERN","tipoOperacao":"COMPLETA"}"""),
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.details[0].field").value("uf"))
    }

    // ------------------------------------------------------ produtos

    @Test
    fun `catalogo exige autenticacao`() {
        mockMvc.perform(get("/produtos"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value(ErrorCodes.NAO_AUTENTICADO))
    }

    @Test
    fun `cliente autenticado consulta o catalogo`() {
        val token = autenticar("cliente@exemplo.com")
        mockMvc.perform(get("/produtos").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItens").value(10))
    }

    @Test
    fun `filtro por categoria funciona e aceita minuscula`() {
        val token = autenticar("cliente@exemplo.com")
        mockMvc.perform(get("/produtos?categoria=bebida").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItens").value(3))
    }

    @Test
    fun `gerente cadastra produto no catalogo`() {
        val token = autenticar("gerente.recife@raizes.com.br")
        mockMvc.perform(
            post("/produtos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nome":"Pamonha","categoria":"milho","precoBase":9.50,"sazonal":true}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.categoria").value("MILHO"))
            .andExpect(jsonPath("$.sazonal").value(true))
    }

    @Test
    fun `cliente nao cadastra produto`() {
        val token = autenticar("cliente@exemplo.com")
        mockMvc.perform(
            post("/produtos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nome":"Produto Pirata","categoria":"X","precoBase":1.00}"""),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `preco com mais de duas casas devolve 422`() {
        val token = autenticar("admin@raizes.com.br")
        mockMvc.perform(
            post("/produtos").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nome":"Cafe Especial","categoria":"BEBIDA","precoBase":5.999}"""),
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.details[0].field").value("precoBase"))
    }

    @Test
    fun `gerente nao inativa produto`() {
        val token = autenticar("gerente.recife@raizes.com.br")
        mockMvc.perform(delete("/produtos/$boloDeRolo").header("Authorization", "Bearer $token"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `admin inativa produto e ele some da listagem sem sumir do banco`() {
        val token = autenticar("admin@raizes.com.br")

        mockMvc.perform(delete("/produtos/$boloDeRolo").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        // Sumiu da listagem...
        mockMvc.perform(get("/produtos").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.totalItens").value(9))

        // ...mas continua existindo, senao os pedidos antigos quebrariam.
        mockMvc.perform(get("/produtos/$boloDeRolo").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ativo").value(false))
    }

    @Test
    fun `atualizar produto inexistente devolve 404`() {
        val token = autenticar("admin@raizes.com.br")
        mockMvc.perform(
            put("/produtos/30000000-0000-0000-0000-0000000000ff")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nome":"Fantasma","categoria":"X","precoBase":1.00}"""),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value(ErrorCodes.PRODUTO_NAO_ENCONTRADO))
    }

    private fun autenticar(email: String): String {
        val corpo = mockMvc.perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","senha":"Senha@123"}"""),
        ).andReturn().response.contentAsString
        return objectMapper.readTree(corpo).get("accessToken").asText()
    }
}
