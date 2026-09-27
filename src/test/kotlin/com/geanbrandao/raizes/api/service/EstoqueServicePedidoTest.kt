package com.geanbrandao.raizes.api.service

import com.geanbrandao.raizes.api.domain.TipoMovimentacaoEstoque
import com.geanbrandao.raizes.api.exception.ConflitoException
import com.geanbrandao.raizes.api.exception.ErrorCodes
import com.geanbrandao.raizes.api.repository.EstoqueRepository
import com.geanbrandao.raizes.api.repository.MovimentacaoEstoqueRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Testes das operações que o fluxo de pedido usa: baixa e devolução de estoque.
 *
 * Ainda não existe endpoint de pedido, então elas são exercitadas direto no service.
 * Vale testar agora em vez de esperar a Etapa 6: são o ponto onde a regra de estoque
 * insuficiente vive, e um erro aqui aparece disfarçado de bug de pedido depois.
 */
@SpringBootTest
@ActiveProfiles("integracao")
@Transactional
class EstoqueServicePedidoTest {

    @Autowired
    lateinit var estoqueService: EstoqueService

    @Autowired
    lateinit var estoqueRepository: EstoqueRepository

    @Autowired
    lateinit var movimentacaoRepository: MovimentacaoEstoqueRepository

    private val recife = UUID.fromString("10000000-0000-0000-0000-000000000001")
    private val tapioca = UUID.fromString("30000000-0000-0000-0000-000000000001")
    private val cuscuz = UUID.fromString("30000000-0000-0000-0000-000000000003")
    private val boloDeRolo = UUID.fromString("30000000-0000-0000-0000-000000000006")
    private val cliente = UUID.fromString("20000000-0000-0000-0000-000000000005")

    private fun saldo(produtoId: UUID) =
        estoqueRepository.findByUnidadeIdAndProdutoId(recife, produtoId)!!.saldoAtual

    @Test
    fun `baixa varios itens de uma vez e registra o pedido em cada movimentacao`() {
        val pedidoId = UUID.randomUUID()

        estoqueService.debitarParaPedido(recife, mapOf(tapioca to 2, cuscuz to 3), pedidoId, cliente)

        assertEquals(48, saldo(tapioca))
        assertEquals(47, saldo(cuscuz))

        val movimentacoes = movimentacaoRepository.findAllByPedidoId(pedidoId)
        assertEquals(2, movimentacoes.size)
        assertTrue(movimentacoes.all { it.tipo == TipoMovimentacaoEstoque.SAIDA })
        assertTrue(movimentacoes.all { it.usuarioId == cliente })
    }

    @Test
    fun `estoque insuficiente barra o pedido inteiro e nao baixa nada`() {
        val saldoTapiocaAntes = saldo(tapioca)

        val ex = assertFailsWith<ConflitoException> {
            // Tapioca tem 50 e passa; bolo de rolo tem 2 e não passa.
            estoqueService.debitarParaPedido(
                recife,
                mapOf(tapioca to 1, boloDeRolo to 10),
                UUID.randomUUID(),
                cliente,
            )
        }

        assertEquals(ErrorCodes.ESTOQUE_INSUFICIENTE, ex.error)
        // O item que tinha saldo tambem não pode ter sido baixado: ou vai tudo, ou nada.
        assertEquals(saldoTapiocaAntes, saldo(tapioca))
    }

    @Test
    fun `resposta lista todos os itens em falta de uma vez`() {
        // Zera o cuscuz para ter dois itens faltando na mesma tentativa.
        val estoqueCuscuz = estoqueRepository.findByUnidadeIdAndProdutoId(recife, cuscuz)!!
        estoqueCuscuz.saldoAtual = 0
        estoqueRepository.save(estoqueCuscuz)

        val ex = assertFailsWith<ConflitoException> {
            estoqueService.debitarParaPedido(
                recife,
                mapOf(boloDeRolo to 10, cuscuz to 5),
                UUID.randomUUID(),
                cliente,
            )
        }

        // Falhar so no primeiro obrigaria o cliente a descobrir os problemas um a um.
        assertEquals(2, ex.details.size)
        assertTrue(ex.details.all { it.issue.contains("disponivel:") })
    }

    @Test
    fun `produto sem linha de estoque conta como saldo zero`() {
        val semEstoque = UUID.fromString("30000000-0000-0000-0000-000000000010")
        // Canjica esta no cardapio de Recife, entao tem estoque; usamos um id de
        // produto que existe mas removemos a linha antes.
        estoqueRepository.findByUnidadeIdAndProdutoId(recife, semEstoque)
            ?.let { estoqueRepository.delete(it) }

        val ex = assertFailsWith<ConflitoException> {
            estoqueService.debitarParaPedido(recife, mapOf(semEstoque to 1), UUID.randomUUID(), cliente)
        }
        assertTrue(ex.details.first().issue.contains("disponivel: 0"))
    }

    @Test
    fun `devolucao repoe exatamente o que o pedido tinha baixado`() {
        val pedidoId = UUID.randomUUID()
        val antes = saldo(tapioca)

        estoqueService.debitarParaPedido(recife, mapOf(tapioca to 4), pedidoId, cliente)
        assertEquals(antes - 4, saldo(tapioca))

        estoqueService.devolverDoPedido(pedidoId, cliente)
        assertEquals(antes, saldo(tapioca))

        // A devolução não apaga a saida: as duas linhas ficam no historico.
        val movimentacoes = movimentacaoRepository.findAllByPedidoId(pedidoId)
        assertEquals(2, movimentacoes.size)
        assertEquals(1, movimentacoes.count { it.tipo == TipoMovimentacaoEstoque.SAIDA })
        assertEquals(1, movimentacoes.count { it.tipo == TipoMovimentacaoEstoque.ENTRADA })
    }

    @Test
    fun `devolver pedido que nunca baixou estoque nao faz nada`() {
        val antes = saldo(tapioca)
        estoqueService.devolverDoPedido(UUID.randomUUID(), cliente)
        assertEquals(antes, saldo(tapioca))
    }

    @Test
    fun `baixa sem itens nao quebra`() {
        val antes = saldo(tapioca)
        estoqueService.debitarParaPedido(recife, emptyMap(), UUID.randomUUID(), cliente)
        assertEquals(antes, saldo(tapioca))
    }
}
