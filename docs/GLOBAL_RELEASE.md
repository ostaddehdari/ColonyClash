# Global release architecture — v0.2.0

## One codebase, three market builds
- `playRelease`: Google Play build; digital goods use Google Play Billing only.
- `iranRelease`: direct/Iran-store build; prices can be displayed in toman (IRT) and routed through an approved local gateway adapter.
- `chinaRelease`: China-market build; same game/API core with store/payment adapter isolated from the Play build.

The three builds share the same API contracts and game data. Payment receipts are validated server-side and settled into the immutable wallet ledger.

## Languages
Built-in app resources: English (`en`), Persian (`fa`), French (`fr`), Arabic (`ar`), Simplified Chinese (`zh-CN`). Persian and Arabic use Android RTL automatically.

## Deployment
Production backend is containerized and can run on a US Linux VPS or any compatible Linux/container host. PostgreSQL and Redis are private; only Nginx/API ingress is public. Horizontal scaling later adds multiple API nodes behind a load balancer and a shared Redis adapter for realtime rooms.

## Privacy
No broad `READ_CONTACTS` permission is declared. The app requests an explicit user-picked contact and creates an invite only from that selected record.
