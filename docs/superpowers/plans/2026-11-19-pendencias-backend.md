# Pendências do backend — plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** fechar no back os endpoints e os status de erro que o front espera (SSE com ticket, min-stock, relatório, histórico paginado, register e /users).

**Architecture:** Spring Boot em camadas (`application/`, `domain/`, `infraestructure/`, `web/`). Exceções de negócio próprias, mapeadas no `GlobalExceptionHandler`. SSE com registro de emitters e tickets em memória.

**Tech Stack:** Java 21, Spring Boot 4.0.5 (webmvc, security, data-jpa), Resend, JUnit 5 + Mockito + AssertJ, `MockMvcBuilders.standaloneSetup` para os testes web.

**Spec:** `docs/superpowers/specs/2026-11-19-pendencias-backend-design.md`

## Global Constraints

- Uma branch por task. Cada branch sai da branch da task anterior (PRs empilhados, mergeados em ordem). A Task 1 sai do `master`.
- Commits em PT-BR, Conventional Commits, explicando o porquê, **sem `Co-Authored-By`**. Sem push e sem PR até o usuário pedir.
- Base: `com.trindadeeesx.stocksentry` (abaixo, `…`). Testes em `src/test/java/…`, com tab, no estilo de `AlertServiceTest`.
- Comando de teste: `./mvnw -q test -Dtest='<Classe>'`. Antes de cada commit, rodar a suíte inteira com `./mvnw -q test` e ter 0 falhas.
- Ticket SSE: uso único, TTL de **30s**. Heartbeat a cada **25s**. `days` do relatório só aceita **7 ou 30**.

## Review Focus

1. Endpoint inexistente (ex.: `/api/v1/sync/status`) responde 404, não 500 → teste na Task 1.
2. Ticket SSE reutilizado ou expirado → 401 → teste na Task 6.
3. Um cliente SSE caído não pode derrubar o envio para os outros clientes do tenant → teste na Task 6.
4. `minStock` negativo ou ausente → 400 com `fields.minStock` → teste na Task 3.
5. OPERATOR chamando `POST /users` → 403, e register com slug ou e-mail já usado → 409 sem gravar nada → testes na Task 4.

---

### Task 1: Exceções e `GlobalExceptionHandler` — branch `fix/global-exception-handler`

**Files:**
- Create: `…/application/common/NotFoundException.java`, `ConflictException.java`, `BusinessRuleException.java` (todas `extends RuntimeException`, construtor `(String message)`)
- Modify: `…/web/handler/GlobalExceptionHandler.java`
- Modify: `ProductService.getProduct`, `ProductService.getProductForUpdate`, `AlertService.deleteConfig`, `AuthController.me`: `IllegalArgumentException("... not found")` vira `NotFoundException` com a mesma mensagem
- Test: `…/web/handler/GlobalExceptionHandlerTest.java`; ajustar `AlertServiceTest.shouldNotDeleteConfig_whenItBelongsToAnotherTenant` para esperar `NotFoundException`

**Interfaces:**
- Produces: `NotFoundException` → 404, `ConflictException` → 409, `BusinessRuleException` → 422, todas com corpo `{"error": <message>}`.

Mapeamento do handler (corpo sempre `Map` com `"error"`):

| Exceção | Status | `error` |
|---|---|---|
| `NotFoundException`, `NoResourceFoundException` | 404 | message / `"Not found"` |
| `ConflictException` | 409 | message |
| `BusinessRuleException` | 422 | message |
| `HttpMessageNotReadableException` | 400 | `"Corpo da requisição inválido"` |
| `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException` | 400 | `"Parâmetro inválido: <nome>"` |
| `org.springframework.security.access.AccessDeniedException` | 403 | `"Access denied"` |
| `Exception` | 500 | `"Internal server error"` + `log.error` (sem `printStackTrace`) |

- [ ] **Step 1: Escrever `GlobalExceptionHandlerTest`.** Usar `standaloneSetup(new FakeController()).setControllerAdvice(new GlobalExceptionHandler())`, com um `@RestController` estático no próprio teste com um endpoint por exceção, mais `POST /movement` com `@RequestBody @Valid MovementRequest`. Asserts:
  - `throwNotFound` → 404 e `$.error == "Product not found"`
  - `POST /movement` com `{"type":"IN","quantity":1}` → 400 e `$.error == "Corpo da requisição inválido"`
  - `GET /typed?id=abc` (param `UUID`) → 400 e `$.error == "Parâmetro inválido: id"`
  - `GET /typed` sem `id` → 400
  - Spring `AccessDeniedException` → 403; `ConflictException` → 409; `BusinessRuleException` → 422
  - `GET /nao-existe` → 404 (Review Focus 1)
- [ ] **Step 2: Rodar `./mvnw -q test -Dtest=GlobalExceptionHandlerTest`.** Esperado: FAIL (as exceções não existem).
- [ ] **Step 3: Criar as exceções, ajustar o handler pela tabela e trocar os pontos listados em Files.**
- [ ] **Step 4: Rodar a suíte inteira.** Esperado: PASS.
- [ ] **Step 5: Commit** `fix(api): responder 400/404 corretos e usar o AccessDeniedException do Spring Security`

### Task 2: Histórico de alertas paginado — branch `feature/historico-alertas-paginado`

**Files:**
- Modify: `AlertRepository`: trocar `List<Alert> findAllByTenantIdOrderByTriggeredAtDesc(UUID)` por `Page<Alert> findAllByTenantIdOrderByTriggeredAtDesc(UUID tenantId, Pageable pageable)`
- Modify: `AlertService.findAlertHistory(Pageable) → Page<AlertResponse>`; `AlertController.history(Pageable) → Page<AlertResponse>`
- Test: `AlertServiceTest`

- [ ] **Step 1: Escrever o teste** `shouldReturnHistoryPage_forCurrentTenant`: o repository mockado devolve `PageImpl` com 1 alerta. Assert: `content` tem 1 item com `productName`, `totalElements == 1`, e o repository foi chamado com o tenant atual e o mesmo `Pageable`.
- [ ] **Step 2: Rodar.** Esperado: FAIL (não compila).
- [ ] **Step 3: Implementar.**
- [ ] **Step 4: Rodar a suíte inteira.** Esperado: PASS.
- [ ] **Step 5: Commit** `feat(alert): paginar o histórico de alertas`

### Task 3: `PATCH /products/{id}/min-stock` — branch `feature/min-stock`

**Files:**
- Create: `…/web/dto/product/MinStockRequest.java` (`@NotNull @DecimalMin("0.0") BigDecimal minStock`)
- Modify: `ProductService`: novo `@Transactional @CacheEvict(value = {"products","critical-products"}, allEntries = true) ProductResponse updateMinStock(UUID id, BigDecimal minStock)`. Injetar `ApplicationEventPublisher` e `AlertService`.
- Modify: `ProductController`: `@PatchMapping("/{id}/min-stock")`
- Test: `…/application/product/ProductServiceTest.java`, `…/web/controller/ProductControllerTest.java`

**Interfaces:**
- Consumes: `NotFoundException` (Task 1)
- Regra: usa `getProductForUpdate`. Se antes não estava crítico e passou a estar, publica `StockBelowMinEvent(productId)`. Se estava e deixou de estar, chama `alertService.resetAlert(product)`. Nos dois outros casos, só salva.

- [ ] **Step 1: Escrever `ProductServiceTest`:**
  - `shouldPublishEvent_whenNewMinMakesProductCritical` (estoque 5, novo mínimo 8)
  - `shouldResetAlert_whenNewMinTakesProductOutOfCritical` (estoque 5, mínimo 8 → 3)
  - `shouldNotPublish_whenStateDoesNotChange`
  - `shouldThrowNotFound_whenProductBelongsToAnotherTenant` (`getProductForUpdate` vazio)
- [ ] **Step 2: Escrever `ProductControllerTest`** (standalone + handler + `LocalValidatorFactoryBean`): `{"minStock":-1}` → 400 com `$.fields.minStock`; `{}` → 400 (Review Focus 4).
- [ ] **Step 3: Rodar os dois.** Esperado: FAIL.
- [ ] **Step 4: Implementar.**
- [ ] **Step 5: Rodar a suíte inteira.** Esperado: PASS.
- [ ] **Step 6: Commit** `feat(product): permitir alterar só o estoque mínimo`

### Task 4: `/auth/register` e `/users` — branch `fix/register-e-usuarios`

**Files:**
- Modify: `AuthService.register`: `@Transactional`. Restaurar o fluxo comentado (cria tenant + ADMIN e devolve `AuthResponse` com token) e remover a dependência de usuário logado e o `System.out`. Slug existente → `ConflictException("Slug already in use")`; e-mail existente → `ConflictException("Email already in use")`. As duas checagens acontecem antes de qualquer `save`.
- Modify: `RegisterRequest.tenantSlug`: `@Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$")`
- Create: `…/application/user/UserService.java`:
  - `UserResponse createOperator(CreateUserRequest)`: só ADMIN, senão Spring `AccessDeniedException`. E-mail duplicado → `ConflictException`. Cria com `role = OPERATOR` no tenant do ADMIN.
  - `List<UserResponse> findAll()`: só ADMIN; usa `userRepository.findAllByTenantId`.
- Create: `…/web/dto/user/CreateUserRequest.java` (`@NotBlank name`, `@Email @NotBlank email`, `@NotBlank @Size(min = 8) password`), `UserResponse.java` (`id, name, email, role, active, createdAt`)
- Create: `…/web/controller/UserController.java`: `/api/v1/users`, `POST` → 201, `GET`
- Test: `…/application/auth/AuthServiceTest.java`, `…/application/user/UserServiceTest.java`

- [ ] **Step 1: Escrever `AuthServiceTest`:**
  - `shouldCreateTenantAndAdmin_andReturnToken`: role `ADMIN`, senha salva como hash do encoder, token do `jwtService`
  - `shouldRejectDuplicatedSlug_withoutSaving` / `shouldRejectDuplicatedEmail_withoutSaving`: `ConflictException`, `verify(never()).save` nos dois repositories (Review Focus 5)
- [ ] **Step 2: Escrever `UserServiceTest`:**
  - `shouldCreateOperatorInAdminsTenant`
  - `shouldDenyOperator`: `AccessDeniedException` sem `save` (Review Focus 5)
  - `shouldRejectDuplicatedEmail`
  - `shouldListOnlyCurrentTenantUsers`
- [ ] **Step 3: Rodar os dois.** Esperado: FAIL.
- [ ] **Step 4: Implementar.**
- [ ] **Step 5: Rodar a suíte inteira.** Esperado: PASS.
- [ ] **Step 6: Commit em dois:**
  - `fix(auth): fazer o register criar empresa e primeiro admin`
  - `feat(user): permitir que o admin cadastre operadores`

### Task 5: Relatório por e-mail — branch `feature/relatorio-alertas`

**Files:**
- Create: `…/application/alert/AlertReportService.java`: `ReportResponse sendReport(int days)`
- Create: `…/web/dto/alert/ReportResponse.java` (`String message, LocalDateTime timestamp`)
- Modify: `AlertRepository`: `List<Alert> findAllByTenantIdAndTriggeredAtAfter(UUID tenantId, LocalDateTime from)`
- Modify: `StockMovementRepository`: `long countByTenantIdAndCreatedAtAfter(UUID tenantId, LocalDateTime from)`
- Modify: `AlertController`: `@PostMapping("/report") ReportResponse report(@RequestParam int days)`
- Test: `…/application/alert/AlertReportServiceTest.java`

**Interfaces:**
- Consumes: `BusinessRuleException` (Task 1)
- Regra:
  - `days` fora de {7, 30} → `IllegalArgumentException("days must be 7 or 30")` (400).
  - Destinatários = configs EMAIL ativas do tenant. Se não houver nenhuma → `BusinessRuleException("Nenhum e-mail de alerta configurado")` (422).
  - Envia um e-mail por destinatário via `resend.emails().send`, `from` = `${resend.from}`, assunto `Relatório de estoque — últimos N dias`.
  - O HTML traz: total de alertas do período (enviados/falhos), a lista de produtos críticos agora (nome, SKU, atual/mínimo) e o total de movimentações.
  - `message` = `"Relatório dos últimos N dias enviado para X destinatário(s)."`

- [ ] **Step 1: Escrever `AlertReportServiceTest`:**
  - `shouldSendOneEmailPerRecipient` (captor de `CreateEmailOptions`: assunto, `to`, HTML contendo o nome do produto crítico)
  - `shouldRejectInvalidDays` (`days = 15`)
  - `shouldFail422_whenNoEmailConfig`
  - `shouldReturnMessageWithRecipientCount`
  - Mock do Resend: pegar `Emails emails = resend.emails()` antes do stubbing, como no fix de `AlertServiceTest`.
- [ ] **Step 2: Rodar.** Esperado: FAIL.
- [ ] **Step 3: Implementar.**
- [ ] **Step 4: Rodar a suíte inteira.** Esperado: PASS.
- [ ] **Step 5: Commit** `feat(alert): enviar relatório de estoque por e-mail`

### Task 6: SSE com ticket — branch `feature/sse-eventos`

**Files:**
- Create: `…/application/events/SseTicketService.java`:
  - `String issue(UUID userId, UUID tenantId)`: 32 bytes de `SecureRandom` em base64url
  - `Optional<TicketOwner> consume(String ticket)`: remove na leitura e respeita o TTL de 30s
  - Recebe um `Clock` no construtor para os testes.
  - `record TicketOwner(UUID userId, UUID tenantId)`
- Create: `…/application/events/SseHub.java`:
  - `SseEmitter subscribe(UUID tenantId)`: timeout de 30 min; remove o emitter em `onCompletion`, `onTimeout` e `onError`
  - `void publish(UUID tenantId, String event)`: `IOException` ou `IllegalStateException` num emitter → remove só aquele emitter e continua
  - `@Scheduled(fixedRate = 25_000) void heartbeat()`: manda `heartbeat` para todos
  - Estrutura: `ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>>`
- Create: `…/web/controller/EventsController.java`:
  - `POST /api/v1/events/ticket` → `{"ticket": "..."}`
  - `GET /api/v1/events?ticket=` (`produces = TEXT_EVENT_STREAM_VALUE`): ticket inválido → `ResponseStatusException(UNAUTHORIZED)`
- Modify: `SecurityConfig`: `permitAll` para `GET /api/v1/events` (o ticket é a autenticação) e `dispatcherTypeMatchers(ASYNC, ERROR).permitAll()`
- Modify: `StocksentryApplication`: `@EnableScheduling`
- Modify: `AlertService`:
  - injetar `SseHub`
  - `publish(tenantId, "alert")` ao fim de `processStockAlert`, se algum alerta foi gravado
  - `publish(tenantId, "config")` em `createConfig` e `deleteConfig`
- Test: `SseTicketServiceTest`, `SseHubTest`, `AlertServiceTest` (+ `@Mock SseHub`)

- [ ] **Step 1: Escrever `SseTicketServiceTest`:**
  - `shouldConsumeTicketOnce`: o segundo `consume` vem vazio (Review Focus 2)
  - `shouldRejectExpiredTicket`: `Clock` avançado 31s
  - `shouldRejectUnknownTicket`
- [ ] **Step 2: Escrever `SseHubTest`:**
  - `shouldDeliverOnlyToSameTenant`
  - `shouldDropBrokenEmitter_andKeepDeliveringToOthers`: subclasse de `SseEmitter` cujo `send` lança `IOException`; o emitter saudável recebe, e um segundo `publish` não tenta mais o quebrado (Review Focus 3)
- [ ] **Step 3: Escrever em `AlertServiceTest`:**
  - `shouldPublishAlertEvent_afterDispatch`
  - `shouldNotPublish_whenSlotNotClaimed`
  - `shouldPublishConfigEvent_onCreateAndDelete`
- [ ] **Step 4: Rodar.** Esperado: FAIL.
- [ ] **Step 5: Implementar.**
- [ ] **Step 6: Rodar a suíte inteira.** Esperado: PASS.
- [ ] **Step 7: Verificação manual com o back rodando:**
  - `curl -X POST /api/v1/events/ticket` com Bearer, depois `curl -N "/api/v1/events?ticket=…"`: deve chegar `event:heartbeat` em até 25s
  - reusar o mesmo ticket → 401
- [ ] **Step 8: Commit** `feat(events): eventos em tempo real via SSE com ticket de uso único`

### Task 7: `CLAUDE.md` — branch `chore/claude-md`

**Files:** Modify: `CLAUDE.md`

- [ ] **Step 1: Atualizar o `CLAUDE.md`:**
  - **Já feito:** incluir os itens das Tasks 1–6 e o fix do cache Redis.
  - **Pendências:** ficam a remoção de sync/PDV/settings no front, o front passar a usar o ticket SSE e o histórico paginado, o Testcontainers, a CI e o README.
  - **Contrato de erros:** registrar a tabela da Task 1.
- [ ] **Step 2: Commit** `docs: atualizar CLAUDE.md com o estado do backend`
