# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

# StockSentry

## O que é esse projeto

StockSentry é um sistema de **monitoramento de estoque em tempo real** desenvolvido para a **Meiliy Cosméticos**.
Ele NÃO é um ERP nem substitui o PDV da empresa — ele se conecta ao banco do PDV em modo **read-only**, sincroniza os
produtos a cada 5 minutos e dispara alertas automáticos (email + push) quando algum produto fica abaixo do estoque
mínimo.

O objetivo é ser apresentado ao dono da empresa como uma solução pronta, rodando com dados reais/mock, mostrando o
problema de descontrole de estoque e como o sistema resolve isso automaticamente.

---

## Contexto de negócio

- **Empresa:** Meiliy Cosméticos — loja física + ecommerce + dois pontos de estoque
- **Problema real:** estoque físico diverge do sistema; produtos quebrados sem baixa; ecommerce vendendo itens sem
  estoque; perda de vendas
- **Exemplo real:** sistema diz 60 unidades, fisicamente tem 3
- **Computadores na loja:** 3 ecommerce, 6 ADM, 5 caixa — todos recebem push
- **PDV:** software Windows com banco PostgreSQL/MySQL em nuvem (ainda sem acesso real — usando mock)
- **Modelo de cobrança:** ~R$5-8k implantação + ~R$800-1.200/mês manutenção

---

## Stack

- **Java 25 + Spring Boot 4**
- **PostgreSQL** — banco próprio do StockSentry
- **PostgreSQL** — banco mock do PDV (`meiliy_pdv`) com 50 produtos reais de cosméticos
- **Redis** — cache de produtos e produtos críticos
- **Flyway** — migrations (atualmente em V4)
- **JWT** — autenticação stateless
- **Resend** — envio de email
- **Web Push VAPID + BouncyCastle** — notificações push nos browsers
- **HikariCP** — pool de conexões (dois datasources)

---

## Arquitetura

```
PDV (meiliy_pdv) ──read-only──► StockSyncScheduler (a cada 5 min)
                                        │
                              sincroniza produtos
                                        │
                              verifica críticos
                                        │
                              AlertService.processBatchAlert()
                                        │
                        ┌───────────────┴───────────────┐
                    1 EMAIL (Resend)            1 PUSH (Web Push)
                  com todos críticos          com resumo críticos
```

### Regras importantes

- **1 email + 1 push por rodada de sync** — nunca um por produto
- **Cooldown de 30 min por produto** — evita spam repetido
- **Estoque mínimo padrão:** 10 unidades para todos os produtos
- **Reset de cooldown:** quando produto volta ao normal, `last_alert` é zerado

---

## Estrutura de pacotes

```
com.trindadeeesx.stocksentry
├── application
│   ├── alert/         AlertService.java
│   ├── auth/          AuthService.java
│   ├── product/       ProductService.java
│   ├── push/          PushNotificationService.java
│   └── sync/          StockSyncScheduler.java  ← coração do sistema
├── domain
│   ├── alert/         Alert, AlertConfig, AlertType, AlertStatus
│   ├── product/       Product, UnitType
│   └── push/          PushSubscription, VapidPublicKeyResponse
├── infraestructure    ← (typo intencional, não renomear agora)
│   ├── cache/         RedisConfig
│   ├── config/        PrimaryDataSourceConfig, PdvDataSourceConfig
│   ├── pdv/           PdvProduct, PdvProductRepository  ← lê o banco do PDV
│   ├── persistence/   todos os repositories JPA
│   └── security/      JwtService, JwtAuthFilter, SecurityUtils, CustomUserDetailsService
└── web
    ├── controller/    AuthController, ProductController, AlertController,
    │                  PushController, SyncController
    ├── dto/           todos os DTOs
    └── handler/       GlobalExceptionHandler
```

---

## Dois datasources

| Bean                                             | Banco         | Uso                                   |
|--------------------------------------------------|---------------|---------------------------------------|
| `primaryDataSource` (HikariDataSource, @Primary) | `stocksentry` | JPA, Flyway, toda a lógica do sistema |
| `pdvDataSource` (HikariDataSource)               | `meiliy_pdv`  | Só leitura via `pdvJdbcTemplate`      |

O Flyway aponta **explicitamente** pro `stocksentry` no `application.yml` — sem isso ele tenta rodar no PDV e quebra.

### Profiles

| Profile          | Banco            | Flyway       | Uso                                                                                   |
|------------------|------------------|--------------|---------------------------------------------------------------------------------------|
| `local` (padrão) | PostgreSQL local | habilitado   | Desenvolvimento                                                                       |
| `test`           | H2 in-memory     | desabilitado | `@ActiveProfiles("test")` — `ResendConfig` e `pdvDataSource` devem ser `@MockitoBean` |

---

## Variáveis de ambiente / application.yml

```yaml
spring.datasource.*          # banco stocksentry (primary)
pdv.datasource.*             # banco meiliy_pdv (read-only)
spring.flyway.*              # apontar explicitamente pro stocksentry
spring.data.redis.*          # Redis local
security.jwt.secret          # mínimo 32 chars
security.jwt.expiration      # 86400000 (1 dia)
resend.api-key               # chave do Resend
resend.from                  # email remetente verificado no Resend
vapid.public-key             # chave pública VAPID
vapid.private-key            # chave privada VAPID
vapid.subject                # mailto:voce@email.com
scheduler.sync-interval-ms   # 300000 = 5 minutos
```

---

## Migrations Flyway

| Versão | O que faz                                                          |
|--------|--------------------------------------------------------------------|
| V1     | Schema inicial com multi-tenant (legado)                           |
| V2     | Adiciona push_subscriptions                                        |
| V3     | Adiciona last_alert em products                                    |
| V4     | Remove multi-tenant e stock_movements — sistema vira single-tenant |

---

## Decisões importantes já tomadas

- **Single-tenant:** 1 empresa, 1 admin — toda a lógica de `Tenant` foi removida no V4
- **Sem OPERATOR role:** só `ADMIN` existe no `UserRole`
- **Sem movimentação manual:** o sistema só lê do PDV, nunca escreve
- **Sem `StockAlertListener` e `StockBelowMinEvent`:** foram removidos; o batch é gerenciado direto no scheduler
- **BouncyCastle registrado no `@PostConstruct`** do `PushNotificationService` — necessário para o VAPID funcionar
- **`@EnableScheduling`** está no `StocksentryApplication` — sem isso o scheduler não roda
- **Import `AccessDeniedException`** deve ser `org.springframework.security.access.AccessDeniedException` — não o
  `java.nio.file`

---

## Banco mock do PDV (meiliy_pdv)

50 produtos reais de cosméticos em 10 categorias:
shampoos, condicionadores, máscaras, óleos, finalizadores, colorações, progressivas, perfumaria, skincare, maquiagem.

- 14 produtos críticos (estoque < 10) — disparam alertas
- 3 produtos zerados
- 30 dias de histórico de vendas com itens

Scripts: `meiliy_pdv_1_ddl.sql` e `meiliy_pdv_2_dml.sql`

A coluna `codigo` do PDV mapeia para `sku` no StockSentry.

---

## O que ainda falta implementar

- [ ] Frontend simples (login + dashboard com produtos críticos + histórico de alertas)
- [ ] Notificações push funcionando nos browsers da loja (service worker no frontend)
- [ ] Relatório semanal/mensal de alertas por email
- [ ] Configuração do intervalo de sync via frontend
- [ ] Ajuste do estoque mínimo por produto via frontend
- [ ] Quando houver acesso ao PDV real: trocar connection string do `pdv.datasource`

---

## Comandos úteis

```bash
# Build
./mvnw clean package -DskipTests

# Subir a aplicação
./mvnw spring-boot:run

# Rodar todos os testes
./mvnw test

# Rodar um teste específico
./mvnw test -Dtest=StockFlowTest

# Forçar sync manual (sem esperar 5 min)
POST http://localhost:8080/api/v1/sync/now
Authorization: Bearer {token}

# Login
POST http://localhost:8080/api/v1/auth/login
{"email": "...", "password": "..."}

# Limpar alertas e cooldowns para testar
DELETE FROM alerts;
UPDATE products SET last_alert = NULL;

# Verificar produtos críticos no banco
SELECT name, sku, current_stock, min_stock
FROM products
WHERE current_stock <= min_stock AND active = true
ORDER BY current_stock;

# Ver histórico de alertas
SELECT a.triggered_at, p.name, a.type, a.status
FROM alerts a JOIN products p ON a.product_id = p.id
ORDER BY a.triggered_at DESC;
```

---

## Problemas conhecidos / já resolvidos

| Problema                                          | Solução                                                          |
|---------------------------------------------------|------------------------------------------------------------------|
| `NoSuchProviderException: BC`                     | Registrar `BouncyCastleProvider` no `@PostConstruct`             |
| Flyway rodando no banco errado (dois datasources) | Configurar `spring.flyway.url` explicitamente                    |
| `javax.sql.DataSource` não encontrado             | Usar `HikariDataSource` diretamente (Spring Boot 4 / Jakarta EE) |
| Checksum mismatch no Flyway                       | `DELETE FROM flyway_schema_history WHERE version IN ('1','3')`   |
| Muitos emails simultâneos (limite Resend 5/s)     | Batch: 1 email por rodada com todos os críticos                  |
| `@Scheduled` não rodando                          | Faltava `@EnableScheduling` no `StocksentryApplication`          |
| `AccessDeniedException` 403 não capturado         | Import errado — usar `org.springframework.security.access`       |