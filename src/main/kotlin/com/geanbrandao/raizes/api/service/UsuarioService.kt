package com.geanbrandao.raizes.api.service

import com.geanbrandao.raizes.api.domain.Perfil
import com.geanbrandao.raizes.api.dto.CadastroClienteRequest
import com.geanbrandao.raizes.api.dto.CadastroOperadorRequest
import com.geanbrandao.raizes.api.dto.UsuarioResponse
import com.geanbrandao.raizes.api.entity.ContaFidelidadeEntity
import com.geanbrandao.raizes.api.entity.UsuarioEntity
import com.geanbrandao.raizes.api.exception.ConflitoException
import com.geanbrandao.raizes.api.exception.ErrorCodes
import com.geanbrandao.raizes.api.exception.ErrorDetail
import com.geanbrandao.raizes.api.exception.NaoEncontradoException
import com.geanbrandao.raizes.api.exception.SemPermissaoException
import com.geanbrandao.raizes.api.exception.ValidacaoException
import com.geanbrandao.raizes.api.repository.ContaFidelidadeRepository
import com.geanbrandao.raizes.api.repository.UnidadeRepository
import com.geanbrandao.raizes.api.repository.UsuarioRepository
import com.geanbrandao.raizes.api.security.UsuarioAutenticado
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Regras de cadastro e consulta de usuarios.
 */
@Service
class UsuarioService(
    private val usuarioRepository: UsuarioRepository,
    private val unidadeRepository: UnidadeRepository,
    private val contaFidelidadeRepository: ContaFidelidadeRepository,
    private val verificacaoEmailService: VerificacaoEmailService,
    private val passwordEncoder: PasswordEncoder,
) {
    private val logger = LoggerFactory.getLogger(UsuarioService::class.java)

    /**
     * Cadastra um cliente e dispara o codigo de verificação.
     *
     * O metodo não devolve nada e nunca lança erro de e-mail duplicado. Quem chama
     * recebe sempre a mesma resposta, exista ou não a conta. Sem isso, o cadastro
     * seria a forma mais comoda de descobrir quem tem conta na rede: bastaria
     * tentar registrar uma lista de enderecos e anotar quais deram conflito.
     *
     * O hash da senha e calculado antes da consulta de proposito. BCrypt custa
     * dezenas de milissegundos, e se ele rodasse so no caminho de e-mail novo, o
     * tempo de resposta entregaria a mesma informação que a mensagem esconde.
     *
     * A conta de fidelidade ja nasce junto, mas inativa. Ela so passa a acumular
     * quando o cliente der consentimento, o que acontece em outro endpoint.
     *
     * @param request Dados do cadastro.
     */
    @Transactional
    fun cadastrarCliente(request: CadastroClienteRequest) {
        val email = request.email.trim().lowercase()

        // Sempre roda, nos dois caminhos, para o tempo de resposta ficar igual.
        val senhaHash = passwordEncoder.encode(request.senha)

        if (usuarioRepository.existsByEmail(email)) {
            // Conta ja existe: nada e criado e nada e dito. Em produção, o certo
            // aqui seria avisar o dono do endereco que alguem tentou se cadastrar
            // com o e-mail dele.
            logger.info("Cadastro tentado em e-mail ja existente; resposta generica devolvida")
            return
        }

        val usuario = usuarioRepository.save(
            UsuarioEntity(
                nome = request.nome.trim(),
                email = email,
                senhaHash = senhaHash,
                perfil = Perfil.CLIENTE,
                unidadeId = null,
                dataNascimento = request.dataNascimento,
                emailVerificado = false,
            ),
        )

        contaFidelidadeRepository.save(
            ContaFidelidadeEntity(clienteId = usuario.id, saldoPontos = 0, ativa = false),
        )

        verificacaoEmailService.emitirCodigo(usuario)
    }

    /**
     * Cadastra um operador vinculado a uma unidade.
     *
     * Duas regras de permissão moram aqui: gerente so cadastra gente na propria
     * unidade, e ninguem cria ADMIN ou CLIENTE por esta rota. Sem a primeira, um
     * gerente conseguiria criar acesso numa loja que não e dele.
     *
     * @param request Dados do operador.
     * @param solicitante Quem esta cadastrando.
     * @return Operador criado.
     * @throws ValidacaoException se o perfil pedido não for de operador.
     * @throws SemPermissaoException se um gerente tentar cadastrar em outra unidade.
     * @throws NaoEncontradoException se a unidade não existir.
     * @throws ConflitoException se o e-mail ja estiver em uso.
     */
    @Transactional
    fun cadastrarOperador(
        request: CadastroOperadorRequest,
        solicitante: UsuarioAutenticado,
    ): UsuarioResponse {
        if (!request.perfil.ehOperador) {
            throw ValidacaoException(
                message = "Esta rota so cadastra operador de unidade.",
                details = listOf(
                    ErrorDetail("perfil", "aceitos: GERENTE, ATENDENTE, COZINHA"),
                ),
            )
        }

        // Gerente e limitado a propria loja. Admin nao tem essa restricao.
        if (!solicitante.podeAcessarUnidade(request.unidadeId)) {
            throw SemPermissaoException(
                message = "Voce so pode cadastrar operadores na sua unidade.",
            )
        }

        if (!unidadeRepository.existsById(request.unidadeId)) {
            throw NaoEncontradoException(
                error = ErrorCodes.UNIDADE_NAO_ENCONTRADA,
                message = "Unidade não encontrada.",
            )
        }

        val email = request.email.trim().lowercase()
        if (usuarioRepository.existsByEmail(email)) {
            // Aqui o 409 explicito pode: quem chama ja e admin ou gerente
            // autenticado, entao não ha enumeração a evitar, e esconder o motivo so
            // atrapalharia quem esta cadastrando a equipe.
            throw ConflitoException(
                error = ErrorCodes.EMAIL_JA_CADASTRADO,
                message = "Ja existe uma conta com este e-mail.",
                details = listOf(ErrorDetail("email", "ja cadastrado")),
            )
        }

        return usuarioRepository.save(
            UsuarioEntity(
                nome = request.nome.trim(),
                email = email,
                senhaHash = passwordEncoder.encode(request.senha),
                perfil = request.perfil,
                unidadeId = request.unidadeId,
                // Operador e criado por alguem de confiança, ja autenticado, entao
                // não precisa confirmar e-mail para começar a trabalhar.
                emailVerificado = true,
            ),
        ).paraResponse()
    }

    /**
     * Busca o perfil de quem esta autenticado.
     *
     * @param usuarioId Id vindo do token.
     * @return Dados do usuario.
     * @throws NaoEncontradoException se a conta tiver sido removida depois do token ser emitido.
     */
    @Transactional(readOnly = true)
    fun buscarPorId(usuarioId: UUID): UsuarioResponse = usuarioRepository.findById(usuarioId)
        .orElseThrow { NaoEncontradoException(message = "Usuario não encontrado.") }
        .paraResponse()

    /** Converte a entidade no DTO de saida, deixando o hash de senha de fora. */
    private fun UsuarioEntity.paraResponse() = UsuarioResponse(
        id = id,
        nome = nome,
        email = email,
        perfil = perfil,
        unidadeId = unidadeId,
        dataNascimento = dataNascimento,
        ativo = ativo,
    )
}
