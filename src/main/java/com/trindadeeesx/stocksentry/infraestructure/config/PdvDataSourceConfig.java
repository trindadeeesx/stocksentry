package com.trindadeeesx.stocksentry.infraestructure.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class PdvDataSourceConfig {
	
	@Bean("pdvDataSourceProperties")
	@ConfigurationProperties("pdv.datasource")
	public DataSourceProperties pdvDataSourceProperties() {
		return new DataSourceProperties();
	}
	
	@Bean("pdvDataSource")
	public HikariDataSource pdvDataSource(
		@Qualifier("pdvDataSourceProperties") DataSourceProperties props) {
		return props.initializeDataSourceBuilder()
			.type(HikariDataSource.class)
			.build();
	}
	
	@Bean("pdvJdbcTemplate")
	public JdbcTemplate pdvJdbcTemplate(
		@Qualifier("pdvDataSource") HikariDataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}
}