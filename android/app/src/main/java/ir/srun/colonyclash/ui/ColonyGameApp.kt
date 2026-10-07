package ir.srun.colonyclash.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.srun.colonyclash.R
import ir.srun.colonyclash.game.ArenaAction
import ir.srun.colonyclash.game.ArenaTactic
import ir.srun.colonyclash.game.ArenaZone
import ir.srun.colonyclash.game.BotPersona
import ir.srun.colonyclash.game.TacticalArenaEngine
import ir.srun.colonyclash.game.TacticalArenaState
import ir.srun.colonyclash.game.TacticalOutcome
import ir.srun.colonyclash.matchmaking.DeepLinkRoute
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.max

private val Night = Color(0xFF071522)
private val Night2 = Color(0xFF0D2438)
private val Steel = Color(0xFF173141)
private val Turquoise = Color(0xFF18B7B0)
private val Turquoise2 = Color(0xFF0B898A)
private val Amber = Color(0xFFFFB33B)
private val Coral = Color(0xFFE75D62)
private val Ivory = Color(0xFFF4F1E8)
private val Muted = Color(0xFF91A9B7)

private enum class V11Screen { HOME, MATCHMAKING, BATTLE, COLONY, DAILY, SOCIAL }

private data class PlayerV11(
    val name: String = "",
    val level: Int = 1,
    val xp: Int = 0,
    val rating: Int = 1000,
    val coins: Int = 0,
    val streak: Int = 0,
    val matches: Int = 0,
    val wins: Int = 0,
    val dailyWins: Int = 0,
    val dailyClaimed: Boolean = false,
    val dailyKey: String = LocalDate.now().toString(),
    val colonyName: String = "",
    val colonyType: String = ""
)

private data class OpponentV11(
    val id: String,
    val name: String,
    val persona: BotPersona,
    val rating: Int,
    val ai: Boolean = true
)

private data class RivalLocal(
    val id: String,
    val name: String,
    val battles: Int,
    val wins: Int,
    val losses: Int
)

private class StoreV11(context: Context) {
    private val p = context.getSharedPreferences("colony_v11", Context.MODE_PRIVATE)

    fun load(): PlayerV11 {
        val today = LocalDate.now().toString()
        val sameDay = p.getString("daily_key", today) == today
        return PlayerV11(
            name = p.getString("name", "") ?: "",
            level = p.getInt("level", 1),
            xp = p.getInt("xp", 0),
            rating = p.getInt("rating", 1000),
            coins = p.getInt("coins", 0),
            streak = p.getInt("streak", 0),
            matches = p.getInt("matches", 0),
            wins = p.getInt("wins", 0),
            dailyWins = if (sameDay) p.getInt("daily_wins", 0) else 0,
            dailyClaimed = if (sameDay) p.getBoolean("daily_claimed", false) else false,
            dailyKey = today,
            colonyName = p.getString("colony_name", "") ?: "",
            colonyType = p.getString("colony_type", "") ?: ""
        )
    }

    fun save(v: PlayerV11) {
        p.edit()
            .putString("name", v.name)
            .putInt("level", v.level)
            .putInt("xp", v.xp)
            .putInt("rating", v.rating)
            .putInt("coins", v.coins)
            .putInt("streak", v.streak)
            .putInt("matches", v.matches)
            .putInt("wins", v.wins)
            .putInt("daily_wins", v.dailyWins)
            .putBoolean("daily_claimed", v.dailyClaimed)
            .putString("daily_key", v.dailyKey)
            .putString("colony_name", v.colonyName)
            .putString("colony_type", v.colonyType)
            .apply()
    }
}

@Composable
fun ColonyGameApp(
    deepLinkRoute: DeepLinkRoute?,
    onDeepLinkConsumed: () -> Unit,
    onPickContact: (((String, String) -> Unit) -> Unit),
    onShare: (String) -> Unit,
    onSendSms: (String, String) -> Unit
) {
    val context = LocalContext.current
    val store = remember { StoreV11(context) }

    var player by remember { mutableStateOf(store.load()) }
    var booting by rememberSaveable { mutableStateOf(true) }
    var screen by rememberSaveable { mutableStateOf(V11Screen.HOME) }
    var battleId by rememberSaveable { mutableIntStateOf(player.matches + 1) }
    var opponent by remember { mutableStateOf(makeOpponent(player.rating, battleId)) }
    val rivals = remember { mutableStateListOf<RivalLocal>() }

    var invitePhone by rememberSaveable { mutableStateOf("") }
    var inviteName by rememberSaveable { mutableStateOf("") }
    val inviteText = stringResource(R.string.v11_invite_message)

    fun persist(next: PlayerV11) {
        player = next
        store.save(next)
    }

    fun recordRival(op: OpponentV11, result: Int) {
        val index = rivals.indexOfFirst { it.id == op.id }
        if (index < 0) {
            rivals.add(
                0,
                RivalLocal(
                    id = op.id,
                    name = op.name,
                    battles = 1,
                    wins = if (result > 0) 1 else 0,
                    losses = if (result < 0) 1 else 0
                )
            )
        } else {
            val r = rivals[index]
            rivals[index] = r.copy(
                battles = r.battles + 1,
                wins = r.wins + if (result > 0) 1 else 0,
                losses = r.losses + if (result < 0) 1 else 0
            )
        }
    }

    LaunchedEffect(Unit) {
        delay(340)
        booting = false
    }

    LaunchedEffect(deepLinkRoute) {
        when (deepLinkRoute) {
            is DeepLinkRoute.MatchInvite,
            is DeepLinkRoute.Challenge -> screen = V11Screen.MATCHMAKING
            is DeepLinkRoute.ColonyInvite,
            is DeepLinkRoute.WarInvite -> screen = V11Screen.COLONY
            else -> Unit
        }
        if (deepLinkRoute != null) onDeepLinkConsumed()
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Turquoise,
            secondary = Amber,
            tertiary = Coral,
            background = Night,
            surface = Night2,
            onPrimary = Night,
            onSecondary = Night,
            onBackground = Ivory,
            onSurface = Ivory
        )
    ) {
        when {
            booting -> SplashV11()
            player.name.isBlank() -> OnboardingV11 {
                persist(player.copy(name = it.trim().ifBlank { "Player" }))
            }
            else -> AppShell(
                player = player,
                screen = screen,
                onScreen = { screen = it }
            ) {
                when (screen) {
                    V11Screen.HOME -> HomeV11(
                        player,
                        onBattle = { screen = V11Screen.MATCHMAKING },
                        onDaily = { screen = V11Screen.DAILY },
                        onColony = { screen = V11Screen.COLONY }
                    )

                    V11Screen.MATCHMAKING -> MatchmakingV11(
                        player = player,
                        battleId = battleId,
                        onCancel = { screen = V11Screen.HOME },
                        onReady = {
                            opponent = it
                            screen = V11Screen.BATTLE
                        }
                    )

                    V11Screen.BATTLE -> BattleV11(
                        player = player,
                        opponent = opponent,
                        battleId = battleId,
                        onFinished = { outcome ->
                            val win = outcome.result > 0
                            val draw = outcome.result == 0
                            val ratingDelta = when {
                                win -> 24
                                draw -> 3
                                else -> -12
                            }
                            val xpGain = if (win) 18 else 8
                            val coinGain = if (win) 90 else 35
                            val rawXp = player.xp + xpGain

                            persist(
                                player.copy(
                                    level = player.level + rawXp / 100,
                                    xp = rawXp % 100,
                                    rating = (player.rating + ratingDelta).coerceAtLeast(0),
                                    coins = player.coins + coinGain,
                                    streak = if (win) player.streak + 1 else 0,
                                    matches = player.matches + 1,
                                    wins = player.wins + if (win) 1 else 0,
                                    dailyWins = (player.dailyWins + if (win) 1 else 0).coerceAtMost(3)
                                )
                            )
                            recordRival(opponent, outcome.result)
                        },
                        onRematch = {
                            battleId += 1
                            opponent = makeOpponent(player.rating, battleId)
                        },
                        onHome = { screen = V11Screen.HOME }
                    )

                    V11Screen.COLONY -> ColonyV11(
                        player = player,
                        phone = invitePhone,
                        contactName = inviteName,
                        onPhone = { invitePhone = it.take(24) },
                        onPick = {
                            onPickContact { name, phone ->
                                inviteName = name
                                invitePhone = phone
                            }
                        },
                        onSms = {
                            if (invitePhone.isNotBlank()) {
                                onSendSms(invitePhone, inviteText)
                            }
                        },
                        onShare = { onShare(inviteText) },
                        onSetupColony = { type, name ->
                            persist(player.copy(colonyType = type, colonyName = name.trim()))
                        },
                        onBattle = { screen = V11Screen.MATCHMAKING }
                    )

                    V11Screen.DAILY -> DailyV11(
                        player = player,
                        onBattle = { screen = V11Screen.MATCHMAKING },
                        onClaim = {
                            if (player.dailyWins >= 3 && !player.dailyClaimed) {
                                persist(
                                    player.copy(
                                        coins = player.coins + 220,
                                        dailyClaimed = true
                                    )
                                )
                            }
                        }
                    )

                    V11Screen.SOCIAL -> SocialV11(
                        rivals = rivals,
                        onBattle = { screen = V11Screen.MATCHMAKING }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppShell(
    player: PlayerV11,
    screen: V11Screen,
    onScreen: (V11Screen) -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Night, Color(0xFF0A2436), Color(0xFF102C3E))))
            .statusBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            if (screen !in setOf(V11Screen.BATTLE, V11Screen.MATCHMAKING)) {
                CompactHud(player)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            if (screen !in setOf(V11Screen.BATTLE, V11Screen.MATCHMAKING)) {
                BottomBarV11(
                    selected = screen,
                    onHome = { onScreen(V11Screen.HOME) },
                    onColony = { onScreen(V11Screen.COLONY) },
                    onBattle = { onScreen(V11Screen.MATCHMAKING) },
                    onSocial = { onScreen(V11Screen.SOCIAL) }
                )
            }
        }
    }
}

private fun makeOpponent(rating: Int, battleId: Int): OpponentV11 {
    val personas = listOf(BotPersona.NOVA, BotPersona.RUSH, BotPersona.MIRA, BotPersona.ZERO, BotPersona.VEX)
    val persona = personas[kotlin.math.abs(battleId) % personas.size]
    val name = persona.name
    val offset = ((battleId * 17) % 61) - 30
    return OpponentV11(
        id = "ai-${persona.name.lowercase()}",
        name = name,
        persona = persona,
        rating = (rating + offset).coerceAtLeast(700)
    )
}

private fun botSkill(playerRating: Int, botRating: Int): Double {
    val relative = (botRating - playerRating).coerceIn(-100, 100)
    return (0.66 + relative / 500.0).coerceIn(0.56, 0.80)
}

@Composable
private fun SplashV11() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF020B12), Night, Color(0xFF0A3040)))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(Modifier.size(132.dp))
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.v11_title), color = Ivory, fontSize = 27.sp, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.v11_tagline), color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun OnboardingV11(onDone: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Night, Color(0xFF0A3040))))
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        PremiumPanel {
            BrandMark(Modifier.size(92.dp))
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.v11_title), color = Ivory, fontWeight = FontWeight.Black, fontSize = 25.sp)
            Text(
                stringResource(R.string.v11_onboarding_desc),
                color = Muted,
                textAlign = TextAlign.Center,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(20) },
                label = { Text(stringResource(R.string.v11_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                text = stringResource(R.string.v11_enter),
                enabled = name.trim().length >= 2,
                onClick = { onDone(name) }
            )
        }
    }
}

@Composable
private fun CompactHud(player: PlayerV11) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(color = Steel, shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f)) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                InitialAvatar(player.name, 38)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(player.name, color = Ivory, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Text("${stringResource(R.string.v11_level)} ${player.level}", color = Amber, fontSize = 9.sp)
                    LinearProgressIndicator(
                        progress = { player.xp / 100f },
                        color = Turquoise,
                        trackColor = Color.White.copy(alpha = .08f),
                        modifier = Modifier.fillMaxWidth().height(5.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(7.dp))
        StatPill("R", player.rating.toString())
        Spacer(Modifier.width(5.dp))
        StatPill("C", player.coins.toString())
    }
}

@Composable
private fun StatPill(mark: String, value: String) {
    Surface(color = Steel, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(18.dp).clip(CircleShape).background(if (mark == "R") Turquoise else Amber),
                contentAlignment = Alignment.Center
            ) {
                Text(mark, color = Night, fontSize = 8.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(5.dp))
            Text(value, color = Ivory, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun HomeV11(
    player: PlayerV11,
    onBattle: () -> Unit,
    onDaily: () -> Unit,
    onColony: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(430.dp)
                    .shadow(16.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF0B334A), Color(0xFF14526A), Color(0xFF0D2636))))
                    .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(28.dp))
            ) {
                IsometricWorld(Modifier.fillMaxSize())

                EventChip(
                    title = stringResource(R.string.v11_daily),
                    value = "${player.dailyWins}/3",
                    modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
                    onClick = onDaily
                )
                EventChip(
                    title = stringResource(R.string.v11_rivalry),
                    value = if (player.colonyName.isBlank()) "--" else "-37",
                    modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
                    onClick = onColony
                )

                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.v11_home_hook),
                        color = Ivory,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(text = stringResource(R.string.v11_battle), onClick = onBattle)
                }
            }
        }

        item {
            Surface(color = Steel, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Turquoise2.copy(alpha = .35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        MiniBattleGlyph(Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.v11_next_match), color = Ivory, fontWeight = FontWeight.Black)
                        Text(
                            if (player.streak > 0) "${stringResource(R.string.v11_streak)} ${player.streak}"
                            else stringResource(R.string.v11_balanced_opponent),
                            color = Muted,
                            fontSize = 10.sp
                        )
                    }
                    TextButton(onClick = onBattle) {
                        Text(stringResource(R.string.v11_play), color = Turquoise)
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchmakingV11(
    player: PlayerV11,
    battleId: Int,
    onCancel: () -> Unit,
    onReady: (OpponentV11) -> Unit
) {
    val opponent = remember(battleId, player.rating) { makeOpponent(player.rating, battleId) }

    LaunchedEffect(battleId) {
        delay(850)
        onReady(opponent)
    }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Night, Color(0xFF0C3142)))).padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.v11_matchmaking), color = Ivory, fontWeight = FontWeight.Black, fontSize = 21.sp)
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                MatchBadge(player.name, player.rating, Turquoise, false)
                Text("VS", color = Amber, fontWeight = FontWeight.Black, fontSize = 26.sp)
                MatchBadge(opponent.name, opponent.rating, Coral, true)
            }
            Spacer(Modifier.height(22.dp))
            CircularProgressIndicator(color = Turquoise)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.v11_matchmaking_note),
                color = Muted,
                textAlign = TextAlign.Center,
                fontSize = 10.sp
            )
            Spacer(Modifier.height(18.dp))
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.v11_cancel)) }
        }
    }
}

@Composable
private fun MatchBadge(name: String, rating: Int, accent: Color, ai: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(accent.copy(alpha = .18f)).border(2.dp, accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (ai) AiGlyph(Modifier.size(42.dp), accent)
            else Text(name.take(2).uppercase(), color = Ivory, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
        Spacer(Modifier.height(7.dp))
        Text(if (ai) "$name • AI" else name, color = Ivory, fontWeight = FontWeight.Black, fontSize = 11.sp)
        Text("R $rating", color = Amber, fontSize = 9.sp)
    }
}

@Composable
private fun BattleV11(
    player: PlayerV11,
    opponent: OpponentV11,
    battleId: Int,
    onFinished: (TacticalOutcome) -> Unit,
    onRematch: () -> Unit,
    onHome: () -> Unit
) {
    var state by remember(battleId) {
        mutableStateOf(TacticalArenaState(seed = battleId * 37 + player.rating))
    }
    var selectedZone by remember(battleId) { mutableStateOf(ArenaZone.CENTER) }
    var finishedSent by remember(battleId) { mutableStateOf(false) }

    val finished = TacticalArenaEngine.isFinished(state)
    val outcome = if (finished) TacticalArenaEngine.outcome(state) else null

    LaunchedEffect(finished, battleId) {
        if (finished && !finishedSent && outcome != null) {
            finishedSent = true
            onFinished(outcome)
        }
    }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Night, Color(0xFF0B2B3D)))).statusBarsPadding()
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            BattleHeader(player, opponent, state)
            Spacer(Modifier.height(10.dp))

            if (!finished) {
                TacticalBoard(
                    state = state,
                    selectedZone = selectedZone,
                    onZone = { selectedZone = it },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    "${stringResource(R.string.v11_round)} ${state.round + 1}/${state.maxRounds}",
                    color = Amber,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(R.string.v11_choose_tactic),
                    color = Muted,
                    fontSize = 10.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TacticButton(
                        title = stringResource(R.string.v11_push),
                        subtitle = "2E",
                        accent = Coral,
                        enabled = state.playerEnergy >= 2,
                        modifier = Modifier.weight(1f)
                    ) {
                        state = TacticalArenaEngine.playRound(
                            state,
                            ArenaAction(selectedZone, ArenaTactic.PUSH),
                            opponent.persona,
                            botSkill(player.rating, opponent.rating)
                        )
                    }
                    TacticButton(
                        title = stringResource(R.string.v11_hold),
                        subtitle = "1E",
                        accent = Turquoise,
                        enabled = state.playerEnergy >= 1,
                        modifier = Modifier.weight(1f)
                    ) {
                        state = TacticalArenaEngine.playRound(
                            state,
                            ArenaAction(selectedZone, ArenaTactic.HOLD),
                            opponent.persona,
                            botSkill(player.rating, opponent.rating)
                        )
                    }
                    TacticButton(
                        title = stringResource(R.string.v11_trick),
                        subtitle = "1E",
                        accent = Amber,
                        enabled = state.playerEnergy >= 1,
                        modifier = Modifier.weight(1f)
                    ) {
                        state = TacticalArenaEngine.playRound(
                            state,
                            ArenaAction(selectedZone, ArenaTactic.TRICK),
                            opponent.persona,
                            botSkill(player.rating, opponent.rating)
                        )
                    }
                }
            } else {
                BattleResultV11(outcome!!, opponent, onRematch, onHome)
            }
        }
    }
}

@Composable
private fun BattleHeader(player: PlayerV11, opponent: OpponentV11, state: TacticalArenaState) {
    Surface(color = Color(0xF2162935), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallIdentity(player.name, player.rating, state.playerEnergy, Turquoise, false, Modifier.weight(1f))
            Text("VS", color = Amber, fontWeight = FontWeight.Black)
            SmallIdentity(opponent.name, opponent.rating, state.botEnergy, Coral, true, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SmallIdentity(
    name: String,
    rating: Int,
    energy: Int,
    accent: Color,
    ai: Boolean,
    modifier: Modifier
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(accent.copy(alpha = .20f)).border(1.dp, accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (ai) AiGlyph(Modifier.size(20.dp), accent)
            else Text(name.take(1).uppercase(), color = Ivory, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(7.dp))
        Column {
            Text(if (ai) "$name • AI" else name, color = Ivory, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            Text("R $rating  •  E $energy", color = Muted, fontSize = 8.sp)
        }
    }
}

@Composable
private fun TacticalBoard(
    state: TacticalArenaState,
    selectedZone: ArenaZone,
    onZone: (ArenaZone) -> Unit,
    modifier: Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF103B4B), Color(0xFF0C2837))))
            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(26.dp))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawLine(Color.White.copy(alpha = .08f), Offset(w*.15f,h*.50f), Offset(w*.85f,h*.50f), 3f)

            val centers = listOf(
                Offset(w * .24f, h * .50f),
                Offset(w * .50f, h * .40f),
                Offset(w * .76f, h * .50f)
            )

            centers.forEachIndexed { i, c ->
                val zone = ArenaZone.entries[i]
                val selected = zone == selectedZone
                val control = state.playerInfluence[i] - state.botInfluence[i]
                val base = when {
                    control > 0 -> Turquoise
                    control < 0 -> Coral
                    else -> Color(0xFF55717F)
                }

                val diamond = Path().apply {
                    moveTo(c.x, c.y - 62f)
                    lineTo(c.x + 72f, c.y)
                    lineTo(c.x, c.y + 62f)
                    lineTo(c.x - 72f, c.y)
                    close()
                }
                drawPath(diamond, color = base.copy(alpha = if (selected) .72f else .42f))
                drawPath(
                    diamond,
                    color = if (selected) Ivory.copy(alpha = .75f) else Color.White.copy(alpha = .15f),
                    style = Stroke(width = if (selected) 4f else 2f)
                )
                drawCircle(Turquoise, radius = 10f + state.playerInfluence[i].coerceAtMost(8) * 1.5f, center = Offset(c.x,c.y+20f))
                drawCircle(Coral, radius = 10f + state.botInfluence[i].coerceAtMost(8) * 1.5f, center = Offset(c.x,c.y-20f))
            }
        }

        Row(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ArenaZone.entries.forEachIndexed { index, zone ->
                val selected = zone == selectedZone
                Column(
                    Modifier.width(96.dp).clickable { onZone(zone) }.padding(vertical = 72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(zoneLabel(zone), color = if (selected) Ivory else Muted, fontWeight = if (selected) FontWeight.Black else FontWeight.Normal, fontSize = 9.sp)
                    Text("${state.playerInfluence[index]} : ${state.botInfluence[index]}", color = if (selected) Amber else Muted, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }
        }

        if (state.lastPlayerAction != null && state.lastBotAction != null) {
            Surface(
                color = Color(0xDD0A1B26),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
            ) {
                Text(
                    "${tacticLabel(state.lastPlayerAction.tactic)} • ${tacticLabel(state.lastBotAction.tactic)} — ${summaryLabel(state.lastSummary)}",
                    color = Ivory,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun zoneLabel(zone: ArenaZone): String = when (zone) {
    ArenaZone.LEFT -> stringResource(R.string.v11_zone_left)
    ArenaZone.CENTER -> stringResource(R.string.v11_zone_center)
    ArenaZone.RIGHT -> stringResource(R.string.v11_zone_right)
}

@Composable
private fun tacticLabel(tactic: ArenaTactic): String = when (tactic) {
    ArenaTactic.PUSH -> stringResource(R.string.v11_push)
    ArenaTactic.HOLD -> stringResource(R.string.v11_hold)
    ArenaTactic.TRICK -> stringResource(R.string.v11_trick)
}

@Composable
private fun summaryLabel(summary: String): String = when (summary) {
    "PLAYER_EDGE" -> stringResource(R.string.v11_you_read_it)
    "BOT_EDGE" -> stringResource(R.string.v11_rival_read_it)
    "CLASH" -> stringResource(R.string.v11_clash)
    else -> stringResource(R.string.v11_split)
}

@Composable
private fun TacticButton(
    title: String,
    subtitle: String,
    accent: Color,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(74.dp),
        color = if (enabled) accent.copy(alpha = .18f) else Color.White.copy(alpha = .04f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (enabled) accent.copy(alpha = .70f) else Color.White.copy(alpha = .06f))
    ) {
        Column(
            Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, color = if (enabled) Ivory else Muted.copy(alpha = .45f), fontWeight = FontWeight.Black, fontSize = 12.sp)
            Text(subtitle, color = if (enabled) accent else Muted.copy(alpha = .35f), fontSize = 9.sp)
        }
    }
}

@Composable
private fun BattleResultV11(
    outcome: TacticalOutcome,
    opponent: OpponentV11,
    onRematch: () -> Unit,
    onHome: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BrandMark(Modifier.size(86.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            when {
                outcome.result > 0 -> stringResource(R.string.v11_victory)
                outcome.result < 0 -> stringResource(R.string.v11_defeat)
                else -> stringResource(R.string.v11_draw)
            },
            color = Ivory,
            fontWeight = FontWeight.Black,
            fontSize = 28.sp
        )
        Text("${outcome.playerZones} — ${outcome.botZones} ${stringResource(R.string.v11_zones)}", color = Amber, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text("${stringResource(R.string.v11_against)} ${opponent.name} • AI", color = Muted, fontSize = 10.sp)
        Spacer(Modifier.height(18.dp))

        Surface(color = Steel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ResultMetric("R", if (outcome.result > 0) "+24" else "-12", Turquoise)
                ResultMetric("XP", if (outcome.result > 0) "+18" else "+8", Amber)
                ResultMetric("C", if (outcome.result > 0) "+90" else "+35", Coral)
            }
        }

        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = stringResource(R.string.v11_rematch), onClick = onRematch)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.v11_home))
        }
    }
}

@Composable
private fun ResultMetric(mark: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(mark, color = accent, fontWeight = FontWeight.Black, fontSize = 11.sp)
        Text(value, color = Ivory, fontWeight = FontWeight.Black, fontSize = 17.sp)
    }
}

@Composable
private fun ColonyV11(
    player: PlayerV11,
    phone: String,
    contactName: String,
    onPhone: (String) -> Unit,
    onPick: () -> Unit,
    onSms: () -> Unit,
    onShare: () -> Unit,
    onSetupColony: (String, String) -> Unit,
    onBattle: () -> Unit
) {
    if (player.colonyName.isBlank()) {
        ColonySetupV11(onSetupColony)
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(color = Steel, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ColonyGlyph(Modifier.size(78.dp), Turquoise)
                    Spacer(Modifier.height(10.dp))
                    Text(player.colonyName, color = Ivory, fontWeight = FontWeight.Black, fontSize = 21.sp)
                    Text(player.colonyType, color = Muted, fontSize = 10.sp)
                    Spacer(Modifier.height(14.dp))
                    RivalryMeterV11(146 + player.wins * 3, 183)
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(text = stringResource(R.string.v11_fight_for_colony), onClick = onBattle)
                }
            }
        }

        item {
            PremiumPanel {
                Text(stringResource(R.string.v11_invite_title), color = Ivory, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text(stringResource(R.string.v11_invite_desc), color = Muted, fontSize = 10.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = onPhone,
                    label = { Text(if (contactName.isBlank()) stringResource(R.string.v11_phone) else contactName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPick, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.v11_contact), fontSize = 10.sp)
                    }
                    Button(onClick = onSms, enabled = phone.isNotBlank(), modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.v11_sms), fontSize = 10.sp)
                    }
                }
                TextButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.v11_share))
                }
            }
        }
    }
}

@Composable
private fun ColonySetupV11(onSetup: (String, String) -> Unit) {
    var type by rememberSaveable { mutableStateOf("دانشگاه") }
    var name by rememberSaveable { mutableStateOf("") }
    val types = listOf("دانشگاه", "دانشکده", "خوابگاه", "کلاس", "گروه دوستان")

    Column(
        Modifier.fillMaxSize().padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ColonyGlyph(Modifier.size(88.dp), Turquoise)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.v11_colony_setup), color = Ivory, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text(
            stringResource(R.string.v11_colony_setup_desc),
            color = Muted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(types) { item ->
                Surface(
                    onClick = { type = item },
                    color = if (type == item) Turquoise.copy(alpha = .18f) else Steel,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, if (type == item) Turquoise else Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        item,
                        color = Ivory,
                        fontWeight = if (type == item) FontWeight.Black else FontWeight.Normal,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(32) },
            label = { Text(stringResource(R.string.v11_colony_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        PrimaryButton(
            text = stringResource(R.string.v11_create_colony),
            enabled = name.trim().length >= 2,
            onClick = { onSetup(type, name) }
        )
    }
}

@Composable
private fun RivalryMeterV11(ourScore: Int, rivalScore: Int) {
    val total = max(1, ourScore + rivalScore)
    val share = ourScore.toFloat() / total.toFloat()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(ourScore.toString(), color = Turquoise, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.v11_rivalry_24h), color = Muted, fontSize = 9.sp)
            Text(rivalScore.toString(), color = Coral, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(6.dp)).background(Coral.copy(alpha = .75f))
        ) {
            Box(Modifier.fillMaxWidth(share).fillMaxHeight().background(Turquoise))
        }
    }
}

@Composable
private fun DailyV11(player: PlayerV11, onBattle: () -> Unit, onClaim: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PremiumPanel {
            Text(stringResource(R.string.v11_daily), color = Ivory, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text(stringResource(R.string.v11_daily_desc), color = Muted, fontSize = 10.sp)
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { player.dailyWins / 3f },
                color = Turquoise,
                trackColor = Color.White.copy(alpha = .08f),
                modifier = Modifier.fillMaxWidth().height(10.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("${player.dailyWins}/3", color = Amber, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Spacer(Modifier.height(12.dp))
            when {
                player.dailyClaimed -> Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.v11_claimed))
                }
                player.dailyWins >= 3 -> PrimaryButton(
                    text = stringResource(R.string.v11_claim_reward),
                    onClick = onClaim
                )
                else -> PrimaryButton(
                    text = stringResource(R.string.v11_battle),
                    onClick = onBattle
                )
            }
        }
    }
}

@Composable
private fun SocialV11(rivals: List<RivalLocal>, onBattle: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(stringResource(R.string.v11_social_title), color = Ivory, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text(stringResource(R.string.v11_social_desc), color = Muted, fontSize = 10.sp)
        }

        if (rivals.isEmpty()) {
            item {
                PremiumPanel {
                    Text(stringResource(R.string.v11_no_rivals), color = Ivory, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.v11_no_rivals_desc), color = Muted, fontSize = 10.sp)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(text = stringResource(R.string.v11_battle), onClick = onBattle)
                }
            }
        } else {
            items(rivals) { rival ->
                Surface(color = Steel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AiGlyph(Modifier.size(38.dp), Coral)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(rival.name, color = Ivory, fontWeight = FontWeight.Black)
                            Text(
                                "${rival.battles} ${stringResource(R.string.v11_matches)} • ${rival.wins}-${rival.losses}",
                                color = Muted,
                                fontSize = 9.sp
                            )
                            if (rival.battles >= 3) {
                                Text(
                                    stringResource(R.string.v11_familiar_rival),
                                    color = Amber,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        TextButton(onClick = onBattle) {
                            Text(stringResource(R.string.v11_rematch_short), color = Turquoise)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBarV11(
    selected: V11Screen,
    onHome: () -> Unit,
    onColony: () -> Unit,
    onBattle: () -> Unit,
    onSocial: () -> Unit
) {
    Surface(color = Color(0xF7081824)) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavV11(
                label = stringResource(R.string.v11_home),
                selected = selected == V11Screen.HOME,
                modifier = Modifier.weight(1f),
                icon = { HomeGlyph(Modifier.size(24.dp), if (selected == V11Screen.HOME) Amber else Muted) },
                onClick = onHome
            )
            NavV11(
                label = stringResource(R.string.v11_colony),
                selected = selected == V11Screen.COLONY,
                modifier = Modifier.weight(1f),
                icon = { ColonyGlyph(Modifier.size(25.dp), if (selected == V11Screen.COLONY) Amber else Muted) },
                onClick = onColony
            )
            BattleNavV11(Modifier.weight(1.25f), onBattle)
            NavV11(
                label = stringResource(R.string.v11_social),
                selected = selected == V11Screen.SOCIAL,
                modifier = Modifier.weight(1f),
                icon = { SocialGlyph(Modifier.size(25.dp), if (selected == V11Screen.SOCIAL) Amber else Muted) },
                onClick = onSocial
            )
        }
    }
}

@Composable
private fun NavV11(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon()
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = if (selected) Amber else Muted,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Normal,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun BattleNavV11(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(60.dp)
            .shadow(12.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Turquoise, Turquoise2)))
            .border(1.dp, Ivory.copy(alpha = .45f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MiniBattleGlyph(Modifier.size(27.dp))
            Text(stringResource(R.string.v11_battle), color = Night, fontWeight = FontWeight.Black, fontSize = 9.sp)
        }
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .shadow(10.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (enabled) Brush.verticalGradient(listOf(Amber, Color(0xFFE58A22)))
                else Brush.verticalGradient(listOf(Color(0xFF41515B), Color(0xFF2F3D45)))
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) Night else Muted,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun PremiumPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Steel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun EventChip(title: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, color = Color(0xE6162935), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Muted, fontSize = 9.sp)
            Spacer(Modifier.width(6.dp))
            Text(value, color = Amber, fontWeight = FontWeight.Black, fontSize = 11.sp)
        }
    }
}

@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawCircle(
            brush = Brush.radialGradient(listOf(Turquoise, Turquoise2, Night), center = center, radius = size.width * .48f),
            radius = size.width * .46f
        )
        val p = Path().apply {
            moveTo(size.width * .20f, size.height * .60f)
            lineTo(size.width * .36f, size.height * .30f)
            lineTo(size.width * .50f, size.height * .52f)
            lineTo(size.width * .64f, size.height * .30f)
            lineTo(size.width * .80f, size.height * .60f)
            lineTo(size.width * .65f, size.height * .72f)
            lineTo(size.width * .35f, size.height * .72f)
            close()
        }
        drawPath(p, color = Ivory)
        drawCircle(color = Amber, radius = size.width * .07f, center = Offset(size.width * .50f, size.height * .52f))
    }
}

@Composable
private fun IsometricWorld(modifier: Modifier) {
    Canvas(modifier) {
        fun diamond(cx: Float, cy: Float, rx: Float, ry: Float, color: Color) {
            val p = Path().apply {
                moveTo(cx, cy - ry)
                lineTo(cx + rx, cy)
                lineTo(cx, cy + ry)
                lineTo(cx - rx, cy)
                close()
            }
            drawPath(p, color)
        }

        val w = size.width
        val h = size.height
        diamond(w*.50f,h*.50f,w*.36f,h*.15f,Color(0xFF173F46))
        diamond(w*.50f,h*.46f,w*.30f,h*.12f,Color(0xFF24666B))
        drawRoundRect(
            color = Color(0xFF0E2B38),
            topLeft = Offset(w*.40f,h*.28f),
            size = Size(w*.20f,h*.18f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f,12f)
        )
        drawRoundRect(
            color = Turquoise,
            topLeft = Offset(w*.44f,h*.32f),
            size = Size(w*.12f,h*.10f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f,8f)
        )
        val nodes = listOf(Offset(w*.28f,h*.52f),Offset(w*.50f,h*.60f),Offset(w*.72f,h*.52f))
        nodes.forEach {
            drawCircle(Turquoise2, radius = w*.055f, center = it)
            drawCircle(Ivory.copy(alpha=.40f), radius=w*.055f, center=it, style=Stroke(width=3f))
        }
        drawCircle(Coral, radius=w*.045f, center=Offset(w*.18f,h*.24f))
        drawCircle(Coral.copy(alpha=.55f), radius=w*.035f, center=Offset(w*.82f,h*.24f))
        drawLine(Turquoise.copy(alpha=.40f), nodes[0], nodes[1], 5f, StrokeCap.Round)
        drawLine(Turquoise.copy(alpha=.40f), nodes[1], nodes[2], 5f, StrokeCap.Round)
    }
}

@Composable
private fun InitialAvatar(name: String, size: Int) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Turquoise.copy(alpha=.16f)).border(1.dp,Turquoise,CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(name.take(1).uppercase(), color=Ivory, fontWeight=FontWeight.Black, fontSize=(size/2.7f).sp)
    }
}

@Composable
private fun AiGlyph(modifier: Modifier, accent: Color) {
    Canvas(modifier) {
        drawRoundRect(
            color = accent.copy(alpha=.20f),
            topLeft = Offset(size.width*.18f,size.height*.22f),
            size = Size(size.width*.64f,size.height*.56f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f,8f)
        )
        drawCircle(accent, radius=size.width*.07f, center=Offset(size.width*.38f,size.height*.48f))
        drawCircle(accent, radius=size.width*.07f, center=Offset(size.width*.62f,size.height*.48f))
        drawLine(accent, Offset(size.width*.50f,size.height*.12f), Offset(size.width*.50f,size.height*.22f), 3f)
    }
}

@Composable
private fun HomeGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val p = Path().apply {
            moveTo(size.width*.18f,size.height*.48f)
            lineTo(size.width*.50f,size.height*.20f)
            lineTo(size.width*.82f,size.height*.48f)
            lineTo(size.width*.76f,size.height*.48f)
            lineTo(size.width*.76f,size.height*.80f)
            lineTo(size.width*.24f,size.height*.80f)
            lineTo(size.width*.24f,size.height*.48f)
            close()
        }
        drawPath(p,color)
    }
}

@Composable
private fun ColonyGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        drawRoundRect(
            color=color,
            topLeft=Offset(size.width*.20f,size.height*.38f),
            size=Size(size.width*.60f,size.height*.42f),
            cornerRadius=androidx.compose.ui.geometry.CornerRadius(5f,5f)
        )
        drawRect(color, topLeft=Offset(size.width*.28f,size.height*.22f), size=Size(size.width*.14f,size.height*.24f))
        drawRect(color, topLeft=Offset(size.width*.58f,size.height*.22f), size=Size(size.width*.14f,size.height*.24f))
        drawCircle(Night, radius=size.width*.09f, center=Offset(size.width*.50f,size.height*.67f))
    }
}

@Composable
private fun SocialGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        drawCircle(color, radius=size.width*.14f, center=Offset(size.width*.36f,size.height*.36f))
        drawCircle(color, radius=size.width*.12f, center=Offset(size.width*.66f,size.height*.40f))
        drawLine(color, Offset(size.width*.18f,size.height*.72f), Offset(size.width*.54f,size.height*.72f), 5f, StrokeCap.Round)
        drawLine(color, Offset(size.width*.50f,size.height*.74f), Offset(size.width*.82f,size.height*.74f), 4f, StrokeCap.Round)
    }
}

@Composable
private fun MiniBattleGlyph(modifier: Modifier) {
    Canvas(modifier) {
        drawLine(Ivory, Offset(size.width*.25f,size.height*.20f), Offset(size.width*.70f,size.height*.78f), 5f, StrokeCap.Round)
        drawLine(Ivory, Offset(size.width*.75f,size.height*.20f), Offset(size.width*.30f,size.height*.78f), 5f, StrokeCap.Round)
        drawCircle(Amber, radius=size.width*.10f, center=center)
    }
}
