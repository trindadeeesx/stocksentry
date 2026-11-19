# StockSentry

Controle de estoque multi-tenant com alerta de estoque crítico por e-mail (Resend) e push (Web Push/VAPID). Peça de portfólio e vitrine para freela com pequenos negócios.

## Stack

- **Backend (`stocksentry/`):** Java 21, Spring Boot 4.0.5, Postgres, Redis (cache), Flyway (V1–V4), JWT. Pacotes `domain/`, `application/`, `infraestructure/`, `web/`.
- **Frontend (`stocksentry-web/`):** Angular 21 standalone + signals, deploy pensado para o Railway.

## Já feito (PR mergeado)

- Lock pessimista na movimentação de estoque + `@Version` em `Product` (migration V4).
- Alerta via `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`, evento só com `productId` e cooldown atômico (`claimAlertSlot`).
- Isolamento por tenant no delete de config de alerta e na chave do cache de produtos críticos.
- Testes unitários com Mockito dos services.

## Pendências conhecidas

- **Endpoints que o front chama e o back não tem:** `/api/v1/sync/status`, `/api/v1/sync/now`, `/api/v1/settings` (GET/PATCH), `/api/v1/events` (SSE), `PATCH /api/v1/products/{id}/min-stock`, `POST /api/v1/alerts/report`. Decidir caso a caso: implementar no back ou remover do front.
- **Marca antiga no front:** "Meiliy Cosméticos" aparece em 9 lugares (topbar, login, títulos das rotas, `index.html`), e o `CLAUDE.md` do front também cita a Meiliy. Tirar tudo: o produto agora é genérico e se chama StockSentry.
- **`GlobalExceptionHandler`:** importa `java.nio.file.AccessDeniedException` (errado, deveria ser o do Spring Security). Produto inexistente responde 400 (`IllegalArgumentException`), mas deveria ser 404.
- **Teste de integração com Testcontainers** provando o lock com duas saídas simultâneas.
- **CI no GitHub Actions** e **README de verdade** (prints, `docker compose up`, usuário demo).

## Regras de trabalho

- Uma branch por tarefa (`feature/` ou `fix/`).
- Commits pequenos em PT-BR, no padrão Conventional Commits, sem `Co-Authored-By`.
- Push e PR só quando o usuário pedir. O merge é sempre do usuário.
