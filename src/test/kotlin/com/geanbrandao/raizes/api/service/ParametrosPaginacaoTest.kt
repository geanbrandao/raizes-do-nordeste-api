package com.geanbrandao.raizes.api.service

import com.geanbrandao.raizes.api.dto.ParametrosPaginacao
import com.geanbrandao.raizes.api.exception.BadRequestException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Testes da conversão dos parametros de paginação.
 *
 * A parte que mais importa aqui e o deslocamento de pagina: a API conta a partir de
 * 1 e o Spring a partir de 0. Errar isso faz a primeira pagina sumir sem ninguem
 * perceber de imediato.
 */
class ParametrosPaginacaoTest {

    @Test
    fun `pagina 1 da API vira pagina 0 do Spring`() {
        val pageable = ParametrosPaginacao.de(pagina = 1, limite = 10)
        assertEquals(0, pageable.pageNumber)
        assertEquals(10, pageable.pageSize)
    }

    @Test
    fun `pagina 3 da API vira pagina 2 do Spring`() {
        assertEquals(2, ParametrosPaginacao.de(pagina = 3, limite = 20).pageNumber)
    }

    @Test
    fun `valores padrao sao pagina 1 e limite 10`() {
        val pageable = ParametrosPaginacao.de()
        assertEquals(0, pageable.pageNumber)
        assertEquals(ParametrosPaginacao.LIMITE_PADRAO, pageable.pageSize)
    }

    @Test
    fun `pagina zero e recusada`() {
        val ex = assertFailsWith<BadRequestException> { ParametrosPaginacao.de(pagina = 0) }
        assertEquals("page", ex.details.first().field)
    }

    @Test
    fun `limite acima do teto e recusado`() {
        val ex = assertFailsWith<BadRequestException> {
            ParametrosPaginacao.de(limite = ParametrosPaginacao.LIMITE_MAXIMO + 1)
        }
        assertEquals("limit", ex.details.first().field)
    }

    @Test
    fun `limite no teto e aceito`() {
        val pageable = ParametrosPaginacao.de(limite = ParametrosPaginacao.LIMITE_MAXIMO)
        assertEquals(ParametrosPaginacao.LIMITE_MAXIMO, pageable.pageSize)
    }

    @Test
    fun `erros de pagina e limite vem juntos numa resposta so`() {
        val ex = assertFailsWith<BadRequestException> {
            ParametrosPaginacao.de(pagina = 0, limite = 0)
        }
        assertEquals(2, ex.details.size)
    }
}
