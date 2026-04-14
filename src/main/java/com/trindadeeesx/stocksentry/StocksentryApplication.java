package com.trindadeeesx.stocksentry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class StocksentryApplication {

	public static void main(String[] args) {
		SpringApplication.run(StocksentryApplication.class, args);
	}

}
