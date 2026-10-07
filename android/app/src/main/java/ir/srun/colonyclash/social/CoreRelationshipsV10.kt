package ir.srun.colonyclash.social

enum class PlayerRelationV10 {
    STRANGER,
    RIVAL,
    REPEATED_RIVAL,
    FRIEND_REQUEST_SENT,
    FRIEND_REQUEST_RECEIVED,
    FRIEND
}

enum class ColonyRelationV10 {
    NEUTRAL,
    SAME_COLONY,
    RIVAL_COLONY
}

data class RelationshipV10(
    val playerRelation: PlayerRelationV10 = PlayerRelationV10.STRANGER,
    val colonyRelation: ColonyRelationV10 = ColonyRelationV10.NEUTRAL,
    val battles: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val blocked: Boolean = false,
    val muted: Boolean = false,
    val reported: Boolean = false
) {
    fun afterBattle(result: Int): RelationshipV10 {
        val nextBattles = battles + 1
        val nextRelation = when {
            playerRelation == PlayerRelationV10.STRANGER ->
                PlayerRelationV10.RIVAL
            playerRelation == PlayerRelationV10.RIVAL && nextBattles >= 3 ->
                PlayerRelationV10.REPEATED_RIVAL
            else -> playerRelation
        }
        return copy(
            playerRelation = nextRelation,
            battles = nextBattles,
            wins = wins + if (result > 0) 1 else 0,
            losses = losses + if (result < 0) 1 else 0
        )
    }

    fun sendFriendRequest(): RelationshipV10 =
        if (playerRelation == PlayerRelationV10.RIVAL ||
            playerRelation == PlayerRelationV10.REPEATED_RIVAL
        ) copy(playerRelation = PlayerRelationV10.FRIEND_REQUEST_SENT)
        else this

    fun acceptFriendRequest(): RelationshipV10 =
        if (playerRelation == PlayerRelationV10.FRIEND_REQUEST_RECEIVED) {
            copy(playerRelation = PlayerRelationV10.FRIEND)
        } else this
}
