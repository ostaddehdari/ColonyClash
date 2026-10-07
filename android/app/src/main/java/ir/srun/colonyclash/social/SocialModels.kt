package ir.srun.colonyclash.social

enum class RelationshipType {
    STRANGER,
    RIVAL,
    FRIEND_REQUEST_SENT,
    FRIEND_REQUEST_RECEIVED,
    FRIEND
}

enum class ColonyRelation {
    NONE,
    SAME_COLONY,
    ALLY_COLONY,
    ENEMY_COLONY
}

enum class SquadRelation {
    NONE,
    SAME_SQUAD
}

data class SocialEdgeState(
    val relationship: RelationshipType = RelationshipType.STRANGER,
    val colonyRelation: ColonyRelation = ColonyRelation.NONE,
    val squadRelation: SquadRelation = SquadRelation.NONE,
    val blockedByMe: Boolean = false,
    val mutedByMe: Boolean = false,
    val reportedByMe: Boolean = false,
    val battles: Int = 0,
    val myWins: Int = 0,
    val theirWins: Int = 0,
    val rivalryPoints: Int = 0
) {
    fun afterBattle(result: Int): SocialEdgeState {
        val nextRelationship = when (relationship) {
            RelationshipType.STRANGER -> RelationshipType.RIVAL
            else -> relationship
        }
        return copy(
            relationship = nextRelationship,
            battles = battles + 1,
            myWins = myWins + if (result > 0) 1 else 0,
            theirWins = theirWins + if (result < 0) 1 else 0,
            rivalryPoints = rivalryPoints + if (result == 0) 1 else 3
        )
    }
}

data class SocialPlayer(
    val id: String,
    val name: String,
    val avatar: String,
    val rating: Int,
    val level: Int,
    val online: Boolean,
    val isBot: Boolean = false,
    val colonyName: String? = null
)

object DemoPlayers {
    val nova = SocialPlayer(
        id = "bot_nova",
        name = "NOVA",
        avatar = "🤖",
        rating = 1180,
        level = 18,
        online = true,
        isBot = true
    )

    val all = listOf(
        SocialPlayer("shadow88", "Shadow88", "🥷", 1358, 24, true, colonyName = "Night Core"),
        SocialPlayer("minafox", "MinaFox", "🦊", 1274, 22, true, colonyName = "Blue Fox"),
        SocialPlayer("rex21", "Rex_21", "🦁", 1212, 19, false, colonyName = "Fire Crown"),
        SocialPlayer("sara", "Sara", "🧙", 1196, 20, true, colonyName = "Blue Citadel"),
        SocialPlayer("ali", "Ali", "🛡️", 1168, 18, false, colonyName = "Blue Citadel")
    )
}

object DemoSocialEdges {
    val initial: Map<String, SocialEdgeState> = mapOf(
        "shadow88" to SocialEdgeState(
            relationship = RelationshipType.RIVAL,
            colonyRelation = ColonyRelation.ENEMY_COLONY,
            battles = 5, myWins = 2, theirWins = 3, rivalryPoints = 15
        ),
        "minafox" to SocialEdgeState(
            relationship = RelationshipType.FRIEND,
            colonyRelation = ColonyRelation.NONE,
            battles = 8, myWins = 5, theirWins = 3, rivalryPoints = 24
        ),
        "rex21" to SocialEdgeState(
            relationship = RelationshipType.FRIEND_REQUEST_RECEIVED,
            colonyRelation = ColonyRelation.ALLY_COLONY
        ),
        "sara" to SocialEdgeState(
            relationship = RelationshipType.FRIEND,
            colonyRelation = ColonyRelation.SAME_COLONY,
            squadRelation = SquadRelation.SAME_SQUAD
        ),
        "ali" to SocialEdgeState(
            relationship = RelationshipType.FRIEND_REQUEST_SENT,
            colonyRelation = ColonyRelation.SAME_COLONY
        ),
        "bot_nova" to SocialEdgeState(
            relationship = RelationshipType.RIVAL,
            battles = 1, rivalryPoints = 3
        )
    )
}
