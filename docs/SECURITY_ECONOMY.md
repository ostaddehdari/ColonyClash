# Economy Security Invariants

1. The Android client is never authoritative for wallet balance, inventory quantity, gift rewards, colony capacity, or paid currency grants.
2. Every wallet debit uses a transaction-scoped advisory lock and an idempotency key.
3. Inventory changes are recorded in an immutable inventory ledger and stack limits are checked under a row lock.
4. Gift opening is a one-time server claim. Premium currency is not granted by social gift payloads in Stage 03.
5. Colony upgrades run in the same database transaction as the wallet debit and require the colony owner.
6. Provider purchase tokens are stored only as SHA-256 hashes in Stage 03.
7. Receipt submission never grants paid value. Provider verification must succeed in the Billing stage before an idempotent grant is created.
8. Refund/revocation processing will reverse entitlements through ledger entries rather than editing historical ledger rows.
