package ir.srun.colonyclash.matchmaking

import android.net.Uri

sealed interface DeepLinkRoute {
    data class MatchInvite(val code:String):DeepLinkRoute
    data class Challenge(val code:String):DeepLinkRoute
    data class WarInvite(val code:String):DeepLinkRoute
    data class Territory(val code:String):DeepLinkRoute
    data class ColonyInvite(val token:String):DeepLinkRoute
    data class SquadInvite(val token:String):DeepLinkRoute

    companion object {
        fun parse(uri:Uri?):DeepLinkRoute? {
            if(uri==null || uri.scheme!="colonyclash") return null
            val parts=uri.pathSegments
            return when(uri.host){
                "match" -> parts.firstOrNull()?.let(::MatchInvite)
                "challenge" -> parts.firstOrNull()?.let(::Challenge)
                "war" -> parts.firstOrNull()?.let(::WarInvite)
                "territory" -> parts.firstOrNull()?.let(::Territory)
                "join" -> when(parts.firstOrNull()){
                    "colony" -> parts.getOrNull(1)?.let(::ColonyInvite)
                    "squad" -> parts.getOrNull(1)?.let(::SquadInvite)
                    else -> null
                }
                else -> null
            }
        }
    }
}
