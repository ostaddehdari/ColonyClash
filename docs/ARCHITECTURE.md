# Architecture baseline

## Product domains
- Identity & age gate
- Social graph (friends, squads, rivals, block)
- Colonies (membership, roles, upgrades, ranking)
- Match service (live + asynchronous authoritative result validation)
- War/season service
- Economy (ledger, inventory, entitlements, store)
- Gifts/social status
- Invites/referrals
- Chat/realtime/emotes
- Moderation/safety
- Billing verification
- Admin/ops/anti-cheat

## Deployment strategy
Start as a modular monolith plus Redis realtime. Each module owns its tables and has explicit service APIs. High-load modules (match/realtime, chat, economy) can be extracted into separate processes later without changing client contracts.

## Anti-cheat baseline
- server-issued match id, seed and signed session token
- monotonic action sequence numbers
- per-game action validation and maximum humanly plausible rate
- server final score calculation for deterministic games
- device/account risk score; no permanent device fingerprint used as identity
- idempotent reward settlement

## Wallet
Never accept `newBalance` from client. All mutations are ledger entries. Google Play purchases are acknowledged only after backend verification and SKU mapping. Purchased virtual currency remains inside this game economy and has no cash-out.
