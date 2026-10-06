# Stage 05 API

All endpoints below require Bearer JWT unless noted otherwise.

## Quick Match
### POST `/api/v1/matchmaking/quick`
```json
{"gameCode":"color_war_10","mode":"live","regionCode":"global"}
```
Returns either a queued ticket or an immediately matched game.

### GET `/api/v1/matchmaking/tickets/:id`
Poll queue state. If matched, response includes the canonical match view.

### DELETE `/api/v1/matchmaking/tickets/:id`
Cancel a queued ticket.

## Presence
### POST `/api/v1/matchmaking/presence/heartbeat`
Refresh online TTL.

### GET `/api/v1/matchmaking/presence/friends`
Return accepted friends plus `online` and `last_seen_at`.

## Reconnect
### GET `/api/v1/matchmaking/reconnect`
Return active matches owned by the caller.

## Direct friend invite
### POST `/api/v1/matchmaking/invites`
```json
{"invitedUserId":"optional-uuid","gameCode":"color_war_10","mode":"live","message":"اگه راست می‌گی بیا بازی"}
```
Returns `url` and `deepLink` (`colonyclash://match/CODE`).

### GET `/api/v1/matchmaking/invites/:code`
Preview invite.

### POST `/api/v1/matchmaking/invites/:code/accept`
Accept and create/return the canonical match.

## Existing Challenge → Match
### POST `/api/v1/matchmaking/challenges/:code/accept-play`
Claims a Stage 02 public challenge and creates its match. Repeated accepts return the existing `match_id` when appropriate.

## Rating
### GET `/api/v1/matchmaking/rating`
Returns current skill rating and core stats.

## Existing game endpoints retained
- `POST /api/v1/games/matches/:id/actions`
- `POST /api/v1/games/matches/:id/rematch`
- `GET /api/v1/games/matches/:id`
