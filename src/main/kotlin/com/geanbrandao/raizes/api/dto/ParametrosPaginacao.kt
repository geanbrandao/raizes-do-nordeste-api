package com.geanbrandao.raizes.api.dto

import com.geanbrandao.raizes.api.exception.BadRequestException
import com.geanbrandao.raizes.api.exception.ErrorDetail
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

/**
 * Traduz os query params de paginação da API para o Pageable do Spring Data.
 *
 * A API usa page começando em 1 e limit, que e o que o roteiro pede. O Spring usa
 * page começando em 0 e size. A conversão fica concentrada aqui para nenhum
 * controller precisar lembrar dessa diferença.
 */
object ParametrosPaginacao {

    /** Teto de itens por pagina. Sem isso, um limit=100000 derrubaria o banco em horario de pico. */
    const val LIMITE_MAXIMO = 100
    const val LIMITE_PADRAO = 10

    /**
     * Monta o Pageable a partir dos parametros recebidos na query.
     *
     * @param pagina Pagina pedida, começando em 1.
     * @param limite Itens por pagina.
     * @param ordenacao Ordenação a aplicar, quando o endpoint tiver uma preferida.
     * @return Pageable equivalente, ja com pagina base zero.
     * @throws BadRequestException se pagina ou limite estiverem fora da faixa aceita.
     */
    fun de(
        pagina: Int = 1,
        limite: Int = LIMITE_PADRAO,
        ordenacao: Sort = Sort.unsorted(),
    ): Pageable {
        val problemas = buildList {
            if (pagina < 1) add(ErrorDetail("page", "deve ser maior ou igual a 1"))
            if (limite < 1) add(ErrorDetail("limit", "deve ser maior ou igual a 1"))
            if (limite > LIMITE_MAXIMO) add(ErrorDetail("limit", "deve ser no maximo $LIMITE_MAXIMO"))
        }
        if (problemas.isNotEmpty()) {
            throw BadRequestException(
                message = "Parametros de paginação invalidos.",
                details = problemas,
            )
        }
        return PageRequest.of(pagina - 1, limite, ordenacao)
    }
}
