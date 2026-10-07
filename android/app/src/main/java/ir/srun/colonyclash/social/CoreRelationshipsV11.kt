package ir.srun.colonyclash.social

enum class PlayerRelationV11 {
    STRANGER,
    RIVAL,
    FAMILIAR_RIVAL,
    FRIEND_REQUEST_SENT,
    FRIEND_REQUEST_RECEIVED,
    FRIEND
}

enum class ColonyRelationV11 {
    NEUTRAL,
    SAME_COLONY,
    RIVAL_COLONY
}

data class RivalHistoryV11(
    val opponentId: String,
    val displayName: String,
    val battles: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val relation: PlayerRelationV11 = PlayerRelationV11.STRANGER,
    val colonyRelation: ColonyRelationV11 = ColonyRelationV11.NEUTRAL,
    val blocked: Boolean = false,
    val muted: Boolean = false,
    val reported: Boolean = false
) {
    fun afterBattle(result: Int): RivalHistoryV11 {
        val newBattles = battles + 1
        val nextRelation = when {
            relation == PlayerRelationV11.STRANGER -> PlayerRelationV11.RIVAL
            relation == PlayerRelationV11.RIVAL && newBattles >= 3 ->
                PlayerRelationV11.FAMILIAR_RIVAL
            else -> relation
        }
        return copy(
            battles = newBattles,
            wins = wins + if (result > 0) 1 else 0,
            losses = losses + if (result < 0) 1 else 0,
            relation = nextRelation
        )
    }
}
