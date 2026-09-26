package com.geanbrandao.raizes.api.config

import com.geanbrandao.raizes.api.domain.Perfil
import com.geanbrandao.raizes.api.domain.StatusPedido
import com.geanbrandao.raizes.api.repository.CardapioUnidadeRepository
import com.geanbrandao.raizes.api.repository.EstoqueRepository
import com.geanbrandao.raizes.api.repository.ProdutoRepository
import com.geanbrandao.raizes.api.repository.UnidadeRepository
import com.geanbrandao.raizes.api.repository.UsuarioRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Confere se as migrations do Flyway e as entidades JPA contam a mesma historia.
 *
 * O teste sobe o contexto inteiro com ddl-auto=validate. Se alguma entidade tiver
 * coluna que não existe no banco, ou nome diferente, o contexto nem sobe. E a forma
 * mais barata de garantir que o DER, as migrations e o codigo continuam alinhados.
 *
 * De quebra confere o seed, que a coleção de testes da API depende para rodar.
 */
@SpringBootTest
@ActiveProfiles("schema-check")
class SchemaMigrationTest {

    @Autowired
    lateinit var unidadeRepository: UnidadeRepository

    @Autowired
    lateinit var usuarioRepository: UsuarioRepository

    @Autowired
    lateinit var produtoRepository: ProdutoRepository

    @Autowired
    lateinit var cardapioRepository: CardapioUnidadeRepository

    @Autowired
    lateinit var estoqueRepository: EstoqueRepository

    private val unidadeRecife = UUID.fromString("10000000-0000-0000-0000-000000000001")
    private val unidadeCaruaru = UUID.fromString("10000000-0000-0000-0000-000000000002")
    private val produtoBoloDeRolo = UUID.fromString("30000000-0000-0000-0000-000000000006")

    @Test
    fun `contexto sobe com as migrations aplicadas e as entidades validadas`() {
        // Chegar aqui ja significa que o ddl-auto=validate passou.
        assertEquals(2, unidadeRepository.count())
    }

    @Test
    fun `seed cria os usuarios de cada perfil`() {
        assertEquals(1, usuarioRepository.countByPerfil(Perfil.ADMIN))
        assertEquals(2, usuarioRepository.countByPerfil(Perfil.GERENTE))
        assertEquals(1, usuarioRepository.countByPerfil(Perfil.ATENDENTE))
        assertEquals(1, usuarioRepository.countByPerfil(Perfil.COZINHA))
        assertEquals(1, usuarioRepository.countByPerfil(Perfil.CLIENTE))
    }

    @Test
    fun `operador tem unidade vinculada e cliente nao tem`() {
        val atendente = usuarioRepository.findByEmail("atendente.recife@raizes.com.br")
        assertNotNull(atendente)
        assertEquals(unidadeRecife, atendente.unidadeId)

        val cliente = usuarioRepository.findByEmail("cliente@exemplo.com")
        assertNotNull(cliente)
        assertEquals(null, cliente.unidadeId)
    }

    @Test
    fun `cardapio e por unidade e a unidade reduzida vende menos itens`() {
        val recife = cardapioRepository.findAllByUnidadeId(unidadeRecife)
        val caruaru = cardapioRepository.findAllByUnidadeId(unidadeCaruaru)

        assertEquals(10, recife.size)
        assertEquals(6, caruaru.size)
        assertTrue(caruaru.size < recife.size, "unidade REDUZIDA deve vender menos itens")
    }

    @Test
    fun `unidade reduzida pratica preco proprio`() {
        val produtoEmComum = UUID.fromString("30000000-0000-0000-0000-000000000001")
        val emRecife = cardapioRepository.findByUnidadeIdAndProdutoId(unidadeRecife, produtoEmComum)
        val emCaruaru = cardapioRepository.findByUnidadeIdAndProdutoId(unidadeCaruaru, produtoEmComum)

        assertNotNull(emRecife)
        assertNotNull(emCaruaru)
        assertTrue(
            emCaruaru.preco < emRecife.preco,
            "o preco de Caruaru deveria ser menor que o de Recife",
        )
    }

    @Test
    fun `todo item de cardapio tem linha de estoque`() {
        val itensCardapio = cardapioRepository.findAllByUnidadeId(unidadeRecife)
        itensCardapio.forEach { item ->
            val estoque = estoqueRepository.findByUnidadeIdAndProdutoId(unidadeRecife, item.produtoId)
            assertNotNull(estoque, "faltou estoque para o produto ${item.produtoId}")
        }
    }

    @Test
    fun `seed deixa um item com saldo baixo para testar estoque insuficiente`() {
        val estoque = estoqueRepository.findByUnidadeIdAndProdutoId(unidadeRecife, produtoBoloDeRolo)
        assertNotNull(estoque)
        assertEquals(2, estoque.saldoAtual)
    }

    @Test
    fun `catalogo tem produto sazonal`() {
        val sazonais = produtoRepository.findAll().filter { it.sazonal }
        assertEquals(1, sazonais.size)
        assertEquals("Canjica junina", sazonais.first().nome)
    }

    @Test
    fun `maquina de estados do pedido nao deixa voltar de entregue`() {
        assertTrue(StatusPedido.AGUARDANDO_PAGAMENTO.podeIrPara(StatusPedido.PAGO))
        assertTrue(StatusPedido.PAGO.podeIrPara(StatusPedido.EM_PREPARO))
        assertTrue(StatusPedido.PRONTO.podeIrPara(StatusPedido.ENTREGUE))

        assertTrue(!StatusPedido.ENTREGUE.podeIrPara(StatusPedido.EM_PREPARO))
        assertTrue(!StatusPedido.PRONTO.podeIrPara(StatusPedido.CANCELADO))
        assertTrue(!StatusPedido.CANCELADO.podeIrPara(StatusPedido.PAGO))
        assertTrue(StatusPedido.ENTREGUE.ehFinal)
        assertTrue(StatusPedido.CANCELADO.ehFinal)
    }
}
