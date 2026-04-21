package com.trindadeeesx.stocksentry.infraestructure.pdv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PdvProduct {
	private String codigo;
	private String nome;
	private BigDecimal estoque;
	private String unidade;
}