package ir.srun.colonyclash.game

object ColorWarBot {
    fun choose(state: ColorWarState, engine: ColorWar10): ColorWarAction? {
        if (state.turn != 2 || engine.isFinished(state)) return null

        data class Candidate(
            val action: ColorWarAction,
            val advantage: Int,
            val capture: Int,
            val center: Int
        )

        val candidates = mutableListOf<Candidate>()

        for (r in 0..4) {
            for (c in 0..4) {
                if (state.board[r][c] != 0) continue
                val action = ColorWarAction(r, c)
                val next = runCatching { engine.apply(state, action) }.getOrNull() ?: continue
                val score = engine.score(next)
                candidates += Candidate(
                    action = action,
                    advantage = score.second - score.first,
                    capture = next.lastCaptured,
                    center = -(kotlin.math.abs(r - 2) + kotlin.math.abs(c - 2))
                )
            }
        }

        return candidates.maxWithOrNull(
            compareBy<Candidate> { it.advantage }
                .thenBy { it.capture }
                .thenBy { it.center }
        )?.action
    }
}
