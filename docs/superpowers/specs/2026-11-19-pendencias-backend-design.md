# Pendências do backend — design

## Por quê
O front (`stocksentry-web`) chama endpoints que o back não tem, e alguns erros do back saem com status errado. O objetivo é fechar essas lacunas no back antes de mexer no front, com o produto já genérico (sem Meiliy/PDV).

## Escopo (uma branch por item)

1. **SSE — `feature/sse-eventos`**
   - `POST /api/v1/events/ticket` (autenticado) → `{ticket}`: aleatório, de uso único, vale 30s e fica associado ao usuário.
   - `GET /api/v1/events?ticket=...` → `SseEmitter`. O ticket é consumido na abertura, e um ticket inválido ou expirado dá 401.
   - Registro em memória das conexões, separado por tenant. Basta enquanto houver uma instância só.
   - Eventos: `alert` (depois do disparo, em `AlertService`), `config` (create/delete de config de alerta) e `heartbeat` a cada 25s.
   - Tickets ficam em memória (`ConcurrentHashMap` + expiração).
2. **Mínimo do produto — `feature/min-stock`**
   - `PATCH /api/v1/products/{id}/min-stock` com body `{minStock}` → `ProductResponse`.
   - Isolado por tenant, valida `>= 0` e invalida o cache `critical-products`.
   - Se o produto entrar em zona crítica, publica `StockBelowMinEvent`. Se sair, chama o `resetAlert`, como na movimentação.
3. **Relatório — `feature/relatorio-alertas`**
   - `POST /api/v1/alerts/report?days=7|30` → `{message, timestamp}`.
   - Envia um e-mail HTML via Resend para as configs EMAIL ativas do tenant, com alertas do período, produtos críticos agora e total de movimentações.
   - `days` fora de 7 e 30 → 400. Sem destino de e-mail → 422.
4. **Erros — `fix/global-exception-handler`**
   - Import certo do `AccessDeniedException` (o do Spring Security).
   - Corpo ilegível, enum inválido ou tipo errado (`HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`) → 400.
   - Recurso inexistente → 404, com uma `NotFoundException` própria no lugar de `IllegalArgumentException("... not found")`.
   - Tirar o `printStackTrace` e usar log.
5. **Histórico paginado — `feature/historico-alertas-paginado`**
   - `GET /alerts/history` devolve `Page<AlertResponse>`, aceita `Pageable` e mantém a ordem por `triggeredAt desc`.
6. **Usuários — `fix/register-e-usuarios`**
   - `POST /auth/register` (público): cria tenant + ADMIN e devolve `AuthResponse`. Slug duplicado → 409, e-mail duplicado → 409.
   - `POST /api/v1/users` (só ADMIN): cria um OPERATOR no tenant do ADMIN.
   - `GET /api/v1/users` (só ADMIN): lista os usuários do tenant.
   - Quem não é ADMIN → 403.
7. **`CLAUDE.md` — `chore/claude-md`:** reescrever com o estado novo, por último.

## Abordagens consideradas (SSE)
- A. JWT no `?token=`. Zero mudança no front, mas o token de 24h vaza em logs e no histórico.
- **B. Ticket de uso único (escolhida pelo usuário).** Nenhum token longo vai na URL. Custa um endpoint a mais e um ajuste no front.
- Redis pub/sub entre instâncias: descartado por enquanto, porque há uma instância só.

## Não-objetivos
- Restringir o que o OPERATOR pode fazer.
- Sync/PDV e `/settings` (saem do front depois).
- Escalar o SSE para várias instâncias.

## Decisões minhas (o usuário pode corrigir)
- O registro do relatório não é persistido.
- O `/users` não permite criar outro ADMIN.
- Cada item tem teste unitário (Mockito), no padrão dos testes existentes.
- Ordem de execução: 4 → 5 → 2 → 6 → 3 → 1 → 7. Primeiro o handler de erros, porque os outros itens dependem do 404.

## Dependências entre branches
Os itens 2, 3, 5 e 6 lançam `NotFoundException`, que nasce no item 4. Cada branch sai do `master`. Se o 4 ainda não estiver mergeado, a branch seguinte sai da branch do 4, e isso vai avisado no PR.
