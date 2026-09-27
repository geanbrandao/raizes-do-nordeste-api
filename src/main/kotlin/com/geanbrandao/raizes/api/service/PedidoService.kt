package com.geanbrandao.raizes.api.service

import com.geanbrandao.raizes.api.domain.CanalPedido
import com.geanbrandao.raizes.api.domain.Perfil
import com.geanbrandao.raizes.api.domain.StatusPedido
import com.geanbrandao.raizes.api.dto.CriarPedidoRequest
import com.geanbrandao.raizes.api.dto.ItemPedidoResponse
import com.geanbrandao.raizes.api.dto.PaginaResponse
import com.geanbrandao.raizes.api.dto.PedidoResponse
import com.geanbrandao.raizes.api.entity.ItemPedidoEntity
import com.geanbrandao.raizes.api.entity.PedidoEntity
import com.geanbrandao.raizes.api.exception.ConflitoException
import com.geanbrandao.raizes.api.exception.ErrorCodes
import com.geanbrandao.raizes.api.exception.ErrorDetail
import com.geanbrandao.raizes.api.exception.NaoEncontradoException
import com.geanbrandao.raizes.api.exception.SemPermissaoException
import com.geanbrandao.raizes.api.exception.ValidacaoException
import com.geanbrandao.raizes.api.repository.CardapioUnidadeRepository
import com.geanbrandao.raizes.api.repository.PedidoRepository
import com.geanbrandao.raizes.api.repository.ProdutoRepository
import com.geanbrandao.raizes.api.security.UsuarioAutenticado
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/**
 * Regras do pedido. E o fluxo critico do sistema.
 *
 * O caminho completo e: cliente escolhe itens de uma unidade, a API confere se cada
 * um esta no cardapio daquela loja, congela o preço praticado ali, baixa o estoque,
 * aplica campanha se houver e cria o pedido aguardando pagamento. Dali em diante o
 * status anda pela maquina de estados ate ser entregue, ou volta atras num
 * cancelamento que devolve o estoque.
 */
@Service
class PedidoService(
    private val pedidoRepository: PedidoRepository,
    private val cardapioRepository: CardapioUnidadeRepository,
    private val produtoRepository: ProdutoRepository,
    private val estoqueService: EstoqueService,
    private val campanhaService: CampanhaService,
    private val unidadeService: UnidadeService,
) {
    private val logger = LoggerFactory.getLogger(PedidoService::class.java)

    /**
     * Cria um pedido.
     *
     * A ordem das etapas importa. Valida o canal e a unidade, resolve os itens contra
     * o cardapio **daquela** loja, monta o pedido com os preços de la, salva para ter
     * id, baixa o estoque (que estoura 409 se faltar) e so entao devolve. Baixar o
     * estoque antes de saber que todos os itens são validos deixaria saldo reservado
     * para pedido que nem chegou a existir.
     *
     * @param request Unidade, canal, itens e, opcionalmente, o cliente.
     * @param solicitante Quem esta criando.
     * @return Pedido criado, em AGUARDANDO_PAGAMENTO.
     * @throws SemPermissaoException se um operador tentar criar pedido em outra unidade.
     * @throws NaoEncontradoException se a unidade não existir.
     * @throws ValidacaoException se algum item não estiver no cardapio da unidade.
     * @throws ConflitoException se faltar estoque para algum item.
     */
    @Transactional
    fun criar(request: CriarPedidoRequest, solicitante: UsuarioAutenticado): PedidoResponse {
        val unidade = unidadeService.buscarEntidade(request.unidadeId)
        if (!unidade.ativa) {
            throw ConflitoException(
                error = ErrorCodes.UNIDADE_INATIVA,
                message = "Esta unidade não esta aceitando pedidos.",
            )
        }

        // Operador so registra pedido na propria loja. Cliente pede em qualquer uma.
        if (solicitante.perfil.ehOperador && !solicitante.podeAcessarUnidade(request.unidadeId)) {
            throw SemPermissaoException(message = "Voce so pode registrar pedidos da sua unidade.")
        }

        val clienteId = resolverCliente(request, solicitante)
        val itensResolvidos = resolverItens(request)

        val pedido = PedidoEntity(
            unidadeId = request.unidadeId,
            clienteId = clienteId,
            canalPedido = request.canalPedido,
            status = StatusPedido.AGUARDANDO_PAGAMENTO,
        )
        itensResolvidos.forEach { pedido.itens.add(it.entidade) }
        pedido.recalcularTotais()

        val desconto = campanhaService.calcularDesconto(
            unidadeId = request.unidadeId,
            canalPedido = request.canalPedido,
            clienteId = clienteId,
            subtotal = pedido.subtotal,
        )
        pedido.desconto = desconto.valor
        pedido.recalcularTotais()

        val salvo = pedidoRepository.save(pedido)

        estoqueService.debitarParaPedido(
            unidadeId = request.unidadeId,
            quantidadePorProduto = itensResolvidos.associate { it.entidade.produtoId to it.entidade.quantidade },
            pedidoId = salvo.id,
            usuarioId = solicitante.id,
        )

        logger.info(
            "Pedido {} criado na unidade {} pelo canal {} no valor de {}",
            salvo.id, request.unidadeId, request.canalPedido, salvo.total,
        )
        return salvo.paraResponse(itensResolvidos.associate { it.entidade.produtoId to it.nome }, desconto.campanha)
    }

    /**
     * Lista pedidos com filtros, respeitando o que cada perfil pode enxergar.
     *
     * O recorte de visibilidade não e opcional nem vem do cliente: cliente ve os
     * pedidos dele, operador ve os da loja dele, admin ve a rede toda. Um filtro de
     * unidade mandado na query nunca amplia o que a pessoa ja podia ver.
     *
     * @param canalPedido Filtro por canal, ou null para todos.
     * @param status Filtro por situação, ou null para todas.
     * @param unidadeId Filtro por unidade, ou null. Ignorado para cliente e operador.
     * @param pageable Pagina e tamanho da pagina.
     * @param solicitante Quem esta consultando.
     * @return Pagina de pedidos visiveis a essa pessoa.
     */
    @Transactional(readOnly = true)
    fun listar(
        canalPedido: CanalPedido?,
        status: StatusPedido?,
        unidadeId: UUID?,
        pageable: Pageable,
        solicitante: UsuarioAutenticado,
    ): PaginaResponse<PedidoResponse> {
        val filtroCliente = if (solicitante.perfil == Perfil.CLIENTE) solicitante.id else null
        val filtroUnidade = when {
            solicitante.perfil.ehOperador -> solicitante.unidadeId
            solicitante.ehAdmin -> unidadeId
            else -> unidadeId
        }

        val pagina = pedidoRepository.buscarComFiltros(
            unidadeId = filtroUnidade,
            clienteId = filtroCliente,
            canalPedido = canalPedido,
            status = status,
            pageable = pageable,
        )

        val nomes = nomesDosProdutos(pagina.content)
        return PaginaResponse.de(pagina) { it.paraResponse(nomes, null) }
    }

    /**
     * Busca um pedido, respeitando a visibilidade.
     *
     * Quando a pessoa não pode ver o pedido, a resposta e 404 e não 403. Dizer "existe
     * mas não e seu" ja confirmaria a existencia do pedido de outra pessoa.
     *
     * @param pedidoId Id do pedido.
     * @param solicitante Quem esta consultando.
     * @return Pedido.
     * @throws NaoEncontradoException se não existir ou não for visivel a essa pessoa.
     */
    @Transactional(readOnly = true)
    fun buscarPorId(pedidoId: UUID, solicitante: UsuarioAutenticado): PedidoResponse {
        val pedido = buscarVisivel(pedidoId, solicitante)
        return pedido.paraResponse(nomesDosProdutos(listOf(pedido)), null)
    }

    /**
     * Avança o status do pedido.
     *
     * Duas travas independentes: a maquina de estados diz se a transição faz sentido,
     * e o perfil diz se aquela pessoa pode fazer justamente aquela transição. Cozinha
     * prepara e marca pronto; atendente entrega; gerente e admin destravam o que
     * precisar.
     *
     * @param pedidoId Id do pedido.
     * @param novoStatus Status desejado.
     * @param solicitante Quem esta mudando.
     * @return Pedido atualizado.
     * @throws ConflitoException se a transição não existir no fluxo.
     * @throws SemPermissaoException se o perfil não puder fazer essa transição.
     */
    @Transactional
    fun atualizarStatus(
        pedidoId: UUID,
        novoStatus: StatusPedido,
        solicitante: UsuarioAutenticado,
    ): PedidoResponse {
        val pedido = buscarVisivel(pedidoId, solicitante)

        if (!pedido.status.podeIrPara(novoStatus)) {
            throw ConflitoException(
                error = ErrorCodes.TRANSICAO_DE_STATUS_INVALIDA,
                message = "Não da para mudar de ${pedido.status} para $novoStatus.",
                details = listOf(
                    ErrorDetail(
                        "status",
                        "a partir de ${pedido.status}, os status possiveis são: " +
                            pedido.status.transicoesPermitidas().joinToString(", "),
                    ),
                ),
            )
        }

        exigirPermissaoParaTransicao(novoStatus, solicitante)

        if (novoStatus == StatusPedido.CANCELADO) {
            return cancelar(pedidoId, solicitante)
        }

        val anterior = pedido.status
        pedido.status = novoStatus
        pedido.atualizadoEm = LocalDateTime.now()
        val salvo = pedidoRepository.save(pedido)

        logger.info("Pedido {} mudou de {} para {}", pedidoId, anterior, novoStatus)
        return salvo.paraResponse(nomesDosProdutos(listOf(salvo)), null)
    }

    /**
     * Cancela o pedido e devolve o estoque.
     *
     * So vale ate PRONTO: depois disso a comida ja foi preparada e devolver o estoque
     * seria mentira contabil.
     *
     * @param pedidoId Id do pedido.
     * @param solicitante Quem esta cancelando.
     * @return Pedido cancelado.
     * @throws ConflitoException se o pedido ja passou do ponto de cancelamento.
     */
    @Transactional
    fun cancelar(pedidoId: UUID, solicitante: UsuarioAutenticado): PedidoResponse {
        val pedido = buscarVisivel(pedidoId, solicitante)

        if (!pedido.status.podeCancelar) {
            throw ConflitoException(
                error = ErrorCodes.PEDIDO_NAO_PODE_SER_CANCELADO,
                message = "Pedido em ${pedido.status} não pode mais ser cancelado.",
            )
        }

        estoqueService.devolverDoPedido(pedidoId, solicitante.id)

        pedido.status = StatusPedido.CANCELADO
        pedido.atualizadoEm = LocalDateTime.now()
        val salvo = pedidoRepository.save(pedido)

        logger.info("Pedido {} cancelado por {}", pedidoId, solicitante.id)
        return salvo.paraResponse(nomesDosProdutos(listOf(salvo)), null)
    }

    // --------------------------------------------------------------- apoio

    /** Item ja resolvido contra o cardapio, com preço e nome congelados. */
    private data class ItemResolvido(val entidade: ItemPedidoEntity, val nome: String)

    /**
     * Confere cada item contra o cardapio da unidade e congela preço e nome.
     *
     * Junta todos os problemas numa resposta so, em vez de parar no primeiro: quem
     * esta integrando corrige tudo de uma vez.
     */
    private fun resolverItens(request: CriarPedidoRequest): List<ItemResolvido> {
        val produtoIds = request.itens.map { it.produtoId }.toSet()
        val cardapio = cardapioRepository
            .findAllByUnidadeIdAndProdutoIdIn(request.unidadeId, produtoIds)
            .associateBy { it.produtoId }
        val produtos = produtoRepository.findAllByIdIn(produtoIds).associateBy { it.id }

        val problemas = mutableListOf<ErrorDetail>()
        val resolvidos = mutableListOf<ItemResolvido>()

        request.itens.forEachIndexed { indice, item ->
            val produto = produtos[item.produtoId]
            val noCardapio = cardapio[item.produtoId]
            when {
                produto == null ->
                    problemas += ErrorDetail("itens[$indice].produtoId", "produto não encontrado")
                noCardapio == null ->
                    problemas += ErrorDetail(
                        "itens[$indice].produtoId",
                        "'${produto.nome}' não faz parte do cardapio desta unidade",
                    )
                !noCardapio.disponivel ->
                    problemas += ErrorDetail(
                        "itens[$indice].produtoId",
                        "'${produto.nome}' esta indisponivel nesta unidade agora",
                    )
                else -> resolvidos += ItemResolvido(
                    entidade = ItemPedidoEntity(
                        produtoId = produto.id,
                        nomeProduto = produto.nome,
                        quantidade = item.quantidade,
                        precoUnitario = noCardapio.preco,
                    ),
                    nome = produto.nome,
                )
            }
        }

        if (problemas.isNotEmpty()) {
            throw ValidacaoException(
                error = ErrorCodes.PRODUTO_FORA_DO_CARDAPIO,
                message = "Um ou mais itens não podem ser pedidos nesta unidade.",
                details = problemas,
            )
        }
        return resolvidos
    }

    /**
     * Decide de quem e o pedido.
     *
     * Cliente logado nunca escolhe: o pedido sai no nome dele, mesmo que mande outro
     * id no corpo. So operador pode registrar pedido em nome de terceiro, que e o caso
     * do balcão.
     */
    private fun resolverCliente(
        request: CriarPedidoRequest,
        solicitante: UsuarioAutenticado,
    ): UUID? = when {
        solicitante.perfil == Perfil.CLIENTE -> solicitante.id
        else -> request.clienteId
    }

    /** Cada perfil so pode empurrar o pedido para os status que são trabalho dele. */
    private fun exigirPermissaoParaTransicao(
        novoStatus: StatusPedido,
        solicitante: UsuarioAutenticado,
    ) {
        val permitidos: Set<StatusPedido> = when (solicitante.perfil) {
            Perfil.COZINHA -> setOf(StatusPedido.EM_PREPARO, StatusPedido.PRONTO)
            Perfil.ATENDENTE -> setOf(
                StatusPedido.EM_PREPARO, StatusPedido.PRONTO,
                StatusPedido.ENTREGUE, StatusPedido.CANCELADO,
            )
            Perfil.GERENTE, Perfil.ADMIN -> StatusPedido.entries.toSet()
            Perfil.CLIENTE -> setOf(StatusPedido.CANCELADO)
        }
        if (novoStatus !in permitidos) {
            throw SemPermissaoException(
                message = "Seu perfil não pode mover o pedido para $novoStatus.",
            )
        }
    }

    /**
     * Busca o pedido aplicando a visibilidade do perfil.
     *
     * Devolve 404 tanto para pedido inexistente quanto para pedido de outra pessoa.
     */
    private fun buscarVisivel(pedidoId: UUID, solicitante: UsuarioAutenticado): PedidoEntity {
        val pedido = pedidoRepository.findById(pedidoId).orElseThrow { pedidoNaoEncontrado() }

        val podeVer = when {
            solicitante.ehAdmin -> true
            solicitante.perfil.ehOperador -> solicitante.unidadeId == pedido.unidadeId
            else -> pedido.clienteId == solicitante.id
        }
        if (!podeVer) throw pedidoNaoEncontrado()
        return pedido
    }

    private fun pedidoNaoEncontrado() = NaoEncontradoException(
        error = ErrorCodes.PEDIDO_NAO_ENCONTRADO,
        message = "Pedido não encontrado.",
    )

    /** Nome atual dos produtos, so para enriquecer a resposta das consultas. */
    private fun nomesDosProdutos(pedidos: List<PedidoEntity>): Map<UUID, String> =
        pedidos.flatMap { it.itens }.associate { it.produtoId to it.nomeProduto }

    private fun PedidoEntity.paraResponse(
        nomes: Map<UUID, String>,
        campanha: String?,
    ) = PedidoResponse(
        id = id,
        unidadeId = unidadeId,
        clienteId = clienteId,
        canalPedido = canalPedido,
        status = status,
        itens = itens.map {
            ItemPedidoResponse(
                produtoId = it.produtoId,
                nome = nomes[it.produtoId] ?: it.nomeProduto,
                quantidade = it.quantidade,
                precoUnitario = it.precoUnitario,
                subtotal = it.subtotal,
            )
        },
        subtotal = subtotal,
        desconto = desconto,
        total = total,
        campanhaAplicada = campanha,
        proximosStatus = status.transicoesPermitidas().toList(),
        criadoEm = criadoEm,
        atualizadoEm = atualizadoEm,
    )
}
