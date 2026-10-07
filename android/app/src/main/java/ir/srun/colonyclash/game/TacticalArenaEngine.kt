package ir.srun.colonyclash.game

enum class ArenaZone { LEFT, CENTER, RIGHT }
enum class ArenaTactic { PUSH, HOLD, TRICK }
enum class BotPersona { NOVA, RUSH, MIRA, ZERO, VEX }

data class ArenaAction(val zone: ArenaZone, val tactic: ArenaTactic)

data class TacticalArenaState(
    val seed: Int,
    val round: Int = 0,
    val maxRounds: Int = 7,
    val playerInfluence: List<Int> = listOf(0, 0, 0),
    val botInfluence: List<Int> = listOf(0, 0, 0),
    val playerEnergy: Int = 8,
    val botEnergy: Int = 8,
    val playerHistory: List<ArenaAction> = emptyList(),
    val botHistory: List<ArenaAction> = emptyList(),
    val lastPlayerAction: ArenaAction? = null,
    val lastBotAction: ArenaAction? = null,
    val lastSummary: String = ""
)

data class TacticalOutcome(
    val result: Int,
    val playerZones: Int,
    val botZones: Int,
    val playerTotal: Int,
    val botTotal: Int
)

object TacticalArenaEngine {
    fun cost(tactic: ArenaTactic): Int = when (tactic) {
        ArenaTactic.PUSH -> 2
        ArenaTactic.HOLD -> 1
        ArenaTactic.TRICK -> 1
    }

    fun legalActions(energy: Int): List<ArenaAction> =
        ArenaZone.entries.flatMap { zone ->
            ArenaTactic.entries
                .filter { cost(it) <= energy }
                .map { ArenaAction(zone, it) }
        }

    fun playRound(
        state: TacticalArenaState,
        playerAction: ArenaAction,
        persona: BotPersona,
        botSkill: Double
    ): TacticalArenaState {
        if (isFinished(state)) return state
        if (cost(playerAction.tactic) > state.playerEnergy) return state

        val botAction = chooseBotAction(state, persona, botSkill)
        val p = state.playerInfluence.toMutableList()
        val b = state.botInfluence.toMutableList()
        val playerZone = playerAction.zone.ordinal
        val botZone = botAction.zone.ordinal

        val summary = if (playerZone == botZone) {
            when {
                playerAction.tactic == botAction.tactic -> {
                    p[playerZone] += 1
                    b[botZone] += 1
                    "CLASH"
                }
                beats(playerAction.tactic, botAction.tactic) -> {
                    p[playerZone] += 3
                    if (playerAction.tactic == ArenaTactic.TRICK) {
                        b[botZone] = (b[botZone] - 1).coerceAtLeast(0)
                    }
                    "PLAYER_EDGE"
                }
                else -> {
                    b[botZone] += 3
                    if (botAction.tactic == ArenaTactic.TRICK) {
                        p[playerZone] = (p[playerZone] - 1).coerceAtLeast(0)
                    }
                    "BOT_EDGE"
                }
            }
        } else {
            p[playerZone] += openLaneGain(playerAction.tactic)
            b[botZone] += openLaneGain(botAction.tactic)
            "SPLIT"
        }

        return state.copy(
            round = state.round + 1,
            playerInfluence = p,
            botInfluence = b,
            playerEnergy = (state.playerEnergy - cost(playerAction.tactic) + 1).coerceIn(0, 8),
            botEnergy = (state.botEnergy - cost(botAction.tactic) + 1).coerceIn(0, 8),
            playerHistory = (state.playerHistory + playerAction).takeLast(6),
            botHistory = (state.botHistory + botAction).takeLast(6),
            lastPlayerAction = playerAction,
            lastBotAction = botAction,
            lastSummary = summary
        )
    }

    fun isFinished(state: TacticalArenaState): Boolean = state.round >= state.maxRounds

    fun outcome(state: TacticalArenaState): TacticalOutcome {
        val pZones = state.playerInfluence.zip(state.botInfluence).count { (p, b) -> p > b }
        val bZones = state.playerInfluence.zip(state.botInfluence).count { (p, b) -> b > p }
        val pTotal = state.playerInfluence.sum()
        val bTotal = state.botInfluence.sum()

        val result = when {
            pZones > bZones -> 1
            bZones > pZones -> -1
            pTotal > bTotal -> 1
            bTotal > pTotal -> -1
            else -> 0
        }

        return TacticalOutcome(result, pZones, bZones, pTotal, bTotal)
    }

    private fun openLaneGain(tactic: ArenaTactic): Int = when (tactic) {
        ArenaTactic.PUSH -> 2
        ArenaTactic.HOLD -> 1
        ArenaTactic.TRICK -> 1
    }

    private fun beats(a: ArenaTactic, b: ArenaTactic): Boolean =
        (a == ArenaTactic.PUSH && b == ArenaTactic.TRICK) ||
        (a == ArenaTactic.TRICK && b == ArenaTactic.HOLD) ||
        (a == ArenaTactic.HOLD && b == ArenaTactic.PUSH)

    private fun chooseBotAction(
        state: TacticalArenaState,
        persona: BotPersona,
        skillInput: Double
    ): ArenaAction {
        val legal = legalActions(state.botEnergy)
        if (legal.isEmpty()) return ArenaAction(ArenaZone.CENTER, ArenaTactic.HOLD)

        val skill = skillInput.coerceIn(0.50, 0.86)
        val predictedZone = predictPlayerZone(state)
        val predictedTactic = predictPlayerTactic(state)

        return legal.mapIndexed { index, action ->
            val z = action.zone.ordinal
            val deficit = state.playerInfluence[z] - state.botInfluence[z]
            val zoneUrgency = deficit.coerceIn(-4, 6) * 0.40
            val centerValue = if (action.zone == ArenaZone.CENTER) 0.35 else 0.0
            val counterBonus =
                if (action.zone == predictedZone && beats(action.tactic, predictedTactic)) {
                    1.2 * skill
                } else 0.0

            val personaBias = when (persona) {
                BotPersona.NOVA -> 0.0
                BotPersona.RUSH -> if (action.tactic == ArenaTactic.PUSH) 0.55 else 0.0
                BotPersona.MIRA -> if (action.tactic == ArenaTactic.HOLD) 0.55 else 0.0
                BotPersona.ZERO -> if (action.tactic == ArenaTactic.TRICK) 0.60 else 0.0
                BotPersona.VEX -> if (cost(action.tactic) == 1) 0.35 else 0.0
            }

            val energyPenalty =
                if (state.botEnergy <= 2 && action.tactic == ArenaTactic.PUSH) -0.75 else 0.0

            val rawNoise =
                (((state.seed * 31 + state.round * 17 + index * 13) % 101) - 50) / 50.0
            val noise = rawNoise * (1.05 - skill)

            action to (
                zoneUrgency +
                centerValue +
                counterBonus +
                personaBias +
                energyPenalty +
                noise
            )
        }.maxByOrNull { it.second }!!.first
    }

    private fun predictPlayerZone(state: TacticalArenaState): ArenaZone {
        val recent = state.playerHistory.takeLast(3)
        if (recent.isEmpty()) return ArenaZone.CENTER
        return ArenaZone.entries.maxByOrNull { zone ->
            recent.count { it.zone == zone }
        } ?: ArenaZone.CENTER
    }

    private fun predictPlayerTactic(state: TacticalArenaState): ArenaTactic {
        val recent = state.playerHistory.takeLast(3)
        if (recent.isEmpty()) return ArenaTactic.PUSH
        return ArenaTactic.entries.maxByOrNull { tactic ->
            recent.count { it.tactic == tactic }
        } ?: ArenaTactic.PUSH
    }
}
