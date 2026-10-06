package ir.srun.colonyclash.war

import ir.srun.colonyclash.network.ApiClient
import org.json.JSONArray
import org.json.JSONObject

class ColonyWarClient(private val api:ApiClient) {
    fun mine()=api.get("wars/mine")
    fun byCode(code:String)=api.get("wars/code/${code.uppercase()}")
    fun get(id:String)=api.get("wars/$id")
    fun declare(challengerColonyId:String,defenderColonyId:String,mode:String="live",roundCount:Int=5)=
        api.post("wars",JSONObject().put("challengerColonyId",challengerColonyId).put("defenderColonyId",defenderColonyId).put("mode",mode).put("roundCount",roundCount))
    fun accept(id:String)=api.post("wars/$id/accept")
    fun setRoster(id:String,colonyId:String,userIds:List<String>)=
        api.post("wars/$id/roster",JSONObject().put("colonyId",colonyId).put("userIds",JSONArray(userIds)))
    fun start(id:String)=api.post("wars/$id/start")
    fun sync(id:String)=api.post("wars/$id/sync")
    fun spectate(id:String)=api.post("wars/$id/spectate")
    fun rematch(id:String)=api.post("wars/$id/rematch")
}
