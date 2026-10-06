package ir.srun.colonyclash.matchmaking

import ir.srun.colonyclash.network.ApiClient
import org.json.JSONObject

class MatchmakingClient(private val api:ApiClient) {
    fun quick(gameCode:String="color_war_10", mode:String="live", region:String="global") =
        api.post("matchmaking/quick", JSONObject().put("gameCode",gameCode).put("mode",mode).put("regionCode",region))

    fun ticket(id:String)=api.get("matchmaking/tickets/$id")
    fun reconnect()=api.get("matchmaking/reconnect")
    fun heartbeat()=api.post("matchmaking/presence/heartbeat")
    fun friendsPresence()=api.get("matchmaking/presence/friends")
    fun acceptInvite(code:String)=api.post("matchmaking/invites/${code.uppercase()}/accept")
    fun acceptChallenge(code:String)=api.post("matchmaking/challenges/${code.uppercase()}/accept-play")
}
