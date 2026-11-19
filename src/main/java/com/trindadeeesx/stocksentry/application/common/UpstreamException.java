package com.trindadeeesx.stocksentry.application.common;

/** Falha num serviço externo (ex.: provedor de e-mail). Vira 502. */
public class UpstreamException extends RuntimeException {
    public UpstreamException(String message) {
        super(message);
    }
}
