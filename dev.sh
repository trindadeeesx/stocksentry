#!/usr/bin/env bash
# Sobe o ambiente de dev inteiro: Postgres + Redis (docker compose), backend
# (Spring Boot, porta 8080) e frontend (Angular, porta 4200).
# Ctrl+C derruba backend e frontend; os containers continuam rodando
# (use --down para pará-los também).
#
# Uso: ./dev.sh [--down]
#   FRONT_DIR=/caminho/do/stocksentry-web ./dev.sh   # se o front não estiver ao lado
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
FRONT_DIR="${FRONT_DIR:-$ROOT/../stocksentry-web}"
DOWN_ON_EXIT=false
[[ "${1:-}" == "--down" ]] && DOWN_ON_EXIT=true

info() { printf '\033[1;36m[dev]\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m[dev]\033[0m %s\n' "$*" >&2; exit 1; }

# ── Pré-requisitos ──────────────────────────────────────────────
[[ -f "$ROOT/src/main/resources/application-local.yml" ]] \
  || fail "Falta src/main/resources/application-local.yml (veja o README)."
[[ -d "$FRONT_DIR" ]] || fail "Front não encontrado em $FRONT_DIR (defina FRONT_DIR)."
command -v npm >/dev/null || fail "npm não encontrado."

# Docker sem sudo só funciona se a sessão já tiver o grupo docker.
DOCKER=(docker)
if ! docker info >/dev/null 2>&1; then
  info "Sem acesso ao Docker nesta sessão; usando sudo (ou faça logout/login para o grupo docker valer)."
  DOCKER=(sudo docker)
fi

# ── Infra ───────────────────────────────────────────────────────
info "Subindo Postgres e Redis..."
"${DOCKER[@]}" compose -f "$ROOT/docker-compose.yml" up -d

wait_port() {
  local name=$1 port=$2
  for _ in $(seq 1 60); do
    (echo >"/dev/tcp/127.0.0.1/$port") 2>/dev/null && { info "$name pronto (:$port)"; return; }
    sleep 1
  done
  fail "$name não respondeu na porta $port."
}
wait_port Postgres 5432
wait_port Redis 6379

# ── Encerramento ────────────────────────────────────────────────
cleanup() {
  trap - INT EXIT
  trap '' TERM   # o próprio script ignora o TERM que manda para o grupo
  info "Encerrando backend e frontend..."
  # kill 0 = todo o grupo de processos: pega também a JVM que o
  # spring-boot:run cria por fora e o node do ng serve.
  kill -TERM 0 2>/dev/null || true
  wait 2>/dev/null || true
  if $DOWN_ON_EXIT; then
    info "Parando containers..."
    "${DOCKER[@]}" compose -f "$ROOT/docker-compose.yml" stop
  fi
}
trap cleanup INT TERM EXIT

prefix() { sed -u "s/^/$1 /"; }

# ── Backend ─────────────────────────────────────────────────────
info "Iniciando backend em http://localhost:8080 ..."
( cd "$ROOT" && exec ./mvnw -q spring-boot:run ) 2>&1 | prefix $'\033[1;33m[back]\033[0m' &

# ── Frontend ────────────────────────────────────────────────────
if [[ ! -d "$FRONT_DIR/node_modules" ]]; then
  info "Instalando dependências do front (npm ci)..."
  ( cd "$FRONT_DIR" && npm ci --no-audit --no-fund )
fi
info "Iniciando frontend em http://localhost:4200 ..."
( cd "$FRONT_DIR" && exec npx ng serve --port 4200 ) 2>&1 | prefix $'\033[1;35m[front]\033[0m' &

info "Tudo subindo. Ctrl+C para parar."
wait
