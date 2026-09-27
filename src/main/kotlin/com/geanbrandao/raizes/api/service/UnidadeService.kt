package com.geanbrandao.raizes.api.service

import com.geanbrandao.raizes.api.dto.PaginaResponse
import com.geanbrandao.raizes.api.dto.UnidadeRequest
import com.geanbrandao.raizes.api.dto.UnidadeResponse
import com.geanbrandao.raizes.api.entity.UnidadeEntity
import com.geanbrandao.raizes.api.exception.ErrorCodes
import com.geanbrandao.raizes.api.exception.NaoEncontradoException
import com.geanbrandao.raizes.api.repository.UnidadeRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/** Regras de cadastro e consulta das unidades da rede. */
@Service
class UnidadeService(
    private val unidadeRepository: UnidadeRepository,
) {

    /**
     * Lista as unidades ativas.
     *
     * A rota e publica, entao so devolve unidade ativa: loja desativada não interessa
     * a quem esta escolhendo onde pedir.
     *
     * @param pageable Pagina e tamanho da pagina.
     * @return Pagina de unidades ativas.
     */
    @Transactional(readOnly = true)
    fun listarAtivas(pageable: Pageable): PaginaResponse<UnidadeResponse> =
        PaginaResponse.de(unidadeRepository.findAllByAtivaTrue(pageable)) { it.paraResponse() }

    /**
     * Busca uma unidade pelo id.
     *
     * @param id Id da unidade.
     * @return Dados da unidade.
     * @throws NaoEncontradoException se não existir.
     */
    @Transactional(readOnly = true)
    fun buscarPorId(id: UUID): UnidadeResponse = buscarEntidade(id).paraResponse()

    /**
     * Cadastra uma unidade nova.
     *
     * @param request Dados da unidade.
     * @return Unidade criada.
     */
    @Transactional
    fun criar(request: UnidadeRequest): UnidadeResponse = unidadeRepository.save(
        UnidadeEntity(
            nome = request.nome.trim(),
            cidade = request.cidade.trim(),
            uf = request.uf.uppercase(),
            tipoOperacao = request.tipoOperacao,
        ),
    ).paraResponse()

    /**
     * Atualiza os dados de uma unidade.
     *
     * @param id Id da unidade.
     * @param request Dados novos.
     * @return Unidade atualizada.
     * @throws NaoEncontradoException se não existir.
     */
    @Transactional
    fun atualizar(id: UUID, request: UnidadeRequest): UnidadeResponse {
        val unidade = buscarEntidade(id)
        unidade.nome = request.nome.trim()
        unidade.cidade = request.cidade.trim()
        unidade.uf = request.uf.uppercase()
        unidade.tipoOperacao = request.tipoOperacao
        unidade.atualizadoEm = LocalDateTime.now()
        return unidadeRepository.save(unidade).paraResponse()
    }

    /**
     * Busca a entidade ou estoura 404.
     *
     * Usado por outros services que precisam confirmar que a unidade existe antes de
     * mexer em cardapio, estoque ou pedido.
     *
     * @param id Id da unidade.
     * @return Entidade encontrada.
     * @throws NaoEncontradoException se não existir.
     */
    @Transactional(readOnly = true)
    fun buscarEntidade(id: UUID): UnidadeEntity = unidadeRepository.findById(id)
        .orElseThrow {
            NaoEncontradoException(
                error = ErrorCodes.UNIDADE_NAO_ENCONTRADA,
                message = "Unidade não encontrada.",
            )
        }

    private fun UnidadeEntity.paraResponse() = UnidadeResponse(
        id = id,
        nome = nome,
        cidade = cidade,
        uf = uf,
        tipoOperacao = tipoOperacao,
        ativa = ativa,
    )
}
