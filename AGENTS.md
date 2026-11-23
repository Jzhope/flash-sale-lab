# Repository maintenance guidance

Optimize this repository for maintainability and beginner readability. Follow CONTRIBUTING.md and keep the beginner reading path in docs/learning-guide.md accurate.

- Use controller -> service -> repository for HTTP business flows. Only repositories use Spring JDBC.
- Keep public HTTP DTOs separate from internal models, and use enums for closed sets such as order states and event types.
- Keep transaction ownership in services; preserve atomic inventory/order/Outbox writes and row-lock-based state transitions.
- Treat event IDs, event names, API paths, JSON fields, and status values as compatibility contracts.
- Use descriptive concrete classes; avoid speculative infrastructure and interface/implementation pairs with no second implementation.
- Explain design reasons in Chinese Java comments; keep README.md in English.
- Use JDK 17, Spotless, and Checkstyle. Run Maven Wrapper validate and clean verify -Dintegration=true for Java behavior/refactoring changes. Preserve architecture and business tests.
- Update docs when structure or behavior changes. Never claim tests, deployment, throughput, or availability that were not actually verified.
- Preserve user edits and secrets, use scoped commits, and do not rewrite shared Git history.
