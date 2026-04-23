# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

# StockSentry — Backend

## O que é esse projeto

StockSentry é um sistema de **monitoramento de estoque em tempo real** desenvolvido para a **Meiliy Cosméticos**.
Ele NÃO é um ERP nem substitui o PDV da empresa — ele se conecta ao banco do PDV em modo **read-only**, sincroniza os
produtos automaticamente e dispara alertas (email + push) quando algum produto fica abaixo do estoque mínimo.

---

## Contexto de negócio

- **Empresa:** Meiliy Cosméticos — loja física + ecommerce + dois pontos de estoque
- **Problema real:** estoque físico diverge do sistema; produtos quebrados sem baixa; ecommerce vendendo itens sem estoque; perda de vendas
- **Exemplo real:** sistema diz 60 unidades, fisicamente tem 3
- **Computadores na loja:** 3 ecommerce, 6 ADM, 5 caixa — todos recebem push
- **PDV:** software Windows com banco PostgreSQL/MySQL em nuvem (ainda sem acesso real — usando mock)
- **Modelo de cobrança:** ~R$5-8k implantação + ~R$800-1.200/mês manutenção

---

## Stack

- **Java 25 + Spring Boot 4**
- **PostgreSQL** — banco próprio do StockSentry (`stocksentry`)
- **PostgreSQL** — banco mock do PDV (`meiliy_pdv`) com 50 produtos reais de cosméticos
- **Redis** — cache de produtos e produtos críticos
- **Flyway** — migrations (atualmente em V5)
- **JWT** — autenticação stateless (Bearer token)
- **Resend** — envio de email transacional e relatórios
- **Web Push VAPID + BouncyCastle** — notificações push nos browsers (service worker)
- **HikariCP** — pool de conexões (dois datasources)

---

## Arquitetura

```
PDV (meiliy_pdv) ──read-only──► StockSyncScheduler (intervalo configurável via API)
                                        │
                              sincroniza produtos
                                        │
                              verifica críticos
                                        │
                              AlertService.processStockAlert()
                                        │
                        ┌───────────────┴───────────────┐
                    1 EMAIL (Resend)            1 PUSH (Web Push)
                  com todos críticos          com resumo críticos
```

### Regras de negócio

- **1 email + 1 push por rodada de sync** — nunca um por produto
- **Cooldown de 30 min por produto** — evita spam repetido
- **Reset de cooldown:** quando produto volta ao normal, `last_alert` é zerado automaticamente
- **Estoque mínimo padrão:** 10 unidades (ajustável por produto via API)
- **Estoque crítico:** `current_stock <= min_stock`
- **Intervalo de sync:** configurável via API, padrão 300000ms (5 min), mínimo 30s, máximo 24h

---

## Estrutura de pacotes

```
com.trindadeeesx.stocksentry
├── application
│   ├── alert/         AlertService.java
│   ├── auth/          AuthService.java
│   ├── product/       ProductService.java
│   ├── push/          PushNotificationService.java
│   ├── settings/      SettingService.java
│   └── sync/          StockSyncScheduler.java  ← coração do sistema
├── domain
│   ├── alert/         Alert, AlertConfig, AlertType, AlertStatus
│   ├── product/       Product, UnitType
│   ├── push/          PushSubscription, VapidPublicKeyResponse
│   ├── settings/      AppSettings
│   └── user/          User, UserRole
├── infraestructure    ← (typo intencional, não renomear agora)
│   ├── cache/         RedisConfig
│   ├── config/        PrimaryDataSourceConfig, PdvDataSourceConfig, SchedulerConfig, JacksonConfig
│   ├── pdv/           PdvProduct, PdvProductRepository  ← lê o banco do PDV
│   ├── persistence/   todos os repositories JPA
│   └── security/      JwtService, JwtAuthFilter, SecurityUtils, CustomUserDetailsService
└── web
    ├── controller/    AuthController, ProductController, AlertController,
    │                  PushController, SyncController, SettingsController, DashboardController
    ├── dto/           todos os DTOs
    └── handler/       GlobalExceptionHandler
```

---

## Dois datasources

| Bean                                             | Banco         | Uso                                   |
|--------------------------------------------------|---------------|---------------------------------------|
| `primaryDataSource` (HikariDataSource, @Primary) | `stocksentry` | JPA, Flyway, toda a lógica do sistema |
| `pdvDataSource` (HikariDataSource)               | `meiliy_pdv`  | Só leitura via `pdvJdbcTemplate`      |

O Flyway aponta **explicitamente** pro `stocksentry` — sem isso ele tenta rodar no PDV e quebra.

### Profiles

| Profile          | Banco            | Flyway       | Uso                                                                                    |
|------------------|------------------|--------------|----------------------------------------------------------------------------------------|
| `local` (padrão) | PostgreSQL local | habilitado   | Desenvolvimento                                                                        |
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
security.register-key        # chave para criar o primeiro usuário admin
resend.api-key               # chave do Resend
resend.from                  # email remetente verificado no Resend
vapid.public-key             # chave pública VAPID
vapid.private-key            # chave privada VAPID
vapid.subject                # mailto:voce@email.com
```

> O intervalo de sync **não é mais variável de ambiente** — é configurado via `PATCH /api/v1/settings` e salvo no banco.

---

## Migrations Flyway

| Versão | O que faz                                                          |
|--------|--------------------------------------------------------------------|
| V1     | Schema inicial com multi-tenant (legado)                           |
| V2     | Adiciona `push_subscriptions`                                      |
| V3     | Adiciona `last_alert` em `products`                                |
| V4     | Remove multi-tenant e `stock_movements` — sistema vira single-tenant |
| V5     | Cria `app_settings` com `sync_interval_ms` (padrão 300000ms)      |

---

## Contrato de API

Todos os endpoints são prefixados com `/api/v1`. Autenticação via `Authorization: Bearer <token>`.
Respostas de erro seguem o padrão `{ "error": "mensagem" }`.

### Autenticação

| Método | Endpoint              | Auth          | Descrição                                    |
|--------|-----------------------|---------------|----------------------------------------------|
| POST   | `/auth/register`      | register-key  | Cria o primeiro usuário admin                |
| POST   | `/auth/login`         | —             | Retorna JWT                                  |

**POST /auth/register**
```
Header: X-Register-Key: <register-key>
Body:   { "name": "string", "email": "string", "password": "string" }
201:    { "token": "string", "email": "string", "role": "ADMIN" }
```

**POST /auth/login**
```
Body: { "email": "string", "password": "string" }
200:  { "token": "string", "email": "string", "role": "ADMIN" }
401:  { "error": "Invalid email or password" }
```

---

### Produtos

| Método | Endpoint                    | Auth  | Descrição                                |
|--------|-----------------------------|-------|------------------------------------------|
| GET    | `/products`                 | token | Lista todos os produtos (paginado)       |
| GET    | `/products/{id}`            | token | Detalhe de um produto                    |
| PATCH  | `/products/{id}/min-stock`  | ADMIN | Atualiza estoque mínimo do produto       |
| GET    | `/products/critical`        | token | Produtos com `current_stock <= min_stock`|
| GET    | `/products/out-of-stock`    | token | Produtos com `current_stock = 0`         |
| GET    | `/products/stats`           | token | Totais: ativos, críticos, zerados        |

**ProductResponse**
```json
{
  "id": "uuid",
  "name": "string",
  "sku": "string",
  "unit": "UN | KG | L | CX",
  "currentStock": 0.000,
  "minStock": 10.000,
  "active": true,
  "critical": false,
  "createdAt": "2026-04-22T00:00:00"
}
```

**ProductStatsResponse**
```json
{ "totalActive": 50, "totalCritical": 14, "totalOutOfStock": 3 }
```

**PATCH /products/{id}/min-stock**
```json
{ "minStock": 15 }
```

Paginação padrão Spring: `?page=0&size=20&sort=name,asc`

---

### Alertas

| Método | Endpoint               | Auth  | Descrição                                          |
|--------|------------------------|-------|----------------------------------------------------|
| POST   | `/alerts/config`       | ADMIN | Cria configuração de alerta (EMAIL ou PUSH)        |
| GET    | `/alerts/config`       | ADMIN | Lista configurações ativas                         |
| DELETE | `/alerts/config/{id}`  | ADMIN | Desativa configuração (soft delete)                |
| GET    | `/alerts/history`      | token | Histórico paginado de alertas disparados           |
| GET    | `/alerts/recent`       | token | Últimos N alertas (`?limit=5`, máx 50)             |
| POST   | `/alerts/report`       | ADMIN | Dispara relatório manual (`?days=7`, máx 365)      |

**AlertConfigRequest**
```json
{ "type": "EMAIL | PUSH", "destination": "email@exemplo.com" }
```
> Para `PUSH`, `destination` pode ser vazio.

**AlertConfigResponse**
```json
{ "id": "uuid", "type": "EMAIL", "destination": "email@exemplo.com", "active": true }
```

**AlertResponse**
```json
{
  "id": "uuid",
  "productName": "string",
  "type": "EMAIL | PUSH",
  "destination": "string",
  "status": "SENT | FAILED",
  "triggeredAt": "2026-04-22T00:00:00"
}
```

**Relatórios automáticos por email:**
- Semanal: toda segunda às 08:00
- Mensal: todo dia 1 às 08:00
- Conteúdo: total de alertas, taxa de sucesso, top 10 produtos mais alertados, lista de críticos atuais

---

### Notificações Push

| Método | Endpoint            | Auth  | Descrição                       |
|--------|---------------------|-------|---------------------------------|
| GET    | `/push/vapid-key`   | token | Retorna a chave pública VAPID   |
| POST   | `/push/subscribe`   | token | Registra subscription do browser|
| DELETE | `/push/unsubscribe` | token | Remove subscription             |

**GET /push/vapid-key**
```json
{ "publicKey": "string" }
```

**POST /push/subscribe**
```json
{ "endpoint": "string", "p256dh": "string", "auth": "string", "deviceName": "string" }
```

**DELETE /push/unsubscribe**
```json
{ "endpoint": "string" }
```

Payload enviado ao service worker:
```json
{ "title": "⚠️ 3 produto(s) com estoque crítico!", "body": "Produto A: 2 | Produto B: 0 | ..." }
```

---

### Sync

| Método | Endpoint       | Auth  | Descrição                                    |
|--------|----------------|-------|----------------------------------------------|
| POST   | `/sync/now`    | ADMIN | Força sync imediato (sem esperar o intervalo)|
| GET    | `/sync/status` | token | Status da última execução                    |

**SyncStatusResponse**
```json
{
  "lastSyncAt": "2026-04-22T21:00:00",
  "lastCreated": 0,
  "lastUpdated": 50,
  "lastCritical": 14,
  "lastRecovered": 3
}
```

---

### Settings

| Método | Endpoint      | Auth  | Descrição                              |
|--------|---------------|-------|----------------------------------------|
| GET    | `/settings`   | ADMIN | Retorna configurações atuais do sistema|
| PATCH  | `/settings`   | ADMIN | Atualiza configurações                 |

**SettingsResponse**
```json
{ "syncIntervalMs": 300000 }
```

**PATCH /settings**
```json
{ "syncIntervalMs": 60000 }
```
> Mínimo: 30000 (30s) — Máximo: 86400000 (24h)

A alteração tem efeito na **próxima** rodada de sync (não interrompe a que está em andamento).

---

### Dashboard

| Método | Endpoint     | Auth  | Descrição                                   |
|--------|--------------|-------|---------------------------------------------|
| GET    | `/dashboard` | token | Resumo geral para tela inicial do frontend  |

**DashboardSummaryResponse** *(estrutura — confirmar com DashboardController)*
```json
{
  "totalProducts": 50,
  "criticalProducts": 14,
  "outOfStockProducts": 3,
  "lastSyncAt": "2026-04-22T21:00:00",
  "recentAlerts": [ /* últimos 5 AlertResponse */ ]
}
```

---

## Banco mock do PDV (meiliy_pdv)

50 produtos reais de cosméticos em 10 categorias:
shampoos, condicionadores, máscaras, óleos, finalizadores, colorações, progressivas, perfumaria, skincare, maquiagem.

- 14 produtos críticos (estoque ≤ 10) — disparam alertas
- 3 produtos zerados
- 30 dias de histórico de vendas com itens

Scripts: `meiliy_pdv_1_ddl.sql` e `meiliy_pdv_2_dml.sql`

A coluna `codigo` do PDV mapeia para `sku` no StockSentry.

---

## Decisões importantes já tomadas

- **Single-tenant:** 1 empresa, 1 admin — lógica de `Tenant` removida no V4
- **Sem OPERATOR role:** só `ADMIN` existe em `UserRole`
- **Sem movimentação manual:** o sistema só lê do PDV, nunca escreve
- **Batch alert:** 1 email + 1 push por rodada — nunca um por produto
- **Intervalo dinâmico:** sync não usa mais `@Scheduled` fixo; usa `TaskScheduler` com auto-reagendamento lendo o intervalo do banco a cada ciclo
- **Self-proxy no scheduler:** `StockSyncScheduler` injeta `@Lazy self` para que `@Transactional` e `@CacheEvict` passem pelo proxy Spring ao chamar `sync()` internamente
- **BouncyCastle registrado no `@PostConstruct`** do `PushNotificationService` — necessário para VAPID funcionar
- **`@EnableScheduling`** está no `StocksentryApplication`
- **Import `AccessDeniedException`** deve ser `org.springframework.security.access.AccessDeniedException`
- **Relatórios semanais e mensais** já implementados via `@Scheduled(cron = ...)` no `AlertService`

---

## O que ainda falta implementar

- [ ] Frontend (login + dashboard + produtos críticos + histórico de alertas)
- [ ] Service worker no frontend para receber push nos browsers da loja
- [ ] Configuração do intervalo de sync via frontend (API já pronta)
- [ ] Ajuste de estoque mínimo por produto via frontend (API já pronta)
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

# Forçar sync manual
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

# Ver configuração atual de sync
SELECT * FROM app_settings;
```

---

## Problemas conhecidos / já resolvidos

| Problema                                          | Solução                                                                        |
|---------------------------------------------------|--------------------------------------------------------------------------------|
| `NoSuchProviderException: BC`                     | Registrar `BouncyCastleProvider` no `@PostConstruct`                           |
| Flyway rodando no banco errado (dois datasources) | Configurar `spring.flyway.url` explicitamente                                  |
| `javax.sql.DataSource` não encontrado             | Usar `HikariDataSource` diretamente (Spring Boot 4 / Jakarta EE)               |
| Checksum mismatch no Flyway                       | `DELETE FROM flyway_schema_history WHERE version IN ('1','3')`                 |
| Muitos emails simultâneos (limite Resend 5/s)     | Batch: 1 email por rodada com todos os críticos                                |
| `@Scheduled` não rodando                          | Faltava `@EnableScheduling` no `StocksentryApplication`                        |
| `AccessDeniedException` 403 não capturado         | Import errado — usar `org.springframework.security.access`                     |
| `SMALLINT` vs `Integer` no Hibernate              | Entidade `AppSettings` usa `Short` para mapear corretamente `SMALLINT` do banco|
| `@Async` + `@Transactional` causando LazyInit     | Removido `@Async` de `processStockAlert` e `sendToAllDevices`                  |
| `System.out.println` de debug em produção         | Removido junto com `ApplicationContext` desnecessário no `PushNotificationService` |
