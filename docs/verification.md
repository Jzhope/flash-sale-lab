# Local Verification Record

Validated on Windows with Java 17, Docker Desktop using Linux containers, and a 768 MB application container limit.

## Automated Tests

After the layering refactor, `mvn clean verify -Dintegration=true` passed all 28 tests with no failures, errors, or skipped tests: 2 authentication tests, 2 order API tests, 11 order service tests, 5 cache tests, 1 publisher-failure test, 3 real middleware integration tests, and 4 architecture rules.

The additional coverage verifies that public order JSON stays compatible, payment/cancellation/expiration roll back when Outbox writes fail, invalid or unavailable cache entries fall back to SQL, and JDBC remains inside repositories. Spotless and Checkstyle also passed through the Maven validation phase.

## Container Verification

The image built successfully, and the Compose services reached healthy status. The smoke script exercised registration, product lookup, ordering, idempotent retry, simulated payment, asynchronous Kafka notification, and logout.

Verification caught and corrected Kafka volume ownership, a dead-letter destination mismatch, and PowerShell array handling in the smoke script. CI also runs the full Compose stack and smoke test.

## Concurrent Buyers

The included k6 script ran with 120 independent buyers against product 1, initially holding 100 units. Every successful buyer retried the same request key.

- All 220 business checks passed.
- SQL contained exactly 100 orders for product 1, with remaining stock at 0.
- Duplicate requests did not create extra orders.
- The Outbox drained to 0 unpublished events.

This finite local test verifies contention behavior, not sustained capacity. Aggregate HTTP metrics include registration, initial orders, and retries; they must not be presented as order-only throughput or latency.

## Validation Limits

The Kubernetes manifests rendered with `kubectl kustomize deploy/k8s`. No Kubernetes context was configured, so live deployment, probe behavior, rolling updates, and multi-Pod execution remain unverified.
