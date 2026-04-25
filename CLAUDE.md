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
- **Computadores na loja:** 3 ecommerce, 6 ADM, 5 caixa — todos recebem push
- **PDV:** software Windows com banco PostgreSQL/MySQL em nuvem (ainda sem acesso real — usando mock)
- **Modelo de cobrança:** ~R$5-8k implantação + ~R$800-1.200/mês manutenção

---

## Stack

- **Java 25 + Spring Boot 4.0.5**
- **PostgreSQL** — banco próprio do StockSentry (`stocksentry`)
- **PostgreSQL** — banco mock do PDV (`meiliy_pdv`) com 50 produtos reais de cosméticos
- **Redis** — cache de produtos e produtos críticos
- **Flyway** — migrations (atualmente em V5)
- **JWT (jjwt 0.12.6)** — autenticação stateless (Bearer token)
- **Resend (resend-java 3.1.0)** — envio de email transacional e relatórios
- **Web Push (nl.martijndwars/web-push) + BouncyCastle** — notificações push nos browsers (service worker)
- **HikariCP** — pool de conexões (dois datasources)
- **Lombok** — redução de boilerplate

---

## Arquitetura

```
PDV (meiliy_pdv) ──read-only──► StockSyncScheduler (intervalo configurável via API)
                                        │
                              sincroniza produtos
                                        │
                              verifica críticos / recuperados
                                        │
                              AlertService.processStockAlert()
                                        │
                        ┌───────────────┴───────────────┐
                    1 EMAIL (Resend)            1 PUSH (Web Push)
                  com todos críticos          com resumo críticos
                                        │
                              SseEmitterService.broadcast("sync" | "alert" | "config")
                              ← frontend escuta via GET /api/v1/events?token={jwt}
```

### Regras de negócio

- **1 email + 1 push por rodada de sync** — nunca um por produto
- **Cooldown de 30 min por produto** — evita spam repetido
- **Reset de cooldown:** quando produto volta ao normal, `last_alert` é zerado via `AlertService.resetAlert()`
- **Estoque mínimo padrão:** 10 unidades (ajustável por produto via API)
- **Estoque crítico:** `currentStock > 0 AND currentStock <= minStock` (produto com algum estoque, mas abaixo do mínimo)
- **Estoque zerado:** `currentStock <= 0` — tratado separadamente de "crítico"
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
│   ├── sse/           SseEmitterService.java
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
│   ├── mail/          ResendConfig
│   ├── pdv/           PdvProduct, PdvProductRepository  ← lê o banco do PDV via pdvJdbcTemplate
│   ├── persistence/   todos os repositories JPA
│   └── security/      JwtService, JwtAuthFilter, SseTokenAuthFilter, SecurityUtils,
│                      CustomUserDetailsService, SecurityConfig
└── web
    ├── controller/    AuthController, ProductController, AlertController,
    │                  PushController, SyncController, SettingsController,
    │                  DashboardController, SseController, DebugController
    ├── dto/           todos os DTOs (organizados por domínio: alert/, product/, push/, settings/, sync/)
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

| Versão | O que faz                                                            |
|--------|----------------------------------------------------------------------|
| V1     | Schema inicial com multi-tenant (legado)                             |
| V2     | Adiciona `push_subscriptions`                                        |
| V3     | Adiciona `last_alert` em `products`                                  |
| V4     | Remove multi-tenant e `stock_movements` — sistema vira single-tenant |
| V5     | Cria `app_settings` com `sync_interval_ms BIGINT` (padrão 300000)   |

---

## Decisões importantes já tomadas

- **Single-tenant:** 1 empresa, 1 admin — lógica de `Tenant` removida no V4
- **Sem OPERATOR role:** só `ADMIN` existe em `UserRole`
- **Sem movimentação manual:** o sistema só lê do PDV, nunca escreve
- **Batch alert:** 1 email + 1 push por rodada — nunca um por produto
- **Crítico vs zerado são queries separadas:** `findCritical()` usa `currentStock > 0 AND currentStock <= minStock`; `findOutOfStock()` usa `currentStock <= 0`. Produto zerado NÃO entra no fluxo de alertas de crítico.
- **Intervalo dinâmico:** sync não usa `@Scheduled` fixo; usa `TaskScheduler` com auto-reagendamento. Cada ciclo agenda o próximo lendo o intervalo do banco.
- **Self-proxy no scheduler:** `StockSyncScheduler` injeta `@Lazy self` para que `@Transactional` e `@CacheEvict` passem pelo proxy Spring ao chamar `sync()` internamente.
- **BouncyCastle registrado no `@PostConstruct`** do `PushNotificationService` — necessário para VAPID funcionar (usa `Security.addProvider` apenas se o provider ainda não estiver registrado).
- **`@EnableScheduling`** está no `StocksentryApplication`.
- **Import `AccessDeniedException`** deve ser `org.springframework.security.access.AccessDeniedException`.
- **Relatórios semanais e mensais** via `@Scheduled(cron = ...)` no `AlertService` — semanal às 08h nas segundas, mensal às 08h no dia 1.
- **SSE via query param:** `EventSource` do browser não suporta headers customizados; o JWT para `/api/v1/events` vem como `?token=` e é validado pelo `SseTokenAuthFilter`.
- **`SseEmitterService` usa `ConcurrentHashMap.newKeySet()`** para thread safety; emitters têm `Long.MAX_VALUE` de timeout.
- **CORS sem credentials:** `allowedOriginPatterns("*")` com `allowCredentials(false)` — API pura Bearer token, sem cookies.
- **Security headers ativos:** HSTS, CSP `default-src 'self'`, Referrer-Policy strict-origin-when-cross-origin, X-Frame-Options DENY.
- **`AppSettings.id`** é `Short` (mapeia `SMALLINT PK`); **`syncIntervalMs`** é `long` (mapeia `BIGINT`) — tabela tem constraint `CHECK (id = 1)` para garantir single-row.
- **`DELETE /push/subscribe`** recebe o endpoint via `@RequestBody` (campo `endpoint` em `PushUnsubscribeRequest`), não via query param.
- **`destination` vazio para PUSH:** `AlertService.createConfig` valida email somente para tipo `EMAIL`; para `PUSH` aceita qualquer string (nome do dispositivo) ou vazio.

---

## Problemas conhecidos / já resolvidos

| Problema                                          | Solução                                                                          |
|---------------------------------------------------|----------------------------------------------------------------------------------|
| `NoSuchProviderException: BC`                     | `Security.addProvider(new BouncyCastleProvider())` no `@PostConstruct`           |
| Flyway rodando no banco errado (dois datasources) | Configurar `spring.flyway.url` explicitamente pro stocksentry                    |
| `javax.sql.DataSource` não encontrado             | Usar `HikariDataSource` diretamente (Spring Boot 4 / Jakarta EE)                 |
| Checksum mismatch no Flyway                       | `DELETE FROM flyway_schema_history WHERE version IN ('1','3')`                   |
| Muitos emails simultâneos (limite Resend 5/s)     | Batch: 1 email por rodada com todos os críticos                                  |
| `@Scheduled` não rodando                          | Faltava `@EnableScheduling` no `StocksentryApplication`                          |
| `AccessDeniedException` 403 não capturado         | Import errado — usar `org.springframework.security.access`                       |
| `SMALLINT` vs `Short` no Hibernate                | `AppSettings.id` é `Short`; `syncIntervalMs` é `long` (mapeia `BIGINT`)          |
| `@Async` + `@Transactional` causando LazyInit     | Removido `@Async` de `processStockAlert` e `sendToAllDevices`                    |
| `destination` vazio para PUSH                     | `AlertService.createConfig` valida destino apenas para tipo `EMAIL`              |
| Produtos zerados entrando no alerta de crítico    | `findCritical()` usa `currentStock > 0 AND currentStock <= minStock`             |
| Push `unsubscribe` sem `@Transactional`           | `deleteByEndpoint` derivada precisa de transação — anotado no `unsubscribe()`    |

---

# Contrato de API — Referência Completa para o Frontend

**Base URL:** `http://localhost:8080/api/v1`
**Autenticação:** `Authorization: Bearer <token>` em todos os endpoints marcados com `token` ou `ADMIN`.
**Erros:** todas as respostas de erro seguem `{ "error": "mensagem" }`.
**Datas:** formato ISO-8601 — `"2026-04-23T21:00:00"`.
**Paginação:** padrão Spring — query params `?page=0&size=20&sort=name,asc`; resposta é o objeto `Page<T>` do Spring.

---

## Autenticação — `/auth`

### `POST /auth/login`
Pública.

**Request body:**
```json
{
  "email": "string",      // obrigatório, formato email, max 150
  "password": "string"    // obrigatório, max 128
}
```

**Response `200`:**
```json
{ "token": "string", "email": "string", "role": "ADMIN" }
```

**Response `401`:** `{ "error": "Invalid email or password" }`

---

### `POST /auth/register`
Cria o primeiro usuário admin. Requer o header `X-Register-Key`.

**Headers:** `X-Register-Key: <chave>`

**Request body:**
```json
{
  "name": "string",       // obrigatório, max 100
  "email": "string",      // obrigatório, formato email, max 150
  "password": "string"    // obrigatório, min 8, max 128
}
```

**Response `201`:**
```json
{ "token": "string", "email": "string", "role": "ADMIN" }
```

---

### `GET /auth/me`
Requer token.

**Response `200`:**
```json
{ "id": "uuid", "name": "string", "email": "string", "role": "ADMIN" }
```

---

## Produtos — `/products`

Todos requerem token. `PATCH` requer ADMIN.

### `GET /products`
Lista produtos ativos, paginado.

**Response `200` — `Page<ProductResponse>`:**
```json
{
  "content": [{
    "id": "uuid", "name": "string", "sku": "string", "unit": "UN",
    "currentStock": 5.000, "minStock": 10.000, "active": true,
    "critical": true, "createdAt": "2026-04-22T00:00:00"
  }],
  "totalElements": 50, "totalPages": 3, "number": 0, "size": 20
}
```

> `unit`: `UN`, `KG`, `L`, `CX`
> `critical`: `true` quando `currentStock > 0 AND currentStock <= minStock`

---

### `GET /products/{id}`
**Response `200`:** `ProductResponse` | **`404`:** `{ "error": "Product not found" }`

---

### `PATCH /products/{id}/min-stock`
Requer ADMIN.

**Request body:** `{ "minStock": 15.0 }` (obrigatório, >= 0)

**Response `200`:** `ProductResponse` atualizado | **`404`:** `{ "error": "Product not found" }`

---

### `GET /products/critical`
Produtos com `currentStock > 0 AND currentStock <= minStock`, sem paginação.

**Response `200`:** `ProductResponse[]`

---

### `GET /products/out-of-stock`
Produtos com `currentStock <= 0`, sem paginação.

**Response `200`:** `ProductResponse[]`

---

### `GET /products/stats`

**Response `200`:**
```json
{ "totalActive": 50, "totalCritical": 14, "totalOutOfStock": 3 }
```

---

## Alertas — `/alerts`

### `POST /alerts/config`
Requer ADMIN.

**Request body:**
```json
{
  "type": "EMAIL",                    // "EMAIL" ou "PUSH"
  "destination": "email@exemplo.com"  // obrigatório e válido para EMAIL; livre para PUSH
}
```

**Response `201`:** `AlertConfigResponse` | **`400`:** se EMAIL sem endereço válido.

---

### `GET /alerts/config`
Requer ADMIN. **Response `200`:** `AlertConfigResponse[]`

---

### `DELETE /alerts/config/{id}`
Requer ADMIN. Soft delete + remove push subscriptions associadas (se tipo PUSH).

**Response `204`** | **`400`:** `{ "error": "Config not found" }`

---

### `GET /alerts/history`
Histórico paginado. Requer token.

**Response `200` — `Page<AlertResponse>`** com campos: `id`, `productName`, `type`, `destination`, `status` (`SENT`|`FAILED`), `triggeredAt`.

---

### `GET /alerts/recent`
Requer token. **Query params:** `limit` (padrão `5`, máx `50`). **Response `200`:** `AlertResponse[]`

---

### `POST /alerts/report`
Requer ADMIN. **Query params:** `days` (padrão `7`, máx `365`).

**Response `200`:** `{ "message": "Report triggered for last 7 days", "timestamp": "..." }`

---

## Notificações Push — `/push`

### `GET /push/vapid-key`
Pública. **Response `200`:** `{ "publicKey": "string" }`

---

### `POST /push/subscribe`
Requer token.

**Request body:**
```json
{
  "endpoint": "string",   // obrigatório, max 500
  "p256dh": "string",     // obrigatório, max 256
  "auth": "string",       // obrigatório, max 128
  "deviceName": "string"  // opcional, max 100
}
```

**Response `201`:** sem body. Se endpoint já existe, atualiza os campos.

---

### `DELETE /push/subscribe`
Requer token. Remove subscription.

**Request body:** `{ "endpoint": "string" }` (obrigatório, max 500)

**Response `204`:** sem body

---

## Sync — `/sync`

### `POST /sync/now`
Requer ADMIN. Força sync imediato.

**Response `200`:** `{ "message": "Sync triggered successfully", "timestamp": "..." }`

---

### `GET /sync/status`
Requer token.

**Response `200`:**
```json
{
  "lastSyncAt": "2026-04-23T21:00:00",  // null se nunca rodou
  "lastCreated": 0, "lastUpdated": 50,
  "lastCritical": 14, "lastRecovered": 3
}
```

---

## Settings — `/settings`

Ambos requerem ADMIN.

### `GET /settings`
**Response `200`:** `{ "syncIntervalMs": 300000 }`

### `PATCH /settings`
**Request body:** `{ "syncIntervalMs": 60000 }` (min 30000, max 86400000)

**Response `200`:** `{ "syncIntervalMs": 60000 }`

---

## Dashboard — `/dashboard`

### `GET /dashboard/summary`
Requer token.

**Response `200`:**
```json
{
  "syncStatus": { "lastSyncAt": "...", "lastCreated": 0, "lastUpdated": 50, "lastCritical": 14, "lastRecovered": 3 },
  "criticalCount": 14, "outOfStockCount": 3,
  "critical": [ /* ProductResponse[] */ ],
  "outOfStock": [ /* ProductResponse[] */ ]
}
```

---

## SSE — `/events`

### `GET /events?token={jwt}`
Stream SSE. JWT via query param (EventSource não suporta headers customizados).

| Evento      | Quando                                                  |
|-------------|---------------------------------------------------------|
| `sync`      | Final de cada ciclo de sincronização                    |
| `alert`     | Após persistir alertas (email ou push enviado)          |
| `config`    | Após criar ou desativar configuração de alerta          |
| `heartbeat` | A cada 25 segundos (evita que proxies fechem conexão)   |

```js
const es = new EventSource(`/api/v1/events?token=${jwt}`)
es.addEventListener('sync',      () => { /* recarregar dashboard */ })
es.addEventListener('alert',     () => { /* recarregar histórico */ })
es.addEventListener('config',    () => { /* recarregar configs   */ })
es.addEventListener('heartbeat', () => { /* ignorar               */ })
```

---

## Debug — `/debug` (apenas ADMIN)

**Não expor em produção.**

### `GET /debug/pdv/products`
Lista todos os produtos do PDV (incluindo inativos).

**Response `200`:** `[{ "id": 1, "codigo": "SH001", "nome": "...", "estoque": 3.000, "unidade": "UN" }]`

---

### `PATCH /debug/pdv/products/{id}/stock`
**Request body:** `{ "estoque": 5.0 }` (>= 0)

**Response `200`:** `{ "id": 1, "estoque": 5.0 }`
**Response `400`:** `{ "error": "estoque deve ser >= 0" }`
**Response `404`:** sem body

---

## Comandos úteis

```bash
# Build
./mvnw clean package -DskipTests

# Subir a aplicação
./mvnw spring-boot:run

# Rodar todos os testes
./mvnw test

# Login
POST http://localhost:8080/api/v1/auth/login
{"email": "...", "password": "..."}

# Forçar sync manual
POST http://localhost:8080/api/v1/sync/now
Authorization: Bearer {token}

# Limpar alertas e cooldowns para testar
DELETE FROM alerts;
UPDATE products SET last_alert = NULL;

# Verificar produtos críticos no banco (exclui zerados)
SELECT name, sku, current_stock, min_stock
FROM products
WHERE current_stock > 0 AND current_stock <= min_stock AND active = true
ORDER BY current_stock;

# Verificar produtos zerados
SELECT name, sku, current_stock FROM products
WHERE current_stock <= 0 AND active = true;

# Ver histórico de alertas
SELECT a.triggered_at, p.name, a.type, a.status
FROM alerts a JOIN products p ON a.product_id = p.id
ORDER BY a.triggered_at DESC;

# Ver configuração atual de sync
SELECT * FROM app_settings;
```

---

## O que ainda falta implementar

- [ ] Frontend (login + dashboard + produtos críticos + histórico de alertas)
- [ ] Service worker no frontend para receber push nos browsers da loja
- [ ] Configuração do intervalo de sync via frontend (API já pronta)
- [ ] Ajuste de estoque mínimo por produto via frontend (API já pronta)
- [ ] Quando houver acesso ao PDV real: trocar connection string do `pdv.datasource`
