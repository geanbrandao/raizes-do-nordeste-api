package com.geanbrandao.raizes.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Ponto de entrada da API da rede Raizes do Nordeste.
 *
 * O sistema atende varias unidades da franquia e varios canais de venda
 * (app, totem, balcão, pickup e web), tratando o canal como dado de dominio
 * do pedido.
 */
@SpringBootApplication
class RaizesApiApplication

fun main(args: Array<String>) {
    runApplication<RaizesApiApplication>(*args)
}
