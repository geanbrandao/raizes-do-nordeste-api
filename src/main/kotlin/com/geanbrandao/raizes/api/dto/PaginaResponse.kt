package com.geanbrandao.raizes.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page

/**
 * Envelope de toda listagem paginada da API.
 *
 * Existe para o cliente não precisar adivinhar quantas paginas faltam. Tambem evita
 * devolver o Page do Spring direto, que expõe um monte de campo interno e muda de
 * formato entre versões do framework.
 *
 * @param conteudo Itens da pagina atual.
 * @param pagina Numero da pagina atual, começando em 1.
 * @param limite Quantos itens cabem por pagina.
 * @param totalItens Total de itens considerando todos os filtros aplicados.
 * @param totalPaginas Quantas paginas existem no total.
 * @param primeira Se esta e a primeira pagina.
 * @param ultima Se esta e a ultima pagina.
 */
@Schema(description = "Listagem paginada")
data class PaginaResponse<T>(
    val conteudo: List<T>,
    @field:Schema(example = "1") val pagina: Int,
    @field:Schema(example = "10") val limite: Int,
    @field:Schema(example = "42") val totalItens: Long,
    @field:Schema(example = "5") val totalPaginas: Int,
    val primeira: Boolean,
    val ultima: Boolean,
) {
    companion object {
        /**
         * Converte um Page do Spring Data no envelope da API.
         *
         * O Spring conta pagina a partir de zero e a API a partir de um, então a
         * conversão do numero acontece aqui, num lugar so.
         *
         * @param page Pagina devolvida pelo repository.
         * @param transformar Função que vira cada entidade no DTO de resposta.
         * @return Envelope pronto para devolver no controller.
         */
        fun <E, T> de(page: Page<E>, transformar: (E) -> T): PaginaResponse<T> = PaginaResponse(
            conteudo = page.content.map(transformar),
            pagina = page.number + 1,
            limite = page.size,
            totalItens = page.totalElements,
            totalPaginas = page.totalPages,
            primeira = page.isFirst,
            ultima = page.isLast,
        )
    }
}
