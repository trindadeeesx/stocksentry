package com.trindadeeesx.stocksentry.domain.stock;

import java.util.UUID;

/**
 * Publicado quando uma movimentação deixa o produto abaixo do estoque mínimo.
 *
 * Carrega só o ID: o alerta roda em outra thread, depois do commit, e precisa
 * recarregar o produto do banco. Passar a entidade JPA entre threads leva a
 * LazyInitializationException e a gravar estado velho por cima do atual.
 */
public record StockBelowMinEvent(UUID productId) {
}
