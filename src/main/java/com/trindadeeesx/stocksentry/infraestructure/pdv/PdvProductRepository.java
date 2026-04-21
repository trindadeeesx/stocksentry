package com.trindadeeesx.stocksentry.infraestructure.pdv;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PdvProductRepository {
	
	@Qualifier("pdvJdbcTemplate")
	private final JdbcTemplate pdvJdbcTemplate;
	
	public List<PdvProduct> findAllActive() {
		return pdvJdbcTemplate.query(
			"""
			SELECT codigo, nome, estoque, unidade
			FROM produtos
			WHERE ativo = TRUE
			ORDER BY nome
			""",
			(rs, rowNum) -> new PdvProduct(
				rs.getString("codigo"),
				rs.getString("nome"),
				rs.getBigDecimal("estoque"),
				rs.getString("unidade")
			)
		);
	}
}