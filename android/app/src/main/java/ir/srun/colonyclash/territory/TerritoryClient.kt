package ir.srun.colonyclash.territory

import ir.srun.colonyclash.network.ApiClient
import org.json.JSONObject

class TerritoryClient(private val api:ApiClient) {
    fun activeSeason()=api.get("seasons/active")
    fun map()=api.get("territories/map")
    fun leaderboard(seasonId:String)=api.get("seasons/$seasonId/leaderboard")
    fun history()=api.get("seasons/history")
    fun myBattles()=api.get("territories/battles/mine")
    fun sync()=api.post("territories/sync")
    fun claim(code:String,colonyId:String)=api.post("territories/${code.uppercase()}/claim",JSONObject().put("colonyId",colonyId))
    fun challenge(code:String,attackerColonyId:String,mode:String="live",roundCount:Int=5)=
        api.post("territories/${code.uppercase()}/challenge",JSONObject().put("attackerColonyId",attackerColonyId).put("mode",mode).put("roundCount",roundCount))
    fun territoryHistory(code:String)=api.get("territories/${code.uppercase()}/history")
}
