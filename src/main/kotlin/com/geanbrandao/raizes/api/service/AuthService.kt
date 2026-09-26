package com.geanbrandao.raizes.api.service

import com.geanbrandao.raizes.api.dto.LoginRequest
import com.geanbrandao.raizes.api.dto.LoginResponse
import com.geanbrandao.raizes.api.dto.UsuarioResumoResponse
import com.geanbrandao.raizes.api.entity.RefreshTokenEntity
import com.geanbrandao.raizes.api.entity.UsuarioEntity
import com.geanbrandao.raizes.api.exception.ApiException
import com.geanbrandao.raizes.api.exception.ErrorCodes
import com.geanbrandao.raizes.api.repository.RefreshTokenRepository
import com.geanbrandao.raizes.api.repository.UsuarioRepository
import com.geanbrandao.raizes.api.security.JwtService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/**
 * Regras de autenticação: login, renovação e encerramento de sessão.
 */
@Service
class AuthService(
    private val usuarioRepository: UsuarioRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${jwt.refresh-token-expiration}") private val refreshExpiracaoMs: Long,
) {
    private val logger = LoggerFactory.getLogger(AuthService::class.java)

    /**
     * Autentica e devolve os tokens.
     *
     * Repare que e-mail inexistente e senha errada devolvem exatamente o mesmo erro.
     * E de proposito: se a API dissesse "e-mail não cadastrado", qualquer pessoa
     * conseguiria descobrir quem tem conta na rede so testando enderecos.
     *
     * @param request E-mail e senha.
     * @return Tokens e dados basicos do usuario.
     * @throws ApiException 401 se as credenciais não conferirem, ou se a conta estiver inativa.
     */
    @Transactional
    fun login(request: LoginRequest): LoginResponse {
        val usuario = usuarioRepository.findByEmail(request.email.trim().lowercase())

        if (usuario == null || !passwordEncoder.matches(request.senha, usuario.senhaHash)) {
            logger.warn("Tentativa de login sem sucesso")
            throw ApiException(
                error = ErrorCodes.CREDENCIAIS_INVALIDAS,
                message = "E-mail ou senha invalidos.",
                status = HttpStatus.UNAUTHORIZED,
            )
        }

        if (!usuario.ativo) {
            throw ApiException(
                error = ErrorCodes.USUARIO_INATIVO,
                message = "Esta conta esta desativada.",
                status = HttpStatus.UNAUTHORIZED,
            )
        }

        return montarResposta(usuario)
    }

    /**
     * Troca um refresh token valido por um par novo de tokens.
     *
     * O refresh antigo e revogado na hora (rotação). Se alguem roubar um refresh e
     * usar, o token do dono para de funcionar e o problema aparece, em vez de o
     * invasor ficar renovando acesso para sempre em silencio.
     *
     * @param refreshToken Token opaco recebido no login.
     * @return Novo par de tokens.
     * @throws ApiException 401 se o token não existir, estiver revogado ou vencido.
     */
    @Transactional
    fun renovar(refreshToken: String): LoginResponse {
        val armazenado = refreshTokenRepository.findByToken(refreshToken)
            ?: throw tokenInvalido()

        if (!armazenado.estaValido()) throw tokenInvalido()

        val usuario = usuarioRepository.findById(armazenado.usuarioId).orElseThrow { tokenInvalido() }
        if (!usuario.ativo) throw tokenInvalido()

        armazenado.revogado = true
        refreshTokenRepository.save(armazenado)

        return montarResposta(usuario)
    }

    /**
     * Encerra a sessão revogando o refresh token.
     *
     * O access token continua valendo ate expirar, porque JWT não tem como ser
     * cancelado sem uma lista negra. Como ele dura 15 minutos, a janela e curta.
     *
     * @param usuarioId Dono da sessão.
     */
    @Transactional
    fun logout(usuarioId: UUID) {
        val revogados = refreshTokenRepository.revogarTodosDoUsuario(usuarioId)
        logger.debug("Logout revogou {} refresh token(s)", revogados)
    }

    /** Gera o par de tokens e monta a resposta de login. */
    private fun montarResposta(usuario: UsuarioEntity): LoginResponse {
        val accessToken = jwtService.gerarToken(usuario)
        val refreshToken = refreshTokenRepository.save(
            RefreshTokenEntity(
                usuarioId = usuario.id,
                token = UUID.randomUUID().toString(),
                expiraEm = LocalDateTime.now().plusSeconds(refreshExpiracaoMs / 1000),
            ),
        )

        return LoginResponse(
            accessToken = accessToken,
            refreshToken = refreshToken.token,
            expiresIn = jwtService.expiracaoEmSegundos(),
            usuario = UsuarioResumoResponse(
                id = usuario.id,
                nome = usuario.nome,
                email = usuario.email,
                perfil = usuario.perfil,
                unidadeId = usuario.unidadeId,
            ),
        )
    }

    private fun tokenInvalido() = ApiException(
        error = ErrorCodes.TOKEN_INVALIDO,
        message = "Refresh token invalido ou expirado. Faça login novamente.",
        status = HttpStatus.UNAUTHORIZED,
    )
}
