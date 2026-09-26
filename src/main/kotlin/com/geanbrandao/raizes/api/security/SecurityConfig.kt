package com.geanbrandao.raizes.api.security

import com.geanbrandao.raizes.api.domain.Perfil
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Configuração de segurança da API.
 *
 * Duas escolhas que valem explicar:
 *
 * 1. **Tudo fechado por padrão.** A ultima regra e anyRequest().authenticated(), o
 *    que significa que endpoint novo ja nasce protegido. Quem quiser deixar uma
 *    rota publica precisa escrever isso aqui, de proposito. O contrario, liberar
 *    por padrão e lembrar de fechar depois, e como vaza rota sem querer.
 *
 * 2. **Sem sessão.** A API e stateless e a identidade vem do JWT a cada requisição.
 *    Isso permite rodar varias instancias atras de um balanceador sem compartilhar
 *    sessão, que e o que sustenta o requisito de aguentar horario de pico.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter,
    private val authenticationEntryPoint: RestAuthenticationEntryPoint,
    private val accessDeniedHandler: RestAccessDeniedHandler,
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // Sem estado e sem formulario: a API não usa cookie de sessão, então
            // CSRF não se aplica.
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .exceptionHandling {
                it.authenticationEntryPoint(authenticationEntryPoint)
                it.accessDeniedHandler(accessDeniedHandler)
            }
            .authorizeHttpRequests { autorizar ->
                autorizar
                    // ---------------------------------------- rotas publicas
                    .requestMatchers("/auth/login", "/auth/refresh").permitAll()
                    .requestMatchers(HttpMethod.POST, "/usuarios").permitAll()
                    .requestMatchers(HttpMethod.GET, "/unidades", "/unidades/*").permitAll()
                    .requestMatchers(HttpMethod.GET, "/unidades/*/cardapio").permitAll()
                    .requestMatchers(
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                    ).permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                    // ------------------------------------ rotas por perfil
                    .requestMatchers(HttpMethod.POST, "/usuarios/operadores")
                    .hasAnyRole(Perfil.ADMIN.name, Perfil.GERENTE.name)
                    .requestMatchers("/auditoria/**").hasRole(Perfil.ADMIN.name)
                    .requestMatchers(HttpMethod.POST, "/unidades").hasRole(Perfil.ADMIN.name)
                    .requestMatchers(HttpMethod.PUT, "/unidades/**").hasRole(Perfil.ADMIN.name)

                    // -------------------------------------------- o resto
                    // Qualquer rota nova que não esteja listada acima cai aqui e
                    // exige autenticação.
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}
