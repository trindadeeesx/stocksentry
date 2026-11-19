# StockSentry

Controle de estoque multi-tenant com alerta de estoque crítico por e-mail (Resend) e push (Web Push/VAPID). Peça de portfólio e vitrine para freela com pequenos negócios.

## Stack

- **Backend (`stocksentry/`):** Java 21, Spring Boot 4.0.5, Postgres, Redis (cache), Flyway (V1–V5), JWT. Pacotes `domain/`, `application/`, `infraestructure/`, `web/`.
- **Frontend (`stocksentry-web/`):** Angular 21 standalone + signals, deploy pensado para o Railway.

## Rodar local

- `./dev.sh` na raiz: sobe o docker compose, backend (:8080) e frontend (:4200). Ctrl+C para; `--down` para os containers também.
- Requer `src/main/resources/application-local.yml` (ignorado pelo git): datasource, `security.jwt.secret`, `resend.*`, `vapid.*`.

## Já feito

- Lock pessimista na movimentação de estoque + `@Version` em `Product` (V4).
- Alerta via `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`, evento só com `productId` e cooldown atômico (`claimAlertSlot`).
- Isolamento por tenant no delete de config de alerta e na chave do cache de críticos; cache `critical-products` com serializer de tipo fixo.
- Exceções próprias em `application/common` e contrato de erros (abaixo).
- `GET /api/v1/alerts/history` paginado.
- `PATCH /api/v1/products/{id}/min-stock` (entrar/sair de crítico dispara evento/reset do alerta).
- `POST /api/v1/auth/register` público cria empresa + ADMIN (5 por IP/hora); `POST/GET /api/v1/users` só ADMIN (cria OPERATOR). E-mail sem diferenciar maiúsculas, com índice único em `lower(email)` (V5).
- `POST /api/v1/alerts/report?days=7|30`: e-mail HTML para os destinos EMAIL do tenant (422 sem destino, 502 se o provedor falhar para algum, 1 por minuto por empresa).
- SSE: `POST /api/v1/events/ticket` (ticket de uso único, 30s, até 5 pendentes por usuário) + `GET /api/v1/events?ticket=` (eventos `alert`, `config` e `heartbeat` a cada 25s; até 50 streams por empresa; em memória, uma instância).
- "Crítico" é sempre `currentStock < minStock` (listagem, relatório e alerta).
- Limites de uso (`RateLimiter`) em memória; IP real atrás de proxy via `server.forward-headers-strategy: native`.

## Contrato de erros

Corpo `{"error": <mensagem>}`, exceto nas rotas `text/event-stream` (só status).

| Exceção | Status | `error` |
|---|---|---|
| `NotFoundException`, `NoResourceFoundException` | 404 | message / `"Not found"` |
| `ConflictException`; `DataIntegrityViolationException` de unicidade (SQLState 23505) | 409 | message / `"Conflict"` |
| `DataIntegrityViolationException` classe 22 (valor não cabe na coluna) | 400 | `"Valor inválido"` |
| outras `DataIntegrityViolationException` (NOT NULL, FK) | 500 | `"Internal server error"` + `log.error` |
| `BusinessRuleException` | 422 | message |
| `TooManyRequestsException` | 429 | message |
| `UpstreamException` (provedor externo) | 502 | message |
| `HttpMessageNotReadableException` | 400 | `"Corpo da requisição inválido"` |
| `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException`, `PropertyReferenceException` (sort) | 400 | `"Parâmetro inválido: <nome>"` |
| `org.springframework.security.access.AccessDeniedException` | 403 | `"Access denied"` |
| `ResponseStatusException` | o próprio status (ex.: 401 ticket SSE inválido) | — |
| `AsyncRequestTimeoutException`, `AsyncRequestNotUsableException` | silencioso (fim de stream SSE) | — |
| `Exception` | 500 | `"Internal server error"` + `log.error` |

## Pendências conhecidas

**Front:**

- Remover sync/PDV: `/sync/status`, `/sync/now`, `/settings`, painel Debug PDV e cards Criados/Atualizados/Recuperados.
- SSE: pedir o ticket (`POST /events/ticket`) antes de cada conexão. O ticket é de uso único, então o auto-reconnect do `EventSource` reusa um ticket gasto, toma 401 e para: no `onerror`, fechar, pedir ticket novo e abrir outro (vale também depois do timeout de 30 min do servidor).
- Consumir o histórico paginado e o novo `/auth/register` e `/users`; tratar 429 e 502.
- Reescrever o `CLAUDE.md` do front (genérico, multiusuário, sem a marca antiga).

**Back:**

- SSE não fecha os emitters no shutdown (o graceful shutdown espera até 30s).
- Teste de integração com Testcontainers provando o lock com duas saídas simultâneas.
- CI no GitHub Actions.
- README de verdade (prints, `./dev.sh`, usuário demo).

## Regras de trabalho

- Uma branch por tarefa (`feature/` ou `fix/`).
- Commits pequenos em PT-BR, no padrão Conventional Commits, sem `Co-Authored-By`.
- Push e PR só quando o usuário pedir. O merge é sempre do usuário.
