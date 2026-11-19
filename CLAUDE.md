# StockSentry

Controle de estoque multi-tenant com alerta de estoque crítico por e-mail (Resend) e push (Web Push/VAPID). Peça de portfólio e vitrine para freela com pequenos negócios.

## Stack

- **Backend (`stocksentry/`):** Java 21, Spring Boot 4.0.5, Postgres, Redis (cache), Flyway (V1–V4), JWT. Pacotes `domain/`, `application/`, `infraestructure/`, `web/`.
- **Frontend (`stocksentry-web/`):** Angular 21 standalone + signals, deploy pensado para o Railway.

## Rodar local

- `./dev.sh` na raiz: sobe o docker compose, backend (:8080) e frontend (:4200). Ctrl+C para; `--down` para os containers também.
- Requer `src/main/resources/application-local.yml` (ignorado pelo git): datasource, `security.jwt.secret`, `resend.*`, `vapid.*`.

## Já feito

Mergeado:

- Lock pessimista na movimentação de estoque + `@Version` em `Product` (migration V4).
- Alerta via `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`, evento só com `productId` e cooldown atômico (`claimAlertSlot`).
- Isolamento por tenant no delete de config de alerta e na chave do cache de produtos críticos.
- Testes unitários com Mockito dos services.

Em branches empilhadas aguardando merge do usuário (nesta ordem):

1. `fix/cache-redis-listas`: cache `critical-products` usa serializer com tipo fixo `List<ProductResponse>` (`Stream.toList()` saía sem tipo e quebrava a leitura com 500).
2. `fix/global-exception-handler`: `NotFoundException`, `ConflictException` e `BusinessRuleException` em `application/common`; contrato de erros abaixo.
3. `feature/historico-alertas-paginado`: `GET /api/v1/alerts/history` devolve `Page`.
4. `feature/min-stock`: `PATCH /api/v1/products/{id}/min-stock` (entrar/sair de crítico dispara evento/reset do alerta).
5. `fix/register-e-usuarios`: `POST /api/v1/auth/register` público cria empresa + ADMIN; `POST/GET /api/v1/users` só ADMIN (cria OPERATOR).
6. `feature/relatorio-alertas`: `POST /api/v1/alerts/report?days=7|30` envia e-mail HTML para os destinos EMAIL do tenant (422 se não houver).
7. `feature/sse-eventos`: `POST /api/v1/events/ticket` (autenticado, ticket de uso único, 30s) + `GET /api/v1/events?ticket=` (SSE; eventos `alert`, `config` e `heartbeat` a cada 25s; registro em memória, uma instância).

## Contrato de erros

Corpo sempre `{"error": <mensagem>}`.

| Exceção | Status | `error` |
|---|---|---|
| `NotFoundException`, `NoResourceFoundException` | 404 | message / `"Not found"` |
| `ConflictException` | 409 | message |
| `BusinessRuleException` | 422 | message |
| `HttpMessageNotReadableException` | 400 | `"Corpo da requisição inválido"` |
| `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException` | 400 | `"Parâmetro inválido: <nome>"` |
| `org.springframework.security.access.AccessDeniedException` | 403 | `"Access denied"` |
| `ResponseStatusException` | o próprio status (ex.: 401 para ticket SSE inválido) | motivo |
| `Exception` | 500 | `"Internal server error"` (com `log.error`) |

## Pendências conhecidas

**Front:**

- Remover sync/PDV: `/sync/status`, `/sync/now`, `/settings`, painel Debug PDV e cards Criados/Atualizados/Recuperados.
- SSE: pedir o ticket (`POST /events/ticket`) antes de cada conexão.
- Consumir o histórico paginado e o novo `/auth/register` e `/users`.
- Limpeza da marca antiga: textos e cor já trocados na branch `fix/remover-meiliy` do front (aguardando merge do usuário); falta reescrever o `CLAUDE.md` do front (genérico, multiusuário, sem a marca antiga).

**Back:**

- Corrida em register/createOperator vira 500 (`DataIntegrityViolationException` não mapeada para 409).
- E-mail é case-sensitive.
- Falha do Resend no meio do relatório vira 422 com envio parcial.
- SSE não fecha os emitters no shutdown.
- Teste de integração com Testcontainers provando o lock com duas saídas simultâneas.
- CI no GitHub Actions.
- README de verdade (prints, `docker compose up`, usuário demo).

## Regras de trabalho

- Uma branch por tarefa (`feature/` ou `fix/`).
- Commits pequenos em PT-BR, no padrão Conventional Commits, sem `Co-Authored-By`.
- Push e PR só quando o usuário pedir. O merge é sempre do usuário.
