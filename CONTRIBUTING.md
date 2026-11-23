# Maintaining Flash Sale Lab

The project should be readable by someone learning Spring Boot. Prefer explicit names, a visible request path, and concrete classes over framework tricks or extra abstraction layers.

## Responsibilities

- Controllers handle HTTP, validation, authenticated identity, and DTO conversion.
- Services own business decisions and database transaction boundaries.
- Repositories own SQL and row mapping. Use bound parameters for external input.
- DTOs define public JSON; models represent internal data. Never expose credential models.
- Messaging code adapts Kafka and Outbox delivery; scheduled tasks trigger services without duplicating business rules.
- Comments explain invariants and failure windows. Keep Java comments in Chinese for learners and the README in English.

Architecture tests enforce the main dependency rules. Do not add an interface/implementation pair, generic repository, mapper framework, or shared base controller unless a concrete requirement benefits from it.

## Contracts to Preserve

Inventory reservation, order insertion, and the Outbox event commit together. Public payment/cancellation/expiration entry points must keep their transaction boundaries. Locks and state checks prevent duplicate inventory release.

Kafka delivery is at least once. Keep the event ID stable during retries and preserve notification deduplication. API paths, DTO fields, status strings, and event names are compatibility contracts; intentional changes need tests and documentation.

## Verification

Use JDK 17 and Maven Wrapper:

```bash
./mvnw spotless:apply
./mvnw validate
./mvnw clean verify -Dintegration=true
docker compose --env-file .env.example config --quiet
```

On Windows, use `mvnw.cmd`. Integration tests require Docker and fail if it is unavailable. Use `clean` after moving classes so stale bytecode cannot affect component scanning or architecture tests.

For runtime or deployment changes, build the image and run the existing smoke script against the local Compose stack. Keep `.env`, credentials, build output, and recordings out of Git. Do not remove volumes to make a failing test pass.

Add focused behavior tests for changed rules or failure handling. Update the reading guide when code moves, and update API/architecture/operations documentation when behavior changes. Commit coherent changes with an explanation of what changed and how it was verified; avoid rewriting shared history.
