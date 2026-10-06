# Stage 04 API examples

## Create a direct match
`POST /games/matches`
```json
{"gameCode":"color_war_10","mode":"live","opponentUserId":"<uuid>"}
```

## Create an open match
`POST /games/matches`
```json
{"gameCode":"color_war_10","mode":"async"}
```
Then the second player calls `POST /games/matches/:id/join`.

## Submit action
`POST /games/matches/:id/actions`
```json
{"clientActionId":"unique-on-device","type":"place","row":1,"col":4}
```

Repeating the same `clientActionId` for the same user/match returns current state without applying the move twice.
