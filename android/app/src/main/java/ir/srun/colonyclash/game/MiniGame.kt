package ir.srun.colonyclash.game

interface MiniGame<S, A> {
    val code: String
    val maxActions: Int
    fun initialState(): S
    fun apply(state: S, action: A): S
    fun score(state: S): Pair<Int, Int>
    fun isFinished(state: S): Boolean
}
