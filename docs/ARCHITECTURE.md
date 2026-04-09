# Architecture

## System shape

StatusFlow is a monorepo with three applications:

- `apps/mobile`: Android app for end users
- `apps/web`: web app for operators
- `apps/api`: backend API and business logic

Supporting modules:

- `packages/shared-types`: shared enums and schema references
- `packages/api-contract`: API contract artifacts
- `packages/test-data`: reusable fixtures and seed inputs
- `infra/docker`: local environment setup

## Initial stack

### Mobile

- Kotlin
- Jetpack Compose
- Room
- Retrofit
- OkHttp
- DataStore

### Web

- React
- Vite
- TypeScript

### API

- FastAPI
- SQLAlchemy
- Pydantic
- JWT auth

### Data

- PostgreSQL for server persistence
- Room for mobile local persistence

## High-level data flow

1. Mobile app authenticates against API.
2. API issues access token.
3. Mobile app stores auth/session config locally.
4. Mobile app reads orders from API and caches them in Room.
5. Web app reads and updates orders through the same API.
6. API persists state changes in PostgreSQL and writes status history.
7. Mobile app refreshes and reconciles local cache with remote state.

## Initial domain entities

- `User`
- `Order`
- `OrderComment`
- `OrderStatusHistory`
- `Session`

## Important design decisions

- business rules for status transitions live in the API, not in clients
- mobile app keeps local cached data but does not own source of truth
- clients share status enums and DTO definitions through contracts
- status history is append-only for easier auditing and testing
- debug builds must allow traffic inspection without certificate pinning

## Key testing seams

- auth and token expiration
- offline cache and stale reads
- concurrent updates from web and mobile
- invalid status transitions
- partial failure between client cache and server state
- API contract regressions

## Planned local environment

Local development should run via Docker Compose for:

- PostgreSQL
- API
- web app

The Android app runs separately in emulator/device and points to the local API environment.
