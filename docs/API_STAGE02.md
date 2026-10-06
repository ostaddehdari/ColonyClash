# Stage 02 API

All authenticated endpoints require `Authorization: Bearer <JWT>` unless marked public.

## Profile
- `GET /v1/profile/me`
- `PATCH /v1/profile/me`
- `POST /v1/profile/referral-code`
- `GET /v1/profile/u/:username` — public player card

## Colony
- `POST /v1/colonies`
- `GET /v1/colonies/mine`
- `GET /v1/colonies/:id` — public summary
- `GET /v1/colonies/:id/members` — public member list
- `POST /v1/colonies/:id/join` — public colonies only
- `POST /v1/colonies/:id/leave`
- `PATCH /v1/colonies/:id/members/:userId/role`
- `DELETE /v1/colonies/:id/members/:userId`
- `POST /v1/colonies/:id/invites`
- `POST /v1/colonies/invites/:token/accept`

## Squad
- `POST /v1/social/squads`
- `GET /v1/social/squads/mine`
- `GET /v1/social/squads/:id/members`
- `POST /v1/social/squads/:id/invites`
- `POST /v1/social/squads/invites/:token/accept`

## Rivalry
- `POST /v1/social/rivalries`
- `GET /v1/social/rivalries/mine`

## Challenge
- `POST /v1/social/challenges`
- `GET /v1/social/challenges/incoming`
- `GET /v1/social/challenges/code/:code` — public challenge card
- `POST /v1/social/challenges/code/:code/accept`
- `POST /v1/social/challenges/:id/cancel`
- `POST /v1/social/challenges/:id/shared`

### Create challenge example
```json
{
  "targetType": "user",
  "targetId": "UUID",
  "gameCode": "flick",
  "mode": "live",
  "message": "If you can beat me, prove it.",
  "stakePoints": 100
}
```

The server returns a `public_code`, a web share URL and an app deep-link. The server validates eligibility again when another user accepts the challenge.
