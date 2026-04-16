# StockSentry

Sistema de controle de estoque com suporte a multi-tenant, alertas automáticos e notificações (email e push).

## 🚀 Tecnologias

* Java 25
* Spring Boot 4
* Spring Security (JWT)
* PostgreSQL
* Redis (cache)
* Flyway (migrations)
* Web Push (VAPID)
* Resend (email)

---

## 📦 Funcionalidades

* Multi-tenant por empresa
* Cadastro e gestão de produtos
* Movimentação de estoque (entrada, saída, ajuste)
* Detecção de estoque crítico
* Alertas automáticos (email e push)
* Histórico de movimentações
* Cache com Redis

---

## ⚙️ Como rodar o projeto

### Pré-requisitos

* Java 25
* PostgreSQL
* Redis

---

### 1. Clone o projeto

```bash
git clone https://github.com/trindadeeesx/stocksentry.git
cd stocksentry
```

---

### 2. Configurar variáveis

Crie um `application-local.yml` ou configure variáveis de ambiente:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/stocksentry
    username: postgres
    password: postgres

security:
  jwt:
    secret: SUA_SECRET_AQUI
    expiration: 86400000

resend:
  api-key: SUA_API_KEY
  from: no-reply@seudominio.com

vapid:
  public-key: SUA_PUBLIC_KEY
  private-key: SUA_PRIVATE_KEY
  subject: mailto:voce@email.com
```

---

### 3. Rodar

```bash
./mvnw spring-boot:run
```

ou

```bash
mvn spring-boot:run
```

---

## 📡 Endpoints principais

### Auth

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
```

---

### Produtos

```http
POST   /api/v1/products
GET    /api/v1/products
GET    /api/v1/products/{id}
PUT    /api/v1/products/{id}
DELETE /api/v1/products/{id}
DELETE /api/v1/products/{id}/hard
GET    /api/v1/products/critical
GET    /api/v1/products/out-of-stock
```

---

### Estoque

```http
POST /api/v1/products/{productId}/movements
GET  /api/v1/products/{productId}/movements
GET  /api/v1/movements
```

---

### Alertas

```http
POST   /api/v1/alerts/config
GET    /api/v1/alerts/config
DELETE /api/v1/alerts/config/{id}
GET    /api/v1/alerts/history
```

---

### Push

```http
GET    /api/v1/push/vapid-key
POST   /api/v1/push/subscribe
DELETE /api/v1/push/subscribe
```

---

## 🔐 Autenticação

Utiliza JWT.

Após login:

```http
Authorization: Bearer SEU_TOKEN
```

---

## 📊 Observabilidade

Health check: (em progresso)

```http
GET /actuator/health
```

---

## 🧠 Arquitetura

* Separação por camadas:

    * `application` (regras de negócio)
    * `domain` (modelo)
    * `infraestructure` (infra)
    * `web` (controllers)

* Uso de eventos:

    * `StockBelowMinEvent` para desacoplar alertas

---

## 📌 Próximos passos

* Melhorar sistema de alertas (evitar spam)
* Implementar controle mais robusto de multi-tenant
* Adicionar retry e fila para notificações
