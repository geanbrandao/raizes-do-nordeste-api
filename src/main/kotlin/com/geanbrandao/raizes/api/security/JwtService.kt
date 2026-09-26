package com.geanbrandao.raizes.api.security

import com.geanbrandao.raizes.api.domain.Perfil
import com.geanbrandao.raizes.api.entity.UsuarioEntity
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Gera e valida o access token.
 *
 * O token carrega o id, o perfil e a unidade do usuario. Isso deixa a API sem
 * estado: nenhuma requisição precisa consultar o banco so para descobrir quem esta
 * chamando, o que ajuda no requisito de escalar em horario de pico.
 *
 * O preco disso e que o token so reflete o que era verdade quando foi emitido. Se
 * o perfil da pessoa mudar, o token antigo continua com o perfil velho ate expirar.
 * Como o access token dura 15 minutos, a janela e curta e aceitavel.
 */
@Service
class JwtService(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.access-token-expiration}") private val expiracaoMs: Long,
) {
    private val logger = LoggerFactory.getLogger(JwtService::class.java)

    private val chave: SecretKey by lazy {
        require(secret.toByteArray().size >= 32) {
            "jwt.secret precisa ter no minimo 32 bytes para HMAC-SHA256"
        }
        Keys.hmacShaKeyFor(secret.toByteArray())
    }

    /**
     * Emite um access token para o usuario.
     *
     * @param usuario Usuario que acabou de autenticar.
     * @return Token assinado, pronto para ir no header Authorization.
     */
    fun gerarToken(usuario: UsuarioEntity): String {
        val agora = Date()
        val expiraEm = Date(agora.time + expiracaoMs)
        return Jwts.builder()
            .subject(usuario.id.toString())
            .claim(CLAIM_PERFIL, usuario.perfil.name)
            .claim(CLAIM_UNIDADE, usuario.unidadeId?.toString())
            .issuedAt(agora)
            .expiration(expiraEm)
            .signWith(chave)
            .compact()
    }

    /**
     * Confere a assinatura e a validade do token e extrai quem e o dono.
     *
     * @param token Token cru vindo do header.
     * @return Dados do usuario, ou null se o token for invalido ou tiver expirado.
     */
    fun extrairUsuario(token: String): UsuarioAutenticado? = try {
        val claims: Claims = Jwts.parser()
            .verifyWith(chave)
            .build()
            .parseSignedClaims(token)
            .payload

        val perfil = Perfil.valueOf(claims[CLAIM_PERFIL] as String)
        UsuarioAutenticado(
            id = UUID.fromString(claims.subject),
            perfil = perfil,
            unidadeId = (claims[CLAIM_UNIDADE] as String?)?.let(UUID::fromString),
        )
    } catch (ex: ExpiredJwtException) {
        logger.debug("Token expirado")
        null
    } catch (ex: JwtException) {
        logger.debug("Token invalido: {}", ex.message)
        null
    } catch (ex: IllegalArgumentException) {
        // Cobre perfil que não existe mais no enum e uuid mal formado no claim.
        logger.warn("Token com conteudo inesperado: {}", ex.message)
        null
    }

    /** Quanto tempo o access token dura, em segundos. Vai na resposta do login. */
    fun expiracaoEmSegundos(): Long = expiracaoMs / 1000

    companion object {
        const val CLAIM_PERFIL = "perfil"
        const val CLAIM_UNIDADE = "unidadeId"
    }
}
