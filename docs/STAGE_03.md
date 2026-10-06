# Stage 03 — Economy & Shop

Version: `0.4.0`

## Goal
Create a server-authoritative virtual economy that is safe to connect to real-money billing later.

## Implemented
- Wallet balances: coin / gem / energy / love / power
- Immutable wallet ledger with idempotency keys
- Advisory transaction lock on wallet spending
- Store catalog and virtual-currency checkout
- User inventory + immutable inventory ledger
- Booster inventory and consume endpoint
- Social gifts with blocked-user checks
- Gift opening with one-time reward claim
- Colony capacity upgrades and premium banner entitlement
- User entitlements / season-pass groundwork
- Purchase receipt staging table (NO client-side grant)
- Billing product aliases for Play / Iran / China
- Android Shop UI shell
- Google Play Billing 9.1.0 dependency only in the `play` flavor
- Play Billing bridge queries provider-localized gem product prices

## Critical security rule
A mobile receipt never grants gems by itself. `POST /billing/receipts` only stores a hashed receipt/token and returns `grant:false`. Provider-side verification and one-time grant are deliberately reserved for the Billing stage.

## Store flow
`Catalog -> virtual balance -> atomic spend -> order -> inventory/entitlement/colony effect`

## Gift flow
`Sender balance -> atomic spend -> gift record -> receiver opens -> one-time claim -> non-premium social rewards`

## Not yet implemented
- Google Play Developer API server verification
- Iran gateway callback verification
- China store receipt verification
- Google Play acknowledge/consume after server grant
- Refund/revocation worker
- Battle-pass progression UI
