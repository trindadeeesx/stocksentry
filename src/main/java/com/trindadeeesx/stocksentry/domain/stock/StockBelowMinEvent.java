package com.trindadeeesx.stocksentry.domain.stock;

import com.trindadeeesx.stocksentry.domain.product.Product;
import org.springframework.context.ApplicationEvent;

public class StockBelowMinEvent extends ApplicationEvent {

    private final Product product;

    public StockBelowMinEvent(Object source, Product product) {
        super(source);
        this.product = product;
    }

    public Product getProduct() {
        return product;
    }
}
