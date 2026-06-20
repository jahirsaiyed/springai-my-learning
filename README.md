# AI Customer Support Agent

Multi-tenant AI customer support agent built with Spring AI, featuring four memory types, subagent orchestration, hybrid search, and semantic caching.

## Architecture

```
┌─────────────┐  ┌──────────────┐
│   Chat UI   │  │   Admin UI   │
│  (Next.js)  │  │  (Next.js)   │
└──────┬──────┘  └──────┬───────┘
       │                │
       └───────┬────────┘
               │
     ┌─────────▼─────────┐
     │     API Module     │
     │  REST / SSE / WS   │
     └─────────┬─────────┘
               │
     ┌─────────▼─────────┐
     │   Agents Module    │
     │   Orchestrator     │
     │  ┌───┬───┬───┐    │
     │  │Ord│Ref│Kno│Esc│ │
     │  └───┴───┴───┘    │
     └─────────┬─────────┘
               │
  ┌────────────▼────────────┐
  │     Memory Module       │
  │ Episodic │ Semantic     │
  │ Procedural │ Shared     │
  └────────────┬────────────┘
               │
  ┌────┬───────┴───────┬────┐
  │ PG │    Redis       │ ES │
  └────┘               └────┘
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.4.4, Java 21, Spring AI 1.0.0 |
| LLM | Core42 API (gpt-4.1) via OpenAI-compatible endpoint |
| Database | PostgreSQL 16 + pgvector |
| Cache | Redis 7 |
| Search | Elasticsearch 8.15.0 |
| Frontend | Next.js 15, React 19, Tailwind CSS 4, Zustand 5 |
| Auth | JWT + GitHub OAuth |
| Deploy | Render (backend) + Vercel (frontends) |

## Modules

| Module | Package | Purpose |
|--------|---------|---------|
| `core` | `com.example.core` | Tenant isolation, user/auth entities, schema routing |
| `memory` | `com.example.memory` | Four memory types, semantic cache, hybrid search |
| `agents` | `com.example.agents` | Orchestrator, subagents, tools, guardrails, observability |
| `admin` | `com.example.admin` | Admin API: knowledge, procedures, insights, analytics |
| `api` | `com.example.api` | REST/SSE/WebSocket endpoints, security, Flyway migrations |

## Memory Types

- **Episodic** — Conversation history per customer, session caching via Redis
- **Semantic** — Knowledge base with document ingestion, vector embeddings (pgvector), and hybrid search (vector + BM25 via RRF)
- **Procedural** — YAML-defined workflows with step execution tracking and approval flow
- **Shared** — Cross-conversation insights with review queue (approve/reject)

## Subagents

The orchestrator classifies intent and routes to specialized subagents:

| Agent | Handles |
|-------|---------|
| OrderAgent | Order lookup, tracking, cancellation |
| RefundAgent | Refund eligibility, processing, status |
| KnowledgeAgent | FAQ, policy questions, product info |
| EscalationAgent | Human handoff when confidence is low |

## Semantic Cache

Three-tier caching to reduce LLM calls:

1. **Redis L1** — Exact query hash match (instant)
2. **pgvector L2** — Cosine similarity > 0.95
3. **RAG Cache** — Cached retrieval context, similarity > 0.98

TTLs are query-type-aware (order status: 5min, policy FAQ: 24h).

## Prerequisites

- Java 21
- Docker & Docker Compose
- Node.js 18+ (for frontends)

## Getting Started

### 1. Start infrastructure

```bash
docker compose up -d
```

This starts PostgreSQL (with pgvector), Redis, and Elasticsearch.

### 2. Set environment variables

```bash
export COMPASS_API_KEY=<your-core42-api-key>
export GITHUB_CLIENT_ID=<your-github-oauth-client-id>
export GITHUB_CLIENT_SECRET=<your-github-oauth-client-secret>
```

### 3. Run the backend

```bash
./gradlew :api:bootRun
```

The API starts on `http://localhost:8080`. Flyway runs migrations automatically.

### 4. Run the Chat UI

```bash
cd chat-ui
npm install
npm run dev
```

Opens on `http://localhost:3000`.

### 5. Run the Admin UI

```bash
cd admin-ui
npm install
npm run dev
```

Opens on `http://localhost:3001`.

## API Endpoints

### Auth
- `POST /api/auth/signup` — Email/password registration
- `POST /api/auth/signin` — Email/password login
- `POST /api/auth/github` — GitHub OAuth code exchange

### Chat
- `POST /api/chat/start` — Start a new conversation
- `POST /api/chat/{conversationId}` — Send a message
- `POST /api/chat/{conversationId}/resolve` — Resolve conversation
- `POST /api/chat/stream/start` — Start with SSE streaming
- `POST /api/chat/stream/{conversationId}` — Stream a message (SSE)
- WebSocket: STOMP over `/ws/chat` with SockJS

### Admin
- `/api/admin/knowledge/**` — Knowledge document CRUD + ingestion
- `/api/admin/procedures/**` — Procedure CRUD + approval workflow
- `/api/admin/insights/**` — Shared insight review queue
- `/api/admin/conversations/**` — Conversation viewer + decision trace
- `/api/admin/analytics/**` — Dashboard, token usage, agent breakdown

## Multi-Tenancy

Tenant isolation uses separate PostgreSQL schemas. Each request includes an `X-Tenant-Slug` header, resolved by `TenantFilter` into `TenantContext`. Flyway runs per-tenant migrations on tenant creation.

## Deployment

### Backend (Render)

The `render.yaml` Blueprint defines a Docker web service with Redis and PostgreSQL. Set these environment variables in the Render dashboard:

- `COMPASS_API_KEY`
- `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET`
- `ES_HOST`
- `ALLOWED_ORIGINS` (comma-separated Vercel URLs)

### Frontends (Vercel)

Import `chat-ui/` and `admin-ui/` as separate Vercel projects. Set `NEXT_PUBLIC_API_URL` to point to the Render backend URL.

## Project Structure

```
├── core/           # Tenant entities, schema routing, user/auth
├── memory/         # 4 memory types, cache, search (ES + pgvector)
├── agents/         # Orchestrator, subagents, tools, guardrails
├── admin/          # Admin REST controllers, analytics
├── api/            # Boot app, security, chat endpoints, migrations
├── chat-ui/        # Next.js customer chat interface
├── admin-ui/       # Next.js admin dashboard
├── infra/          # DB init script
├── Dockerfile      # Multi-stage backend build
├── docker-compose.yml
└── render.yaml     # Render Blueprint
```
