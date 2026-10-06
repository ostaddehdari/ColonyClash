# Stage 03 API

- `GET /wallet`
- `GET /wallet/ledger?limit=50`
- `GET /shop?market=play_global`
- `POST /shop/buy` `{sku, quantity, targetId?}`
- `GET /inventory`
- `POST /inventory/use` `{sku, quantity, contextId}`
- `POST /gifts/send` `{receiverUserId, sku, message}`
- `GET /gifts/inbox`
- `POST /gifts/:id/open`
- `GET /billing/products?market=play_global`
- `POST /billing/receipts` stores a receipt for later provider verification; it never grants currency in Stage 03.
