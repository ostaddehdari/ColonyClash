package ir.srun.colonyclash.game

data class ColorWarState(
    val board: List<List<Int>>,
    val turn: Int = 1,
    val actions: Int = 0,
    val lastCaptured: Int = 0
)
data class ColorWarAction(val row: Int, val col: Int)

class ColorWar10 : MiniGame<ColorWarState, ColorWarAction> {
    override val code = "color_war_10"
    override val maxActions = 10
    override fun initialState(): ColorWarState {
        val b = MutableList(5) { MutableList(5) { 0 } }
        b[0][0] = 1; b[4][4] = 2
        return ColorWarState(b.map { it.toList() })
    }
    override fun apply(state: ColorWarState, action: ColorWarAction): ColorWarState {
        require(action.row in 0..4 && action.col in 0..4)
        require(state.actions < maxActions)
        require(state.board[action.row][action.col] == 0)
        val mine = state.turn
        val enemy = if (mine == 1) 2 else 1
        val b = state.board.map { it.toMutableList() }.toMutableList()
        b[action.row][action.col] = mine
        val dirs = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        var captured = 0
        dirs.forEach { (dr, dc) ->
            val r = action.row + dr; val c = action.col + dc
            if (r in 0..4 && c in 0..4 && b[r][c] == enemy) {
                val friendly = dirs.count { (er, ec) ->
                    val rr = r + er; val cc = c + ec
                    rr in 0..4 && cc in 0..4 && b[rr][cc] == mine
                }
                if (friendly >= 2) { b[r][c] = mine; captured++ }
            }
        }
        return ColorWarState(b.map { it.toList() }, if (mine == 1) 2 else 1, state.actions + 1, captured)
    }
    override fun score(state: ColorWarState): Pair<Int, Int> =
        state.board.flatten().count { it == 1 } to state.board.flatten().count { it == 2 }
    override fun isFinished(state: ColorWarState) = state.actions >= maxActions || state.board.flatten().none { it == 0 }
}
