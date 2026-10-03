package com.geanbrandao.raizes.api.service

import com.geanbrandao.raizes.api.domain.TipoMovimentacaoEstoque
import com.geanbrandao.raizes.api.dto.EstoqueResponse
import com.geanbrandao.raizes.api.dto.MovimentacaoEstoqueRequest
import com.geanbrandao.raizes.api.dto.MovimentacaoEstoqueResponse
import com.geanbrandao.raizes.api.dto.PaginaResponse
import com.geanbrandao.raizes.api.entity.EstoqueEntity
import com.geanbrandao.raizes.api.entity.MovimentacaoEstoqueEntity
import com.geanbrandao.raizes.api.entity.ProdutoEntity
import com.geanbrandao.raizes.api.exception.ConflitoException
import com.geanbrandao.raizes.api.exception.ErrorCodes
import com.geanbrandao.raizes.api.exception.ErrorDetail
import com.geanbrandao.raizes.api.exception.SemPermissaoException
import com.geanbrandao.raizes.api.exception.ValidacaoException
import com.geanbrandao.raizes.api.repository.EstoqueRepository
import com.geanbrandao.raizes.api.repository.MovimentacaoEstoqueRepository
import com.geanbrandao.raizes.api.repository.ProdutoRepository
import com.geanbrandao.raizes.api.security.UsuarioAutenticado
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/**
 * Regras de estoque por unidade.
 *
 * Duas responsabilidades convivem aqui. A primeira e a operação do dia a dia da loja:
 * consultar saldo e registrar entrada, saida e ajuste. A segunda e servir o fluxo de
 * pedido, que precisa baixar varios itens de uma vez e devolver tudo se o pedido for
 * cancelado — e essas duas operações são o coração da regra de estoque insuficiente.
 */
@Service
class EstoqueService(
    private val estoqueRepository: EstoqueRepository,
    private val movimentacaoRepository: MovimentacaoEstoqueRepository,
    private val produtoRepository: ProdutoRepository,
    private val unidadeService: UnidadeService,
    private val produtoService: ProdutoService,
    private val auditoriaService: AuditoriaService,
) {
    private val logger = LoggerFactory.getLogger(EstoqueService::class.java)

    // ------------------------------------------------------ operação da loja

    /**
     * Lista o saldo de todos os produtos de uma unidade.
     *
     * @param unidadeId Unidade consultada.
     * @param pageable Pagina e tamanho da pagina.
     * @param solicitante Quem esta consultando.
     * @return Pagina de saldos.
     * @throws SemPermissaoException se a pessoa não for da unidade.
     */
    @Transactional(readOnly = true)
    fun listarSaldos(
        unidadeId: UUID,
        pageable: Pageable,
        solicitante: UsuarioAutenticado,
    ): PaginaResponse<EstoqueResponse> {
        exigirAcessoAUnidade(unidadeId, solicitante)
        unidadeService.buscarEntidade(unidadeId)

        val pagina = estoqueRepository.findAllByUnidadeId(unidadeId, pageable)
        // Uma consulta so para os produtos da pagina, em vez de uma por linha.
        val produtos = produtoRepository
            .findAllByIdIn(pagina.content.map { it.produtoId })
            .associateBy { it.id }

        return PaginaResponse.de(pagina) { estoque ->
            montarSaldo(estoque, produtos[estoque.produtoId])
        }
    }

    /**
     * Registra uma movimentação e atualiza o saldo.
     *
     * Se ainda não existe linha de estoque para aquele produto na unidade, ela e
     * criada na hora. Isso acontece quando a loja passa a vender um produto novo: o
     * cardapio ganha o item e a primeira entrada abre o saldo.
     *
     * @param unidadeId Unidade a movimentar.
     * @param request Produto, tipo, quantidade e motivo.
     * @param solicitante Quem esta registrando.
     * @return Movimentação registrada, ja com o saldo resultante.
     * @throws SemPermissaoException se a pessoa não for da unidade.
     * @throws ConflitoException se a saida deixaria o saldo negativo.
     */
    @Transactional
    fun movimentar(
        unidadeId: UUID,
        request: MovimentacaoEstoqueRequest,
        solicitante: UsuarioAutenticado,
    ): MovimentacaoEstoqueResponse {
        exigirAcessoAUnidade(unidadeId, solicitante)
        unidadeService.buscarEntidade(unidadeId)
        val produto = produtoService.buscarEntidade(request.produtoId)

        // Zero so faz sentido em AJUSTE, onde a quantidade e o saldo resultante.
        // Entrada ou saida de zero não movimenta nada e so sujaria o historico.
        if (request.tipo != TipoMovimentacaoEstoque.AJUSTE && request.quantidade < 1) {
            throw ValidacaoException(
                message = "Entrada e saida precisam de quantidade maior que zero.",
                details = listOf(
                    ErrorDetail("quantidade", "use AJUSTE para zerar o saldo"),
                ),
            )
        }

        val estoque = estoqueRepository.findByUnidadeIdAndProdutoId(unidadeId, request.produtoId)
            ?: EstoqueEntity(unidadeId = unidadeId, produtoId = request.produtoId)

        val saldoAnterior = estoque.saldoAtual
        val saldoNovo = when (request.tipo) {
            TipoMovimentacaoEstoque.ENTRADA -> estoque.saldoAtual + request.quantidade
            TipoMovimentacaoEstoque.SAIDA -> {
                if (!estoque.temSaldoPara(request.quantidade)) {
                    throw estoqueInsuficiente(produto, request.quantidade, estoque.saldoAtual)
                }
                estoque.saldoAtual - request.quantidade
            }
            // Contagem de inventario: a quantidade informada vira o saldo.
            TipoMovimentacaoEstoque.AJUSTE -> request.quantidade
        }

        estoque.saldoAtual = saldoNovo
        estoque.atualizadoEm = LocalDateTime.now()
        val salvo = estoqueRepository.save(estoque)

        val movimentacao = movimentacaoRepository.save(
            MovimentacaoEstoqueEntity(
                estoqueId = salvo.id,
                tipo = request.tipo,
                quantidade = request.quantidade,
                saldoApos = saldoNovo,
                motivo = request.motivo?.trim(),
                usuarioId = solicitante.id,
            ),
        )

        auditoriaService.registrar(
            acao = AuditoriaService.Acoes.ESTOQUE_MOVIMENTADO,
            entidade = AuditoriaService.Entidades.ESTOQUE,
            entidadeId = salvo.id,
            usuarioId = solicitante.id,
            antes = mapOf("saldo" to saldoAnterior),
            depois = mapOf(
                "saldo" to saldoNovo,
                "tipo" to request.tipo.name,
                "quantidade" to request.quantidade,
                "produto" to produto.nome,
            ),
        )

        return montarMovimentacao(movimentacao, produto)
    }

    /**
     * Histórico de movimentações de um produto numa unidade.
     *
     * @param unidadeId Unidade consultada.
     * @param produtoId Produto consultado.
     * @param pageable Pagina e tamanho da pagina.
     * @param solicitante Quem esta consultando.
     * @return Pagina de movimentações, da mais recente para a mais antiga.
     */
    @Transactional(readOnly = true)
    fun listarMovimentacoes(
        unidadeId: UUID,
        produtoId: UUID,
        pageable: Pageable,
        solicitante: UsuarioAutenticado,
    ): PaginaResponse<MovimentacaoEstoqueResponse> {
        exigirAcessoAUnidade(unidadeId, solicitante)
        val produto = produtoService.buscarEntidade(produtoId)
        val estoque = buscarEstoque(unidadeId, produtoId)

        return PaginaResponse.de(
            movimentacaoRepository.findAllByEstoqueIdOrderByCriadoEmDesc(estoque.id, pageable),
        ) { montarMovimentacao(it, produto) }
    }

    // ------------------------------------------------------- fluxo de pedido

    /**
     * Baixa do estoque os itens de um pedido.
     *
     * Confere **todos** os itens antes de baixar qualquer um. Falhar no primeiro item
     * sem saldo obrigaria o cliente a descobrir os problemas um a um, a cada tentativa;
     * assim a resposta ja lista tudo o que faltou de uma vez.
     *
     * @param unidadeId Unidade do pedido.
     * @param quantidadePorProduto Quanto baixar de cada produto.
     * @param pedidoId Pedido que originou a baixa, para rastreabilidade.
     * @param usuarioId Quem criou o pedido.
     * @throws ConflitoException se faltar saldo em um ou mais itens.
     */
    @Transactional
    fun debitarParaPedido(
        unidadeId: UUID,
        quantidadePorProduto: Map<UUID, Int>,
        pedidoId: UUID,
        usuarioId: UUID?,
    ) {
        if (quantidadePorProduto.isEmpty()) return

        val estoques = estoqueRepository
            .findAllByUnidadeIdAndProdutoIdIn(unidadeId, quantidadePorProduto.keys)
            .associateBy { it.produtoId }
        val produtos = produtoRepository
            .findAllByIdIn(quantidadePorProduto.keys)
            .associateBy { it.id }

        val faltantes = quantidadePorProduto.mapNotNull { (produtoId, quantidade) ->
            val disponivel = estoques[produtoId]?.saldoAtual ?: 0
            if (disponivel < quantidade) {
                val nome = produtos[produtoId]?.nome ?: produtoId.toString()
                ErrorDetail(
                    field = "itens[$nome].quantidade",
                    issue = "pedido: $quantidade, disponivel: $disponivel",
                )
            } else {
                null
            }
        }

        if (faltantes.isNotEmpty()) {
            logger.info("Pedido {} barrado por estoque insuficiente em {} item(ns)", pedidoId, faltantes.size)
            throw ConflitoException(
                error = ErrorCodes.ESTOQUE_INSUFICIENTE,
                message = "Não ha quantidade suficiente para um ou mais itens.",
                details = faltantes,
            )
        }

        quantidadePorProduto.forEach { (produtoId, quantidade) ->
            val estoque = estoques.getValue(produtoId)
            estoque.saldoAtual -= quantidade
            estoque.atualizadoEm = LocalDateTime.now()
            estoqueRepository.save(estoque)

            movimentacaoRepository.save(
                MovimentacaoEstoqueEntity(
                    estoqueId = estoque.id,
                    tipo = TipoMovimentacaoEstoque.SAIDA,
                    quantidade = quantidade,
                    saldoApos = estoque.saldoAtual,
                    motivo = "Baixa por pedido",
                    pedidoId = pedidoId,
                    usuarioId = usuarioId,
                ),
            )
        }
    }

    /**
     * Devolve ao estoque o que um pedido cancelado havia baixado.
     *
     * Em vez de recalcular a partir dos itens do pedido, le as movimentações de saida
     * que aquele pedido gerou. Assim a devolução espelha exatamente o que saiu, mesmo
     * que o pedido tenha sido alterado no meio do caminho.
     *
     * @param pedidoId Pedido cancelado.
     * @param usuarioId Quem cancelou.
     */
    @Transactional
    fun devolverDoPedido(pedidoId: UUID, usuarioId: UUID?) {
        val saidas = movimentacaoRepository.findAllByPedidoId(pedidoId)
            .filter { it.tipo == TipoMovimentacaoEstoque.SAIDA }
        if (saidas.isEmpty()) return

        val estoques = estoqueRepository.findAllById(saidas.map { it.estoqueId })
            .associateBy { it.id }

        saidas.forEach { saida ->
            val estoque = estoques[saida.estoqueId] ?: return@forEach
            estoque.saldoAtual += saida.quantidade
            estoque.atualizadoEm = LocalDateTime.now()
            estoqueRepository.save(estoque)

            movimentacaoRepository.save(
                MovimentacaoEstoqueEntity(
                    estoqueId = estoque.id,
                    tipo = TipoMovimentacaoEstoque.ENTRADA,
                    quantidade = saida.quantidade,
                    saldoApos = estoque.saldoAtual,
                    motivo = "Devolução por cancelamento de pedido",
                    pedidoId = pedidoId,
                    usuarioId = usuarioId,
                ),
            )
        }
        logger.info("Pedido {} cancelado: {} item(ns) devolvidos ao estoque", pedidoId, saidas.size)
    }

    // ------------------------------------------------------------- apoio

    /**
     * Busca a linha de estoque ou estoura 404.
     *
     * @param unidadeId Unidade.
     * @param produtoId Produto.
     * @return Linha de estoque.
     */
    @Transactional(readOnly = true)
    fun buscarEstoque(unidadeId: UUID, produtoId: UUID): EstoqueEntity =
        estoqueRepository.findByUnidadeIdAndProdutoId(unidadeId, produtoId)
            ?: throw com.geanbrandao.raizes.api.exception.NaoEncontradoException(
                error = ErrorCodes.ESTOQUE_NAO_ENCONTRADO,
                message = "Este produto não tem estoque cadastrado nesta unidade.",
            )

    /**
     * Garante que quem esta chamando pertence a unidade.
     *
     * Vem antes de qualquer busca: confirmar que a unidade existe para quem não tem
     * acesso a ela ja seria informação demais.
     */
    private fun exigirAcessoAUnidade(unidadeId: UUID, solicitante: UsuarioAutenticado) {
        if (!solicitante.podeAcessarUnidade(unidadeId)) {
            throw SemPermissaoException(
                message = "Voce so pode acessar o estoque da sua unidade.",
            )
        }
    }

    private fun estoqueInsuficiente(produto: ProdutoEntity, pedido: Int, disponivel: Int) =
        ConflitoException(
            error = ErrorCodes.ESTOQUE_INSUFICIENTE,
            message = "Não ha quantidade suficiente em estoque.",
            details = listOf(
                ErrorDetail(
                    field = "quantidade",
                    issue = "pedido: $pedido, disponivel: $disponivel (${produto.nome})",
                ),
            ),
        )

    private fun montarSaldo(estoque: EstoqueEntity, produto: ProdutoEntity?) = EstoqueResponse(
        produtoId = estoque.produtoId,
        nome = produto?.nome ?: "(produto removido)",
        categoria = produto?.categoria ?: "-",
        saldoAtual = estoque.saldoAtual,
        saldoMinimo = estoque.saldoMinimo,
        abaixoDoMinimo = estoque.saldoAtual <= estoque.saldoMinimo,
    )

    private fun montarMovimentacao(
        movimentacao: MovimentacaoEstoqueEntity,
        produto: ProdutoEntity,
    ) = MovimentacaoEstoqueResponse(
        id = movimentacao.id,
        produtoId = produto.id,
        nome = produto.nome,
        tipo = movimentacao.tipo,
        quantidade = movimentacao.quantidade,
        saldoApos = movimentacao.saldoApos,
        motivo = movimentacao.motivo,
        pedidoId = movimentacao.pedidoId,
        criadoEm = movimentacao.criadoEm,
    )
}
