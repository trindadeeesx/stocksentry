package com.trindadeeesx.stocksentry.application.common;

/** Limite de uso estourado. Vira 429. */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
