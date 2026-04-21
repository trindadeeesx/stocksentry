package com.trindadeeesx.stocksentry.infraestructure.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.boot.context.properties.ConfigurationProperties;

import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Objects;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
	basePackages = "com.trindadeeesx.stocksentry.infraestructure.persistence",
	entityManagerFactoryRef = "entityManagerFactory",
	transactionManagerRef = "transactionManager"
)
public class PrimaryDataSourceConfig {
	
	@Primary
	@Bean
	@ConfigurationProperties("spring.datasource")
	public DataSourceProperties primaryDataSourceProperties() {
		return new DataSourceProperties();
	}
	
	@Primary
	@Bean("primaryDataSource")
	public HikariDataSource primaryDataSource(
		@Qualifier("primaryDataSourceProperties") DataSourceProperties props) {
		return props.initializeDataSourceBuilder()
			.type(HikariDataSource.class)
			.build();
	}
	
	@Primary
	@Bean("entityManagerFactory")
	public LocalContainerEntityManagerFactoryBean entityManagerFactory(
		EntityManagerFactoryBuilder builder,
		@Qualifier("primaryDataSource") HikariDataSource dataSource) {
		return builder
			.dataSource(dataSource)
			.packages("com.trindadeeesx.stocksentry.domain")
			.persistenceUnit("primary")
			.build();
	}
	
	@Primary
	@Bean("transactionManager")
	public PlatformTransactionManager transactionManager(
		@Qualifier("entityManagerFactory") LocalContainerEntityManagerFactoryBean emf) {
		return new JpaTransactionManager(Objects.requireNonNull(emf.getObject()));
	}
}