package ir.srun.colonyclash.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.srun.colonyclash.BuildConfig
import ir.srun.colonyclash.R
import ir.srun.colonyclash.game.ColonyRushEngine
import ir.srun.colonyclash.game.RushBattleState
import ir.srun.colonyclash.game.RushGate
import ir.srun.colonyclash.game.RushGateType
import ir.srun.colonyclash.game.RushOutcome
import ir.srun.colonyclash.matchmaking.DeepLinkRoute
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.min

private val DeepNavy = Color(0xFF06152C)
private val Panel = Color(0xFF102A56)
private val Panel2 = Color(0xFF17386E)
private val Royal = Color(0xFF6B3EFF)
private val Cyan = Color(0xFF21D6FF)
private val Gold = Color(0xFFFFC93D)
private val Lime = Color(0xFFA8F52F)
private val Coral = Color(0xFFFF5E70)
private val PlayerBlue = Color(0xFF2DADFF)
private val RivalRed = Color(0xFFFF5A57)

private enum class V10Screen {
    HOME,
    MATCHMAKING,
    BATTLE,
    COLONY,
    DAILY,
    RIVALS
}

private data class PlayerProgress(
    val name: String = "",
    val avatar: String = "😎",
    val level: Int = 1,
    val xp: Int = 0,
    val trophies: Int = 1000,
    val coins: Int = 0,
    val streak: Int = 0,
    val matches: Int = 0,
    val wins: Int = 0,
    val colonyPoints: Int = 0,
    val dailyWins: Int = 0,
    val dailyClaimed: Boolean = false,
    val dailyKey: String = LocalDate.now().toString()
)

private data class RushOpponent(
    val name: String,
    val avatar: String,
    val rating: Int,
    val isBot: Boolean
)

private class ProgressStore(context: Context) {
    private val p = context.getSharedPreferences("colony_v10", Context.MODE_PRIVATE)

    fun load(): PlayerProgress {
        val today = LocalDate.now().toString()
        val storedDay = p.getString("daily_key", today) ?: today
        val resetDaily = storedDay != today

        return PlayerProgress(
            name = p.getString("name", "") ?: "",
            avatar = p.getString("avatar", "😎") ?: "😎",
            level = p.getInt("level", 1),
            xp = p.getInt("xp", 0),
            trophies = p.getInt("trophies", 1000),
            coins = p.getInt("coins", 0),
            streak = p.getInt("streak", 0),
            matches = p.getInt("matches", 0),
            wins = p.getInt("wins", 0),
            colonyPoints = p.getInt("colony_points", 0),
            dailyWins = if (resetDaily) 0 else p.getInt("daily_wins", 0),
            dailyClaimed = if (resetDaily) false else p.getBoolean("daily_claimed", false),
            dailyKey = today
        )
    }

    fun save(v: PlayerProgress) {
        p.edit()
            .putString("name", v.name)
            .putString("avatar", v.avatar)
            .putInt("level", v.level)
            .putInt("xp", v.xp)
            .putInt("trophies", v.trophies)
            .putInt("coins", v.coins)
            .putInt("streak", v.streak)
            .putInt("matches", v.matches)
            .putInt("wins", v.wins)
            .putInt("colony_points", v.colonyPoints)
            .putInt("daily_wins", v.dailyWins)
            .putBoolean("daily_claimed", v.dailyClaimed)
            .putString("daily_key", v.dailyKey)
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
    val store = remember { ProgressStore(context) }

    var progress by remember { mutableStateOf(store.load()) }
    var booting by rememberSaveable { mutableStateOf(true) }
    var screen by rememberSaveable { mutableStateOf(V10Screen.HOME) }
    var battleId by rememberSaveable { mutableIntStateOf(progress.matches + 1) }
    var opponent by remember { mutableStateOf(RushOpponent("NOVA", "🤖", 1015, true)) }
    var invitePhone by rememberSaveable { mutableStateOf("") }
    var inviteName by rememberSaveable { mutableStateOf("") }

    val inviteText = stringResource(R.string.v10_invite_message)

    fun persist(next: PlayerProgress) {
        progress = next
        store.save(next)
    }

    LaunchedEffect(Unit) {
        delay(280)
        booting = false
    }

    LaunchedEffect(deepLinkRoute) {
        when (deepLinkRoute) {
            is DeepLinkRoute.MatchInvite,
            is DeepLinkRoute.Challenge -> screen = V10Screen.MATCHMAKING
            is DeepLinkRoute.ColonyInvite,
            is DeepLinkRoute.WarInvite -> screen = V10Screen.COLONY
            else -> Unit
        }
        if (deepLinkRoute != null) onDeepLinkConsumed()
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Royal,
            secondary = Cyan,
            tertiary = Gold,
            background = DeepNavy,
            surface = Panel,
            onPrimary = Color.White,
            onSecondary = DeepNavy,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        when {
            booting -> FastSplashV10()
            progress.name.isBlank() -> OnboardingScreen(
                onComplete = { name, avatar ->
                    persist(progress.copy(name = name.trim().ifBlank { "Player" }, avatar = avatar))
                }
            )
            else -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF06152C),
                                    Color(0xFF102D60),
                                    Color(0xFF451B83)
                                )
                            )
                        )
                        .statusBarsPadding()
                ) {
                    Column(Modifier.fillMaxSize()) {
                        if (screen != V10Screen.BATTLE && screen != V10Screen.MATCHMAKING) {
                            PlayerHud(progress)
                        }

                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            when (screen) {
                                V10Screen.HOME -> HomeScreenV10(
                                    progress = progress,
                                    onBattle = { screen = V10Screen.MATCHMAKING },
                                    onColony = { screen = V10Screen.COLONY },
                                    onDaily = { screen = V10Screen.DAILY }
                                )

                                V10Screen.MATCHMAKING -> MatchmakingScreenV10(
                                    player = progress,
                                    battleId = battleId,
                                    onCancel = { screen = V10Screen.HOME },
                                    onReady = {
                                        opponent = it
                                        screen = V10Screen.BATTLE
                                    }
                                )

                                V10Screen.BATTLE -> BattleScreenV10(
                                    player = progress,
                                    opponent = opponent,
                                    battleId = battleId,
                                    onFinished = { outcome ->
                                        val win = outcome.result > 0
                                        val draw = outcome.result == 0
                                        val trophyDelta = when {
                                            win -> 28
                                            draw -> 4
                                            else -> -8
                                        }
                                        val xpGain = if (win) 20 else 8
                                        val coinGain = if (win) 100 else 40
                                        val colonyGain = if (win) 3 else 1
                                        val nextXpRaw = progress.xp + xpGain
                                        val levelGain = nextXpRaw / 100
                                        val nextXp = nextXpRaw % 100

                                        persist(
                                            progress.copy(
                                                level = progress.level + levelGain,
                                                xp = nextXp,
                                                trophies = (progress.trophies + trophyDelta).coerceAtLeast(0),
                                                coins = progress.coins + coinGain,
                                                streak = if (win) progress.streak + 1 else 0,
                                                matches = progress.matches + 1,
                                                wins = progress.wins + if (win) 1 else 0,
                                                colonyPoints = progress.colonyPoints + colonyGain,
                                                dailyWins = (progress.dailyWins + if (win) 1 else 0).coerceAtMost(3)
                                            )
                                        )
                                    },
                                    onRematch = {
                                        battleId += 1
                                    },
                                    onHome = {
                                        screen = V10Screen.HOME
                                    }
                                )

                                V10Screen.COLONY -> ColonyScreenV10(
                                    progress = progress,
                                    phone = invitePhone,
                                    contactName = inviteName,
                                    onPhoneChange = { invitePhone = it },
                                    onPickContact = {
                                        onPickContact { name, phone ->
                                            inviteName = name
                                            invitePhone = phone
                                        }
                                    },
                                    onSendSms = {
                                        if (invitePhone.isNotBlank()) {
                                            onSendSms(invitePhone, inviteText)
                                        }
                                    },
                                    onShare = { onShare(inviteText) },
                                    onBattle = { screen = V10Screen.MATCHMAKING }
                                )

                                V10Screen.DAILY -> DailyScreenV10(
                                    progress = progress,
                                    onBattle = { screen = V10Screen.MATCHMAKING },
                                    onClaim = {
                                        if (progress.dailyWins >= 3 && !progress.dailyClaimed) {
                                            persist(
                                                progress.copy(
                                                    coins = progress.coins + 250,
                                                    dailyClaimed = true
                                                )
                                            )
                                        }
                                    }
                                )

                                V10Screen.RIVALS -> RivalScreenV10(progress)
                            }
                        }

                        if (screen != V10Screen.BATTLE && screen != V10Screen.MATCHMAKING) {
                            GameBottomBarV10(
                                selected = screen,
                                onHome = { screen = V10Screen.HOME },
                                onColony = { screen = V10Screen.COLONY },
                                onBattle = { screen = V10Screen.MATCHMAKING },
                                onDaily = { screen = V10Screen.DAILY },
                                onRivals = { screen = V10Screen.RIVALS }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FastSplashV10() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF031127), Color(0xFF174783), Color(0xFF6531C4))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(150.dp)
                    .shadow(30.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Cyan, Royal, DeepNavy))),
                contentAlignment = Alignment.Center
            ) {
                Text("👑", fontSize = 70.sp)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.v10_title),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                stringResource(R.string.v10_loading),
                color = Color.White.copy(alpha = .66f),
                fontSize = 11.sp
            )
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { .9f },
                modifier = Modifier.width(170.dp),
                color = Lime,
                trackColor = Color.White.copy(alpha = .12f)
            )
        }
    }
}

@Composable
private fun OnboardingScreen(
    onComplete: (String, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var avatar by rememberSaveable { mutableStateOf("😎") }
    val avatars = listOf("😎", "🧑‍🚀", "🦊", "🥷", "🧙", "🦁")

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF07172F), Color(0xFF2F2E91), Color(0xFF7723B7))
                )
            )
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        GameCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⚔️", fontSize = 48.sp)
                Text(
                    stringResource(R.string.v10_title),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp
                )
                Text(
                    stringResource(R.string.v10_tagline),
                    color = Color.White.copy(alpha = .68f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    stringResource(R.string.v10_choose_avatar),
                    color = Gold,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    avatars.forEach {
                        Surface(
                            onClick = { avatar = it },
                            shape = CircleShape,
                            color = if (avatar == it) Royal else Color.White.copy(alpha = .08f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(it, fontSize = 24.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.v10_name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                PrimaryGameButton(
                    text = stringResource(R.string.v10_start),
                    onClick = { onComplete(name, avatar) },
                    enabled = name.trim().length >= 2
                )
            }
        }
    }
}

@Composable
private fun PlayerHud(progress: PlayerProgress) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Panel,
            modifier = Modifier.weight(1f)
        ) {
            Row(
                Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Royal,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(progress.avatar, fontSize = 22.sp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        progress.name,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${stringResource(R.string.v10_level)} ${progress.level}",
                            color = Gold,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(5.dp))
                        LinearProgressIndicator(
                            progress = { progress.xp / 100f },
                            modifier = Modifier
                                .height(6.dp)
                                .weight(1f),
                            color = Lime,
                            trackColor = Color.White.copy(alpha = .10f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(7.dp))
        HudPill("🏆", progress.trophies.toString())
        Spacer(Modifier.width(5.dp))
        HudPill("🪙", progress.coins.toString())
    }
}

@Composable
private fun HudPill(icon: String, value: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Panel
    ) {
        Row(
            Modifier.padding(horizontal = 9.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 14.sp)
            Spacer(Modifier.width(4.dp))
            Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun HomeScreenV10(
    progress: PlayerProgress,
    onBattle: () -> Unit,
    onColony: () -> Unit,
    onDaily: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GamePanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp),
                brush = Brush.linearGradient(
                    listOf(Color(0xFF4ED1FF), Color(0xFF3F6DF4), Color(0xFF7938D5))
                )
            ) {
                Box(Modifier.fillMaxSize()) {
                    Text("☁️", fontSize = 44.sp, modifier = Modifier.align(Alignment.TopStart))
                    Text("☁️", fontSize = 36.sp, modifier = Modifier.align(Alignment.TopEnd))
                    Text("🏰", fontSize = 126.sp, modifier = Modifier.align(Alignment.Center))
                    Text(
                        "🔵",
                        fontSize = 30.sp,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 26.dp, top = 20.dp)
                    )
                    Text(
                        "🔴",
                        fontSize = 30.sp,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 26.dp, top = 20.dp)
                    )

                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PrimaryGameButton(
                            text = "⚔️ ${stringResource(R.string.v10_battle)}",
                            onClick = onBattle
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            stringResource(R.string.v10_human_or_ai),
                            color = Color.White,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        item {
            RivalryCard(
                ourScore = 3120 + progress.colonyPoints,
                rivalScore = 3185,
                onClick = onColony
            )
        }

        item {
            DailyCard(
                progress = progress.dailyWins,
                claimed = progress.dailyClaimed,
                onClick = onDaily
            )
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatTile(
                    "🔥",
                    stringResource(R.string.v10_streak),
                    progress.streak.toString(),
                    Modifier.weight(1f)
                )
                StatTile(
                    "🎮",
                    stringResource(R.string.v10_matches),
                    progress.matches.toString(),
                    Modifier.weight(1f)
                )
                StatTile(
                    "🏅",
                    stringResource(R.string.v10_wins),
                    progress.wins.toString(),
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RivalryCard(
    ourScore: Int,
    rivalScore: Int,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(listOf(Color(0xFF167CFF), Color(0xFF5F36DC), Color(0xFFE54662))),
                RoundedCornerShape(24.dp)
            )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🏰", fontSize = 30.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.v10_rivalry),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                    Text(
                        stringResource(R.string.v10_rivalry_today),
                        color = Color.White.copy(alpha = .74f),
                        fontSize = 10.sp
                    )
                }
                Text("24H", color = Gold, fontWeight = FontWeight.Black)
            }

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ScoreSide("🔵", stringResource(R.string.v10_colony_blue), ourScore)
                Text("VS", color = Gold, fontWeight = FontWeight.Black, fontSize = 18.sp)
                ScoreSide("🔴", stringResource(R.string.v10_colony_red), rivalScore)
            }
        }
    }
}

@Composable
private fun ScoreSide(icon: String, name: String, score: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 22.sp)
        Text(name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(score.toString(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun DailyCard(
    progress: Int,
    claimed: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = Panel2,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (claimed) "✅" else "🎯", fontSize = 34.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.v10_daily_title),
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
                Text(
                    stringResource(R.string.v10_daily_desc),
                    color = Color.White.copy(alpha = .60f),
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { progress / 3f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp),
                    color = Lime,
                    trackColor = Color.White.copy(alpha = .12f)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (claimed) stringResource(R.string.v10_claimed) else "$progress/3",
                color = if (claimed) Lime else Gold,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun StatTile(
    icon: String,
    label: String,
    value: String,
    modifier: Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Panel
    ) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 23.sp)
            Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(label, color = Color.White.copy(alpha = .55f), fontSize = 9.sp)
        }
    }
}

@Composable
private fun MatchmakingScreenV10(
    player: PlayerProgress,
    battleId: Int,
    onCancel: () -> Unit,
    onReady: (RushOpponent) -> Unit
) {
    LaunchedEffect(battleId) {
        delay(900)
        onReady(
            RushOpponent(
                name = "NOVA",
                avatar = "🤖",
                rating = (player.trophies - 15).coerceAtLeast(900),
                isBot = true
            )
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF06152C), Color(0xFF122C65), Color(0xFF3D1D78))
                )
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.v10_matchmaking),
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp
            )
            Spacer(Modifier.height(18.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                MatchAvatar(player.avatar, player.name, player.trophies, PlayerBlue)
                Text("VS", color = Gold, fontWeight = FontWeight.Black, fontSize = 32.sp)
                MatchAvatar("🤖", "NOVA • AI", (player.trophies - 15).coerceAtLeast(900), RivalRed)
            }

            Spacer(Modifier.height(24.dp))

            CircularProgressIndicator(color = Cyan)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.v10_bot_fallback),
                color = Color.White.copy(alpha = .64f),
                textAlign = TextAlign.Center,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(18.dp))
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.v10_cancel))
            }
        }
    }
}

@Composable
private fun MatchAvatar(
    avatar: String,
    name: String,
    rating: Int,
    accent: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = accent,
            modifier = Modifier.size(76.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(avatar, fontSize = 40.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
        Text("🏆 $rating", color = Gold, fontSize = 10.sp)
    }
}

@Composable
private fun BattleScreenV10(
    player: PlayerProgress,
    opponent: RushOpponent,
    battleId: Int,
    onFinished: (RushOutcome) -> Unit,
    onRematch: () -> Unit,
    onHome: () -> Unit
) {
    var state by remember(battleId) {
        mutableStateOf(RushBattleState(seed = battleId * 37 + player.level))
    }
    var rewardSent by remember(battleId) { mutableStateOf(false) }

    val finished = ColonyRushEngine.isFinished(state)
    val outcome = if (finished) ColonyRushEngine.outcome(state) else null

    LaunchedEffect(finished, battleId) {
        if (finished && !rewardSent && outcome != null) {
            rewardSent = true
            onFinished(outcome)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF07172F), Color(0xFF17407A), Color(0xFF39226C))
                )
            )
            .statusBarsPadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            BattleScoreHud(player, opponent, state)

            Spacer(Modifier.height(10.dp))

            GamePanel(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF1BAFE9), Color(0xFF4C58D7), Color(0xFF30245F))
                )
            ) {
                if (!finished) {
                    BattleLane(state)
                } else {
                    BattleResult(
                        outcome = outcome!!,
                        onRematch = onRematch,
                        onHome = onHome
                    )
                }
            }

            if (!finished) {
                Spacer(Modifier.height(10.dp))
                val stage = ColonyRushEngine.currentStage(state)
                if (stage != null) {
                    Text(
                        "${stringResource(R.string.v10_stage)} ${state.stage + 1}/5 • ${stringResource(R.string.v10_choose_gate)}",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GateButton(stage.left, Modifier.weight(1f)) {
                            state = ColonyRushEngine.pick(state, true)
                        }
                        GateButton(stage.right, Modifier.weight(1f)) {
                            state = ColonyRushEngine.pick(state, false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BattleScoreHud(
    player: PlayerProgress,
    opponent: RushOpponent,
    state: RushBattleState
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xEE0B1D3D),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BattleIdentity(
                player.avatar,
                player.name,
                state.playerUnits,
                state.playerShield,
                PlayerBlue,
                Modifier.weight(1f)
            )
            Text("VS", color = Gold, fontWeight = FontWeight.Black, fontSize = 20.sp)
            BattleIdentity(
                opponent.avatar,
                if (opponent.isBot) "${opponent.name} • AI" else opponent.name,
                state.botUnits,
                state.botShield,
                RivalRed,
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BattleIdentity(
    avatar: String,
    name: String,
    units: Int,
    shield: Int,
    color: Color,
    modifier: Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = color, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(avatar, fontSize = 22.sp)
            }
        }
        Spacer(Modifier.width(7.dp))
        Column {
            Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            Text("👥 $units  🛡 $shield", color = Color.White.copy(alpha = .70f), fontSize = 9.sp)
        }
    }
}

@Composable
private fun BattleLane(state: RushBattleState) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🤖", fontSize = 48.sp)
            SquadCloud(state.botUnits, RivalRed)
            Text(
                "${state.botUnits}",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 28.sp
            )
        }

        Surface(
            shape = RoundedCornerShape(40.dp),
            color = Color(0x55304977),
            modifier = Modifier
                .align(Alignment.Center)
                .width(90.dp)
                .height(180.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "⬆\n⬆\n⬆",
                    color = Color.White.copy(alpha = .35f),
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "${state.playerUnits}",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 30.sp
            )
            SquadCloud(state.playerUnits, PlayerBlue)
            Text("👑", fontSize = 46.sp)
        }

        if (state.lastPlayerGate.isNotBlank()) {
            Text(
                "${state.lastPlayerGate}  •  NOVA ${state.lastBotGate}",
                color = Gold,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 210.dp)
            )
        }
    }
}

@Composable
private fun SquadCloud(count: Int, color: Color) {
    val dots = min(10, (count / 8).coerceAtLeast(3))
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(dots) {
            Box(
                Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, Color.White.copy(alpha = .45f), CircleShape)
            )
        }
    }
}

@Composable
private fun GateButton(
    gate: RushGate,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val colors = when (gate.type) {
        RushGateType.ADD -> listOf(Color(0xFF25C6F7), Color(0xFF186EEB))
        RushGateType.MULTIPLY -> listOf(Color(0xFFA7F52E), Color(0xFF50B92C))
        RushGateType.SHIELD -> listOf(Color(0xFF8F70FF), Color(0xFF5A37D9))
    }

    Box(
        modifier = modifier
            .height(74.dp)
            .shadow(10.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(colors))
            .border(2.dp, Color.White.copy(alpha = .55f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            gate.label,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun BattleResult(
    outcome: RushOutcome,
    onRematch: () -> Unit,
    onHome: () -> Unit
) {
    val title = when {
        outcome.result > 0 -> stringResource(R.string.v10_you_win)
        outcome.result < 0 -> stringResource(R.string.v10_you_lose)
        else -> stringResource(R.string.v10_draw)
    }
    val rewardCoins = if (outcome.result > 0) 100 else 40
    val rewardXp = if (outcome.result > 0) 20 else 8
    val rewardColony = if (outcome.result > 0) 3 else 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(if (outcome.result > 0) "👑" else "⚔️", fontSize = 76.sp)
        Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp)
        Text(
            "${outcome.playerPower} — ${outcome.botPower}",
            color = Gold,
            fontWeight = FontWeight.Black,
            fontSize = 24.sp
        )

        Spacer(Modifier.height(18.dp))

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xCC0D1F45),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                RewardItem("🪙", "+$rewardCoins", stringResource(R.string.v10_coins))
                RewardItem("⭐", "+$rewardXp", "XP")
                RewardItem("🏰", "+$rewardColony", stringResource(R.string.v10_colony_points))
            }
        }

        Spacer(Modifier.height(18.dp))

        PrimaryGameButton(
            text = "↻ ${stringResource(R.string.v10_rematch)}",
            onClick = onRematch
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onHome,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.v10_home))
        }
    }
}

@Composable
private fun RewardItem(icon: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 24.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
        Text(label, color = Color.White.copy(alpha = .55f), fontSize = 8.sp)
    }
}

@Composable
private fun ColonyScreenV10(
    progress: PlayerProgress,
    phone: String,
    contactName: String,
    onPhoneChange: (String) -> Unit,
    onPickContact: () -> Unit,
    onSendSms: () -> Unit,
    onShare: () -> Unit,
    onBattle: () -> Unit
) {
    val ourScore = 3120 + progress.colonyPoints
    val rivalScore = 3185

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GamePanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(270.dp),
                brush = Brush.linearGradient(
                    listOf(Color(0xFF2AA8FF), Color(0xFF5842DD), Color(0xFFE34862))
                )
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🏰", fontSize = 62.sp)
                    Text(
                        stringResource(R.string.v10_colony_title),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp
                    )
                    Text(
                        stringResource(R.string.v10_colony_rule),
                        color = Color.White.copy(alpha = .72f),
                        textAlign = TextAlign.Center,
                        fontSize = 10.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ScoreSide("🔵", stringResource(R.string.v10_colony_blue), ourScore)
                        Text("VS", color = Gold, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        ScoreSide("🔴", stringResource(R.string.v10_colony_red), rivalScore)
                    }
                    Spacer(Modifier.height(14.dp))
                    PrimaryGameButton(
                        text = "⚔️ ${stringResource(R.string.v10_fight_for_colony)}",
                        onClick = onBattle
                    )
                }
            }
        }

        item {
            GameCard {
                Text(
                    "👥 ${stringResource(R.string.v10_invite_member)}",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp
                )
                Text(
                    stringResource(R.string.v10_invite_desc),
                    color = Color.White.copy(alpha = .60f),
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { onPhoneChange(it.take(24)) },
                    singleLine = true,
                    label = {
                        Text(
                            if (contactName.isNotBlank()) contactName
                            else stringResource(R.string.v10_phone)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPickContact,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.v10_pick_contact), fontSize = 10.sp)
                    }
                    Button(
                        onClick = onSendSms,
                        enabled = phone.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.v10_send_sms), fontSize = 10.sp)
                    }
                }

                TextButton(
                    onClick = onShare,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📤 ${stringResource(R.string.v10_share)}")
                }
            }
        }

        item {
            GameCard {
                Text(
                    "📊 ${stringResource(R.string.v10_colony_contribution)}",
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(8.dp))
                ContributionRow(progress.name, progress.colonyPoints, "🔥")
                ContributionRow("Sara", 34, "🧙")
                ContributionRow("Ali", 28, "🛡️")
            }
        }
    }
}

@Composable
private fun ContributionRow(name: String, points: Int, avatar: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(avatar, fontSize = 21.sp)
        Spacer(Modifier.width(8.dp))
        Text(name, color = Color.White, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Text("+$points", color = Gold, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun DailyScreenV10(
    progress: PlayerProgress,
    onBattle: () -> Unit,
    onClaim: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GamePanel(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            brush = Brush.linearGradient(
                listOf(Color(0xFFFF8B2E), Color(0xFFF14E6E), Color(0xFF7A3DFF))
            )
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("🎯", fontSize = 58.sp)
                Text(
                    stringResource(R.string.v10_daily_title),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp
                )
                Text(
                    stringResource(R.string.v10_daily_desc),
                    color = Color.White.copy(alpha = .78f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        GameCard {
            Text(
                "${stringResource(R.string.v10_daily_progress)}: ${progress.dailyWins}/3",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress.dailyWins / 3f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = Lime,
                trackColor = Color.White.copy(alpha = .12f)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "🎁 250 ${stringResource(R.string.v10_coins)}",
                color = Gold,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp
            )
            Spacer(Modifier.height(10.dp))

            when {
                progress.dailyClaimed -> {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("✅ ${stringResource(R.string.v10_claimed)}")
                    }
                }
                progress.dailyWins >= 3 -> {
                    PrimaryGameButton(
                        text = "🎁 ${stringResource(R.string.v10_claim)}",
                        onClick = onClaim
                    )
                }
                else -> {
                    PrimaryGameButton(
                        text = "⚔️ ${stringResource(R.string.v10_battle)}",
                        onClick = onBattle
                    )
                }
            }
        }
    }
}

@Composable
private fun RivalScreenV10(progress: PlayerProgress) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GameCard {
                Text(
                    "🔥 ${stringResource(R.string.v10_relationships)}",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
                Text(
                    stringResource(R.string.v10_relationship_note),
                    color = Color.White.copy(alpha = .60f),
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(12.dp))
                RelationStep("1", stringResource(R.string.v10_stranger), Color(0xFF6C7B99))
                RelationStep("2", stringResource(R.string.v10_rival), Coral)
                RelationStep("3", stringResource(R.string.v10_repeat_rival), Gold)
                RelationStep("4", stringResource(R.string.v10_friend), Lime)
            }
        }

        item {
            GameCard {
                Text(
                    "🤖 NOVA • AI",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp
                )
                Text(
                    stringResource(R.string.v10_ai_transparency),
                    color = Color.White.copy(alpha = .60f),
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "${stringResource(R.string.v10_matches)}: ${progress.matches}",
                    color = Gold,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RelationStep(number: String, title: String, color: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = color, modifier = Modifier.size(30.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(number, color = Color.White, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GameBottomBarV10(
    selected: V10Screen,
    onHome: () -> Unit,
    onColony: () -> Unit,
    onBattle: () -> Unit,
    onDaily: () -> Unit,
    onRivals: () -> Unit
) {
    Surface(
        color = Color(0xF40A1A38),
        tonalElevation = 8.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            BottomItem(
                "🏠",
                stringResource(R.string.v10_home),
                selected == V10Screen.HOME,
                Modifier.weight(1f),
                onHome
            )
            BottomItem(
                "🏰",
                stringResource(R.string.v10_colony),
                selected == V10Screen.COLONY,
                Modifier.weight(1f),
                onColony
            )
            BattleBottomButton(
                modifier = Modifier.weight(1.35f),
                onClick = onBattle
            )
            BottomItem(
                "🎯",
                stringResource(R.string.v10_daily),
                selected == V10Screen.DAILY,
                Modifier.weight(1f),
                onDaily
            )
            BottomItem(
                "🔥",
                stringResource(R.string.v10_rivals),
                selected == V10Screen.RIVALS,
                Modifier.weight(1f),
                onRivals
            )
        }
    }
}

@Composable
private fun BottomItem(
    icon: String,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 22.sp)
        Text(
            label,
            color = if (selected) Gold else Color.White.copy(alpha = .62f),
            fontWeight = if (selected) FontWeight.Black else FontWeight.Normal,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun BattleBottomButton(
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(62.dp)
            .shadow(12.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Lime, Color(0xFF61C922))))
            .border(2.dp, Color.White.copy(alpha = .60f), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚔️", fontSize = 25.sp)
            Text(
                stringResource(R.string.v10_battle),
                color = Color(0xFF193900),
                fontWeight = FontWeight.Black,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun PrimaryGameButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .shadow(12.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (enabled) {
                    Brush.verticalGradient(listOf(Color(0xFFFFE04B), Color(0xFFFFA91D)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF7A7A7A), Color(0xFF555555)))
                }
            )
            .border(2.dp, Color.White.copy(alpha = .45f), RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) Color(0xFF402400) else Color.White.copy(alpha = .65f),
            fontWeight = FontWeight.Black,
            fontSize = 18.sp
        )
    }
}

@Composable
private fun GameCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Panel,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun GamePanel(
    modifier: Modifier,
    brush: Brush,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(brush)
            .border(2.dp, Color.White.copy(alpha = .20f), RoundedCornerShape(26.dp)),
        content = content
    )
}
