package ir.srun.colonyclash.game

enum class RushGateType {
    ADD,
    MULTIPLY,
    SHIELD
}

data class RushGate(
    val label: String,
    val type: RushGateType,
    val value: Int
)

data class RushStage(
    val left: RushGate,
    val right: RushGate
)

data class RushBattleState(
    val seed: Int,
    val stage: Int = 0,
    val playerUnits: Int = 12,
    val botUnits: Int = 12,
    val playerShield: Int = 0,
    val botShield: Int = 0,
    val lastPlayerGate: String = "",
    val lastBotGate: String = ""
)

data class RushOutcome(
    val result: Int,
    val playerPower: Int,
    val botPower: Int
)

object ColonyRushEngine {
    private val scenarioA = listOf(
        RushStage(
            RushGate("+8", RushGateType.ADD, 8),
            RushGate("×2", RushGateType.MULTIPLY, 2)
        ),
        RushStage(
            RushGate("🛡 +6", RushGateType.SHIELD, 6),
            RushGate("+14", RushGateType.ADD, 14)
        ),
        RushStage(
            RushGate("×2", RushGateType.MULTIPLY, 2),
            RushGate("+18", RushGateType.ADD, 18)
        ),
        RushStage(
            RushGate("🛡 +12", RushGateType.SHIELD, 12),
            RushGate("×3", RushGateType.MULTIPLY, 3)
        ),
        RushStage(
            RushGate("+24", RushGateType.ADD, 24),
            RushGate("×2", RushGateType.MULTIPLY, 2)
        )
    )

    private val scenarioB = listOf(
        RushStage(
            RushGate("+10", RushGateType.ADD, 10),
            RushGate("🛡 +5", RushGateType.SHIELD, 5)
        ),
        RushStage(
            RushGate("×2", RushGateType.MULTIPLY, 2),
            RushGate("+15", RushGateType.ADD, 15)
        ),
        RushStage(
            RushGate("+20", RushGateType.ADD, 20),
            RushGate("🛡 +10", RushGateType.SHIELD, 10)
        ),
        RushStage(
            RushGate("×2", RushGateType.MULTIPLY, 2),
            RushGate("+28", RushGateType.ADD, 28)
        ),
        RushStage(
            RushGate("🛡 +16", RushGateType.SHIELD, 16),
            RushGate("×2", RushGateType.MULTIPLY, 2)
        )
    )

    private val scenarioC = listOf(
        RushStage(
            RushGate("🛡 +6", RushGateType.SHIELD, 6),
            RushGate("+9", RushGateType.ADD, 9)
        ),
        RushStage(
            RushGate("+16", RushGateType.ADD, 16),
            RushGate("×2", RushGateType.MULTIPLY, 2)
        ),
        RushStage(
            RushGate("×2", RushGateType.MULTIPLY, 2),
            RushGate("🛡 +12", RushGateType.SHIELD, 12)
        ),
        RushStage(
            RushGate("+22", RushGateType.ADD, 22),
            RushGate("×2", RushGateType.MULTIPLY, 2)
        ),
        RushStage(
            RushGate("+30", RushGateType.ADD, 30),
            RushGate("×2", RushGateType.MULTIPLY, 2)
        )
    )

    fun stages(seed: Int): List<RushStage> = when (kotlin.math.abs(seed) % 3) {
        0 -> scenarioA
        1 -> scenarioB
        else -> scenarioC
    }

    fun currentStage(state: RushBattleState): RushStage? =
        stages(state.seed).getOrNull(state.stage)

    fun isFinished(state: RushBattleState): Boolean =
        state.stage >= stages(state.seed).size

    fun pick(state: RushBattleState, chooseLeft: Boolean): RushBattleState {
        val current = currentStage(state) ?: return state
        val playerGate = if (chooseLeft) current.left else current.right
        val botGate = chooseBotGate(state, current)

        val player = applyGate(state.playerUnits, state.playerShield, playerGate)
        val bot = applyGate(state.botUnits, state.botShield, botGate)

        return state.copy(
            stage = state.stage + 1,
            playerUnits = player.first,
            playerShield = player.second,
            botUnits = bot.first,
            botShield = bot.second,
            lastPlayerGate = playerGate.label,
            lastBotGate = botGate.label
        )
    }

    fun outcome(state: RushBattleState): RushOutcome {
        val playerPower = state.playerUnits + (state.playerShield / 2)
        val botPower = state.botUnits + (state.botShield / 2)
        return RushOutcome(
            result = playerPower.compareTo(botPower),
            playerPower = playerPower,
            botPower = botPower
        )
    }

    private fun chooseBotGate(state: RushBattleState, stage: RushStage): RushGate {
        val leftPower = gatePower(state.botUnits, state.botShield, stage.left)
        val rightPower = gatePower(state.botUnits, state.botShield, stage.right)

        // NOVA is intentionally beatable: roughly one of every three stages
        // it chooses the weaker option. This keeps the first-session loop fun.
        val chooseWeaker = (state.seed + state.stage) % 3 == 0
        return if (chooseWeaker) {
            if (leftPower <= rightPower) stage.left else stage.right
        } else {
            if (leftPower >= rightPower) stage.left else stage.right
        }
    }

    private fun gatePower(units: Int, shield: Int, gate: RushGate): Int {
        val applied = applyGate(units, shield, gate)
        return applied.first + (applied.second / 2)
    }

    private fun applyGate(units: Int, shield: Int, gate: RushGate): Pair<Int, Int> {
        return when (gate.type) {
            RushGateType.ADD -> (units + gate.value).coerceAtMost(999) to shield
            RushGateType.MULTIPLY -> (units * gate.value).coerceAtMost(999) to shield
            RushGateType.SHIELD -> units to (shield + gate.value).coerceAtMost(999)
        }
    }
}
