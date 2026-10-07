package ir.srun.colonyclash.ui

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.srun.colonyclash.BuildConfig
import ir.srun.colonyclash.R
import ir.srun.colonyclash.game.ColorWar10
import ir.srun.colonyclash.game.ColorWarAction
import ir.srun.colonyclash.game.ColorWarBot
import ir.srun.colonyclash.matchmaking.DeepLinkRoute
import ir.srun.colonyclash.social.ColonyRelation
import ir.srun.colonyclash.social.DemoPlayers
import ir.srun.colonyclash.social.DemoSocialEdges
import ir.srun.colonyclash.social.RelationshipType
import ir.srun.colonyclash.social.SocialEdgeState
import ir.srun.colonyclash.social.SquadRelation
import ir.srun.colonyclash.social.SocialPlayer
import kotlinx.coroutines.delay

private val DeepNavy = Color(0xFF07172F)
private val Navy = Color(0xFF0B2447)
private val Blue = Color(0xFF1687FF)
private val Cyan = Color(0xFF29D7FF)
private val Purple = Color(0xFF6A3CFF)
private val Violet = Color(0xFF8E53FF)
private val Gold = Color(0xFFFFC74A)
private val Lime = Color(0xFFA9F22D)
private val Coral = Color(0xFFFF5D6C)
private val Ice = Color(0xFFF5F7FF)

private enum class GameScreen {
    HOME,
    MATCHMAKING,
    BATTLE,
    COLONY,
    ARMORY,
    SOCIAL,
    SHOP,
    LEAGUE,
    PROFILE
}

private enum class QueueState { IDLE, SEARCHING }

@Composable
fun ColonyGameApp(
    deepLinkRoute: DeepLinkRoute?,
    onDeepLinkConsumed: () -> Unit,
    onPickContact: (((String, String) -> Unit) -> Unit),
    onShare: (String) -> Unit,
    onSendSms: (String, String) -> Unit
) {
    var booting by rememberSaveable { mutableStateOf(true) }
    var screen by rememberSaveable { mutableStateOf(GameScreen.HOME) }
    var opponent by remember { mutableStateOf(DemoPlayers.nova) }
    var invitePhone by rememberSaveable { mutableStateOf("") }
    var inviteName by rememberSaveable { mutableStateOf("") }

    val socialEdges = remember {
        mutableStateMapOf<String, SocialEdgeState>().apply {
            putAll(DemoSocialEdges.initial)
        }
    }

    LaunchedEffect(Unit) {
        delay(320)
        booting = false
    }

    LaunchedEffect(deepLinkRoute) {
        when (deepLinkRoute) {
            is DeepLinkRoute.MatchInvite,
            is DeepLinkRoute.Challenge -> screen = GameScreen.MATCHMAKING
            is DeepLinkRoute.WarInvite,
            is DeepLinkRoute.ColonyInvite,
            is DeepLinkRoute.Territory -> screen = GameScreen.COLONY
            is DeepLinkRoute.SquadInvite -> screen = GameScreen.SOCIAL
            null -> Unit
        }
        if (deepLinkRoute != null) onDeepLinkConsumed()
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Purple,
            secondary = Cyan,
            tertiary = Gold,
            background = DeepNavy,
            surface = Navy,
            onPrimary = Color.White,
            onSecondary = DeepNavy,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        if (booting) {
            FastSplash()
            return@MaterialTheme
        }

        val appBackground = Brush.verticalGradient(
            listOf(
                Color(0xFF07172F),
                Color(0xFF13285B),
                Color(0xFF3C1974)
            )
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(appBackground)
                .statusBarsPadding()
        ) {
            Column(Modifier.fillMaxSize()) {
                if (screen != GameScreen.BATTLE) {
                    GameTopHud(
                        onProfile = { screen = GameScreen.PROFILE },
                        onShop = { screen = GameScreen.SHOP }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (screen) {
                        GameScreen.HOME -> HomeScreen(
                            onBattle = { screen = GameScreen.MATCHMAKING },
                            onBot = {
                                opponent = DemoPlayers.nova
                                screen = GameScreen.BATTLE
                            },
                            onColony = { screen = GameScreen.COLONY },
                            onArmory = { screen = GameScreen.ARMORY },
                            onSocial = { screen = GameScreen.SOCIAL },
                            onShop = { screen = GameScreen.SHOP },
                            onLeague = { screen = GameScreen.LEAGUE }
                        )

                        GameScreen.MATCHMAKING -> MatchmakingScreen(
                            incomingRoute = deepLinkRoute,
                            onBack = { screen = GameScreen.HOME },
                            onStart = {
                                opponent = it
                                screen = GameScreen.BATTLE
                            },
                            onInviteFriend = { screen = GameScreen.SOCIAL }
                        )

                        GameScreen.BATTLE -> BattleScreen(
                            opponent = opponent,
                            edge = socialEdges[opponent.id] ?: SocialEdgeState(),
                            onEdgeChange = { socialEdges[opponent.id] = it },
                            onFinished = { result ->
                                val current = socialEdges[opponent.id] ?: SocialEdgeState()
                                socialEdges[opponent.id] = current.afterBattle(result)
                            },
                            onHome = { screen = GameScreen.HOME }
                        )

                        GameScreen.COLONY -> ColonyScreen(
                            onBattle = { screen = GameScreen.MATCHMAKING },
                            onInvite = { screen = GameScreen.SOCIAL },
                            onLeague = { screen = GameScreen.LEAGUE }
                        )

                        GameScreen.ARMORY -> ArmoryScreen()

                        GameScreen.SOCIAL -> SocialScreen(
                            socialEdges = socialEdges,
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
                                    onSendSms(invitePhone, inviteText())
                                }
                            },
                            onShare = { onShare(inviteText()) },
                            onChallenge = {
                                opponent = it
                                screen = GameScreen.BATTLE
                            },
                            onEdgeChange = { id, edge -> socialEdges[id] = edge }
                        )

                        GameScreen.SHOP -> ShopScreen()
                        GameScreen.LEAGUE -> LeagueScreen()
                        GameScreen.PROFILE -> ProfileScreen(socialEdges)
                    }
                }

                if (screen != GameScreen.BATTLE) {
                    GameBottomBar(
                        selected = screen,
                        onHome = { screen = GameScreen.HOME },
                        onColony = { screen = GameScreen.COLONY },
                        onBattle = { screen = GameScreen.MATCHMAKING },
                        onArmory = { screen = GameScreen.ARMORY },
                        onSocial = { screen = GameScreen.SOCIAL }
                    )
                }
            }
        }
    }
}

private fun inviteText(): String =
    "Colony Clash — بیا با من بازی کن. https://cc.srun.ir/i/R8X7F2K"

@Composable
private fun FastSplash() {
    val bg = Brush.verticalGradient(
        listOf(Color(0xFF05162D), Color(0xFF153A72), Color(0xFF5323A6))
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(150.dp)
                    .shadow(28.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(Cyan, Blue, Purple))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("👑", fontSize = 72.sp)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "COLONY CLASH",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 28.sp
            )
            Text(
                "⚔",
                color = Gold,
                fontSize = 28.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(22.dp))
            LinearProgressIndicator(
                progress = { .86f },
                modifier = Modifier.width(180.dp),
                color = Lime,
                trackColor = Color.White.copy(alpha = .12f)
            )
        }
    }
}

@Composable
private fun GameTopHud(
    onProfile: () -> Unit,
    onShop: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onProfile,
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF152C59),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Purple,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("😎", fontSize = 22.sp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "You",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("LV 21", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(5.dp))
                        LinearProgressIndicator(
                            progress = { .62f },
                            modifier = Modifier
                                .height(6.dp)
                                .weight(1f),
                            color = Lime,
                            trackColor = Color.White.copy(alpha = .12f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))
        CurrencyPill("💎", "12", Cyan, onShop)
        Spacer(Modifier.width(6.dp))
        CurrencyPill("🪙", "216K", Gold, onShop)
    }
}

@Composable
private fun CurrencyPill(
    icon: String,
    value: String,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF152C59)
    ) {
        Row(
            Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 15.sp)
            Spacer(Modifier.width(4.dp))
            Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text("+", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun HomeScreen(
    onBattle: () -> Unit,
    onBot: () -> Unit,
    onColony: () -> Unit,
    onArmory: () -> Unit,
    onSocial: () -> Unit,
    onShop: () -> Unit,
    onLeague: () -> Unit
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
                    .height(300.dp),
                background = Brush.linearGradient(
                    listOf(Color(0xFF45C8FF), Color(0xFF3E68EE), Color(0xFF7736D2))
                )
            ) {
                Box(Modifier.fillMaxSize()) {
                    Text("☁️", fontSize = 46.sp, modifier = Modifier.align(Alignment.TopStart))
                    Text("☁️", fontSize = 38.sp, modifier = Modifier.align(Alignment.TopEnd))
                    Text("🏰", fontSize = 116.sp, modifier = Modifier.align(Alignment.Center))
                    Text("🛡️", fontSize = 40.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 26.dp, top = 48.dp))
                    Text("⚔️", fontSize = 42.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 26.dp, top = 48.dp))

                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BigBattleButton(onBattle)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "بازیکن واقعی پیدا نشد؟ NOVA بدون توقف وارد می‌شود.",
                            color = Color.White,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeCard("🤖", "تمرین", "فوری با NOVA", Modifier.weight(1f), onBot)
                ModeCard("🏆", "رتبه‌ای", "رقیب هم‌سطح", Modifier.weight(1f), onBattle)
                ModeCard("👥", "با رفیق", "دعوت با شماره", Modifier.weight(1f), onSocial)
            }
        }

        item {
            Surface(
                onClick = onLeague,
                shape = RoundedCornerShape(22.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFFFFB421), Color(0xFFFF6E43), Color(0xFF8B3DFF))),
                        RoundedCornerShape(22.dp)
                    )
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🏆", fontSize = 42.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("فصل طلایی", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text("ماموریت‌ها، لیگ و جایزه‌های فصلی", color = Color.White.copy(alpha = .82f), fontSize = 10.sp)
                    }
                    Text("21", color = DeepNavy, fontWeight = FontWeight.Black, modifier = Modifier.background(Lime, CircleShape).padding(10.dp))
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FeatureTile("🏰", "کلونی", "جنگ و اعضا", Modifier.weight(1f), onColony)
                FeatureTile("🃏", "کارت‌ها", "ترکیب و ارتقا", Modifier.weight(1f), onArmory)
                FeatureTile("🛒", "فروشگاه", "پک و بوستر", Modifier.weight(1f), onShop)
            }
        }

        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun BigBattleButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .shadow(14.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFFFE246), Color(0xFFFFB31A))))
            .border(2.dp, Color.White.copy(alpha = .55f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚔️", fontSize = 28.sp)
            Spacer(Modifier.width(8.dp))
            Text("مبارزه کن", color = Color(0xFF3B2100), fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ModeCard(icon: String, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF163562),
        tonalElevation = 3.dp
    ) {
        Column(
            Modifier.padding(vertical = 13.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 28.sp)
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Color.White.copy(alpha = .58f), fontSize = 8.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun FeatureTile(icon: String, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = .08f)
    ) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 30.sp)
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text(subtitle, color = Color.White.copy(alpha = .5f), fontSize = 8.sp)
        }
    }
}

@Composable
private fun MatchmakingScreen(
    incomingRoute: DeepLinkRoute?,
    onBack: () -> Unit,
    onStart: (SocialPlayer) -> Unit,
    onInviteFriend: () -> Unit
) {
    var queue by remember { mutableStateOf(QueueState.IDLE) }

    LaunchedEffect(queue) {
        if (queue == QueueState.SEARCHING) {
            delay(900)
            onStart(DemoPlayers.nova)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("پیدا کردن حریف", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text("اول انسان؛ اگر نبود، Bot فوری جایگزین می‌شود.", color = Color.White.copy(alpha = .58f), fontSize = 11.sp)
        Spacer(Modifier.height(20.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerVsCard("😎", "YOU", "1,284", Blue, Modifier.weight(1f))
            Text("VS", color = Gold, fontSize = 30.sp, fontWeight = FontWeight.Black)
            PlayerVsCard("🤖", if (queue == QueueState.SEARCHING) "SEARCH…" else "NOVA", "1,180", Coral, Modifier.weight(1f))
        }

        Spacer(Modifier.height(18.dp))

        if (queue == QueueState.SEARCHING) {
            CircularProgressIndicator(color = Lime)
            Spacer(Modifier.height(10.dp))
            Text("در حال جست‌وجوی رقیب واقعی…", color = Color.White, fontWeight = FontWeight.Bold)
            Text("بازی هیچ‌وقت پشت صف متوقف نمی‌شود.", color = Color.White.copy(alpha = .5f), fontSize = 10.sp)
        } else {
            PrimaryGameButton("⚔️ نبرد سریع") { queue = QueueState.SEARCHING }
            Spacer(Modifier.height(9.dp))
            SecondaryGameButton("🤖 تمرین فوری با NOVA") { onStart(DemoPlayers.nova) }
            Spacer(Modifier.height(9.dp))
            SecondaryGameButton("📱 دعوت رفیق با شماره / شبکه اجتماعی", onClick = onInviteFriend)
        }

        Spacer(Modifier.weight(1f))
        if (incomingRoute != null) {
            Text("🔥 چالش ورودی آماده است", color = Gold, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
        }
        TextButton(onClick = onBack) { Text("برگشت") }
    }
}

@Composable
private fun PlayerVsCard(icon: String, name: String, rating: String, accent: Color, modifier: Modifier) {
    GamePanel(
        modifier = modifier,
        background = Brush.verticalGradient(listOf(accent.copy(alpha = .95f), Navy))
    ) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 44.sp)
            Text(name, color = Color.White, fontWeight = FontWeight.Black)
            Text("🏆 $rating", color = Gold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun BattleScreen(
    opponent: SocialPlayer,
    edge: SocialEdgeState,
    onEdgeChange: (SocialEdgeState) -> Unit,
    onFinished: (Int) -> Unit,
    onHome: () -> Unit
) {
    val engine = remember { ColorWar10() }
    var game by remember(opponent.id) { mutableStateOf(engine.initialState()) }
    val score = engine.score(game)
    val finished = engine.isFinished(game)

    LaunchedEffect(finished) {
        if (finished) {
            onFinished(score.first.compareTo(score.second))
        }
    }

    val battleBg = Brush.verticalGradient(listOf(Color(0xFF10284A), Color(0xFF12365A), Color(0xFF1B1D4E)))

    Column(
        Modifier
            .fillMaxSize()
            .background(battleBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BattlePlayerBadge("😎", "YOU", score.first, Blue, Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("VS", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text("${game.actions}/${engine.maxActions}", color = Color.White.copy(alpha = .55f), fontSize = 10.sp)
            }
            BattlePlayerBadge(opponent.avatar, opponent.name, score.second, Coral, Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))

        Text(
            if (finished) {
                when {
                    score.first > score.second -> "🏆 پیروزی!"
                    score.first < score.second -> "🔥 ${opponent.name} برد"
                    else -> "🤝 مساوی"
                }
            } else "🎨 نوبت تو — خانه خالی را بگیر",
            color = if (finished) Gold else Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(Modifier.height(14.dp))

        GamePanel(
            modifier = Modifier.fillMaxWidth(),
            background = Brush.verticalGradient(listOf(Color(0xFF24395E), Color(0xFF152843)))
        ) {
            Column(
                Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(5) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(5) { col ->
                            val cell = game.board[row][col]
                            val cellColor = when (cell) {
                                1 -> Color(0xFF2A9FFF)
                                2 -> Color(0xFFFF5E5E)
                                else -> Color(0xFF0B1D33)
                            }
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .shadow(4.dp, RoundedCornerShape(13.dp))
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(cellColor)
                                    .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(13.dp))
                                    .clickable(enabled = !finished && game.turn == 1 && cell == 0) {
                                        val human = runCatching {
                                            engine.apply(game, ColorWarAction(row, col))
                                        }.getOrNull() ?: return@clickable

                                        if (engine.isFinished(human)) {
                                            game = human
                                        } else {
                                            val botMove = ColorWarBot.choose(human, engine)
                                            game = if (botMove != null) {
                                                runCatching { engine.apply(human, botMove) }.getOrDefault(human)
                                            } else human
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    when (cell) { 1 -> "●"; 2 -> "●"; else -> "" },
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "${relationLabel(edge.relationship)} • ${if (opponent.isBot) "AI" else "PLAYER"}",
            color = Color.White.copy(alpha = .55f),
            fontSize = 10.sp
        )

        if (finished) {
            Spacer(Modifier.height(10.dp))
            GamePanel(
                modifier = Modifier.fillMaxWidth(),
                background = Brush.horizontalGradient(listOf(Color(0xFF48238B), Color(0xFF1D4F91)))
            ) {
                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("جوایز نبرد", color = Color.White, fontWeight = FontWeight.Black)
                    Text("🏆 +24    🪙 +40    ⭐ +30 XP", color = Gold, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                    if (!opponent.isBot && (edge.relationship == RelationshipType.RIVAL || edge.relationship == RelationshipType.STRANGER)) {
                        Spacer(Modifier.height(8.dp))
                        SecondaryGameButton("🤝 درخواست رفاقت") {
                            onEdgeChange(edge.copy(relationship = RelationshipType.FRIEND_REQUEST_SENT))
                        }
                    } else if (!opponent.isBot && edge.relationship == RelationshipType.FRIEND_REQUEST_RECEIVED) {
                        Spacer(Modifier.height(8.dp))
                        PrimaryGameButton("✅ قبول رفاقت") {
                            onEdgeChange(edge.copy(relationship = RelationshipType.FRIEND))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SecondaryGameButton("🏠 خانه", Modifier.weight(1f), onHome)
            PrimaryGameButton("↻ دوباره", Modifier.weight(1f)) { game = engine.initialState() }
        }
    }
}

@Composable
private fun BattlePlayerBadge(icon: String, name: String, score: Int, accent: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0D1D35),
        border = androidx.compose.foundation.BorderStroke(2.dp, accent.copy(alpha = .7f))
    ) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 28.sp)
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(score.toString(), color = accent, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun ColonyScreen(
    onBattle: () -> Unit,
    onInvite: () -> Unit,
    onLeague: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GamePanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(225.dp),
                background = Brush.linearGradient(listOf(Color(0xFF2259A5), Color(0xFF6A35B5)))
            ) {
                Box(Modifier.fillMaxSize()) {
                    Text("🏰", fontSize = 100.sp, modifier = Modifier.align(Alignment.Center))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = .3f),
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("BLUE CITADEL", color = Color.White, fontWeight = FontWeight.Black)
                            Text("12/50 عضو • 4 آنلاین", color = Color.White.copy(alpha = .65f), fontSize = 10.sp)
                        }
                    }
                    Text("LV 7", color = Gold, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.TopEnd).padding(14.dp))
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPanel("🏆", "6,175", "امتیاز", Modifier.weight(1f))
                StatPanel("🗺️", "2", "قلمرو", Modifier.weight(1f))
                StatPanel("🔥", "3", "برد جنگ", Modifier.weight(1f))
            }
        }

        item {
            PrimaryGameButton("⚔️ ورود به جنگ کلونی", Modifier.fillMaxWidth(), onBattle)
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryGameButton("📱 دعوت عضو", Modifier.weight(1f), onInvite)
                SecondaryGameButton("🏆 رتبه کلونی", Modifier.weight(1f), onLeague)
            }
        }

        item {
            SectionTitle("اعضای فعال")
            ColonyMember("👑", "Sara", "Captain", "1,420")
            ColonyMember("🛡️", "Ali", "Defender", "1,180")
            ColonyMember("⚔️", "Mina", "Raider", "1,090")
        }

        item {
            SectionTitle("چت کلونی")
            ChatBubble("Mina", "امشب ساعت ۹ جنگ شروع میشه 🔥")
            ChatBubble("Ali", "من هستم؛ دفاع سمت شمال با من")
        }
    }
}

@Composable
private fun ColonyMember(icon: String, name: String, role: String, points: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = .07f),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 24.sp)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = Color.White, fontWeight = FontWeight.Bold)
                Text(role, color = Color.White.copy(alpha = .5f), fontSize = 9.sp)
            }
            Text("🏆 $points", color = Gold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ChatBubble(name: String, message: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF152F59),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(name, color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(message, color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ArmoryScreen() {
    val cards = listOf(
        Triple("🤖", "NOVA Core", "33"),
        Triple("🥷", "Ninja", "26"),
        Triple("🐔", "Chicken", "15"),
        Triple("👽", "Alien", "31"),
        Triple("🛡️", "Knight", "21"),
        Triple("🦖", "Big Blob", "28"),
        Triple("🚀", "Rocket", "23"),
        Triple("🌈", "Rainbow", "22")
    )

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            Text("کارت‌ها و تجهیزات", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text("ترکیب نبرد را بساز؛ ارتقاها باید قابل فهم و تصویری باشند.", color = Color.White.copy(alpha = .55f), fontSize = 10.sp)
        }

        items(cards.chunked(2)) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { card ->
                    ArmoryCard(card.first, card.second, card.third, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ArmoryCard(icon: String, name: String, level: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF193C69),
        border = androidx.compose.foundation.BorderStroke(1.dp, Cyan.copy(alpha = .45f))
    ) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("LV $level", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text(icon, fontSize = 44.sp)
            Text(name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(7.dp))
            LinearProgressIndicator(
                progress = { .64f },
                color = Lime,
                trackColor = Color.White.copy(alpha = .1f),
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
            Text("65/100", color = Color.White.copy(alpha = .55f), fontSize = 8.sp)
        }
    }
}

@Composable
private fun SocialScreen(
    socialEdges: Map<String, SocialEdgeState>,
    phone: String,
    contactName: String,
    onPhoneChange: (String) -> Unit,
    onPickContact: () -> Unit,
    onSendSms: () -> Unit,
    onShare: () -> Unit,
    onChallenge: (SocialPlayer) -> Unit,
    onEdgeChange: (String, SocialEdgeState) -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val players = DemoPlayers.all

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            TabButton("🔥 رقبا", tab == 0, Modifier.weight(1f)) { tab = 0 }
            TabButton("👥 رفقا", tab == 1, Modifier.weight(1f)) { tab = 1 }
            TabButton("🔔 درخواست", tab == 2, Modifier.weight(1f)) { tab = 2 }
            TabButton("📱 دعوت", tab == 3, Modifier.weight(1f)) { tab = 3 }
        }

        Spacer(Modifier.height(10.dp))

        if (tab == 3) {
            InvitePanel(
                phone = phone,
                contactName = contactName,
                onPhoneChange = onPhoneChange,
                onPickContact = onPickContact,
                onSendSms = onSendSms,
                onShare = onShare
            )
            return@Column
        }

        val filtered = players.filter { player ->
            val edge = socialEdges[player.id] ?: SocialEdgeState()
            if (edge.blockedByMe) return@filter false
            when (tab) {
                0 -> edge.relationship == RelationshipType.RIVAL || edge.relationship == RelationshipType.STRANGER
                1 -> edge.relationship == RelationshipType.FRIEND || edge.squadRelation == SquadRelation.SAME_SQUAD
                else -> edge.relationship == RelationshipType.FRIEND_REQUEST_SENT || edge.relationship == RelationshipType.FRIEND_REQUEST_RECEIVED
            }
        }

        if (filtered.isEmpty()) {
            GamePanel(
                modifier = Modifier.fillMaxWidth(),
                background = Brush.horizontalGradient(listOf(Color(0xFF17345E), Color(0xFF331C68)))
            ) {
                Text(
                    if (tab == 2) "درخواست رفاقت جدیدی نداری." else "هنوز کسی اینجا نیست؛ چند نبرد انجام بده.",
                    color = Color.White.copy(alpha = .7f),
                    modifier = Modifier.padding(18.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { player ->
                    val edge = socialEdges[player.id] ?: SocialEdgeState()
                    SocialPlayerCard(
                        player = player,
                        edge = edge,
                        onChallenge = { onChallenge(player) },
                        onPrimaryRelationAction = {
                            val next = when (edge.relationship) {
                                RelationshipType.STRANGER, RelationshipType.RIVAL -> edge.copy(relationship = RelationshipType.FRIEND_REQUEST_SENT)
                                RelationshipType.FRIEND_REQUEST_RECEIVED -> edge.copy(relationship = RelationshipType.FRIEND)
                                else -> edge
                            }
                            onEdgeChange(player.id, next)
                        },
                        onBlock = {
                            onEdgeChange(player.id, edge.copy(blockedByMe = true))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InvitePanel(
    phone: String,
    contactName: String,
    onPhoneChange: (String) -> Unit,
    onPickContact: () -> Unit,
    onSendSms: () -> Unit,
    onShare: () -> Unit
) {
    GamePanel(
        modifier = Modifier.fillMaxWidth(),
        background = Brush.verticalGradient(listOf(Color(0xFF1E4F85), Color(0xFF3E1D75)))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("دعوت مستقیم", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("شماره را وارد کن یا مخاطب را انتخاب کن؛ پیامک توسط برنامه پیامک گوشی ارسال می‌شود.", color = Color.White.copy(alpha = .62f), fontSize = 10.sp)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = onPhoneChange,
                label = { Text("شماره تلفن") },
                placeholder = { Text("+98 912 345 6789") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (contactName.isNotBlank()) {
                Text("مخاطب: $contactName", color = Cyan, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
            }

            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryGameButton("📇 مخاطب", Modifier.weight(1f), onPickContact)
                PrimaryGameButton("✉️ پیامک", Modifier.weight(1f), onSendSms)
            }

            Spacer(Modifier.height(10.dp))
            SecondaryGameButton("📤 اشتراک در واتساپ، تلگرام و ...", Modifier.fillMaxWidth(), onShare)

            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = .22f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "https://cc.srun.ir/i/R8X7F2K",
                    color = Color.White.copy(alpha = .8f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(10.dp),
                    textAlign = TextAlign.Center
                )
            }

            Text(
                "لینک خام colonyclash:// دیگر به کاربر نمایش داده نمی‌شود.",
                color = Gold.copy(alpha = .8f),
                fontSize = 9.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SocialPlayerCard(
    player: SocialPlayer,
    edge: SocialEdgeState,
    onChallenge: () -> Unit,
    onPrimaryRelationAction: () -> Unit,
    onBlock: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = .07f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(player.avatar, fontSize = 34.sp)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(player.name, color = Color.White, fontWeight = FontWeight.Black)
                        Spacer(Modifier.width(5.dp))
                        if (player.online) Text("●", color = Lime, fontSize = 9.sp)
                    }
                    Text(
                        "LV ${player.level} • 🏆 ${player.rating} • ${relationLabel(edge.relationship)}",
                        color = Color.White.copy(alpha = .52f),
                        fontSize = 9.sp
                    )
                    if (edge.battles > 0) {
                        Text(
                            "⚔ ${edge.battles} بازی • ${edge.myWins}-${edge.theirWins} • Rivalry ${edge.rivalryPoints}",
                            color = Cyan.copy(alpha = .85f),
                            fontSize = 8.sp
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(colonyRelationLabel(edge.colonyRelation), color = Gold, fontSize = 9.sp)
                    if (edge.squadRelation == SquadRelation.SAME_SQUAD) {
                        Text("هم‌تیمی", color = Lime, fontSize = 8.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PrimaryGameButton("⚔️ چالش", Modifier.weight(1f), onChallenge)
                val relationAction = when (edge.relationship) {
                    RelationshipType.STRANGER, RelationshipType.RIVAL -> "🤝 رفاقت"
                    RelationshipType.FRIEND_REQUEST_RECEIVED -> "✅ قبول"
                    RelationshipType.FRIEND_REQUEST_SENT -> "⏳ ارسال شد"
                    RelationshipType.FRIEND -> "👥 رفیق"
                }
                SecondaryGameButton(
                    relationAction,
                    Modifier.weight(1f),
                    if (edge.relationship == RelationshipType.FRIEND_REQUEST_SENT || edge.relationship == RelationshipType.FRIEND) ({}) else onPrimaryRelationAction
                )
                TextButton(onClick = onBlock) { Text("⋯", color = Color.White) }
            }
        }
    }
}

@Composable
private fun ShopScreen() {
    val packs = listOf(
        Triple("🎟️", "10 Skip", "$0.99"),
        Triple("🎟️", "30 Skip", "$4.99"),
        Triple("💎", "500 Gems", "$0.99"),
        Triple("💎", "1,200 Gems", "$4.99"),
        Triple("🪙", "75K Coins", "$0.99"),
        Triple("🎁", "Starter Pack", "$3.99")
    )

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GamePanel(
                modifier = Modifier.fillMaxWidth(),
                background = Brush.horizontalGradient(listOf(Color(0xFFFF4B78), Color(0xFFFF9D24)))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🎁", fontSize = 48.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("پیشنهاد شروع", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text("پک، سکه و جم در یک کارت واضح", color = Color.White.copy(alpha = .75f), fontSize = 10.sp)
                    }
                    Text("60%", color = DeepNavy, fontWeight = FontWeight.Black, modifier = Modifier.background(Gold, CircleShape).padding(9.dp))
                }
            }
        }

        items(packs.chunked(2)) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { item ->
                    ShopCard(item.first, item.second, item.third, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        item {
            Text(
                "خرید واقعی پس از اتصال فروشگاه، روی سرور Verify و فقط یک بار Grant می‌شود.",
                color = Color.White.copy(alpha = .45f),
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ShopCard(icon: String, title: String, price: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(19.dp),
        color = Ice
    ) {
        Column(
            Modifier.padding(13.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 45.sp)
            Text(title, color = DeepNavy, fontWeight = FontWeight.Black, fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(price, color = Purple, fontWeight = FontWeight.Black, fontSize = 14.sp)
        }
    }
}

@Composable
private fun LeagueScreen() {
    val rows = listOf(
        "2630" to "Shadow88 • 25,063",
        "2631" to "YOU • 25,060",
        "2632" to "Elena • 25,059",
        "2633" to "MinaFox • 25,058",
        "2634" to "Rex_21 • 25,022"
    )

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item {
            Text("لیگ قهرمانان", color = Gold, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("رتبه با نبرد واقعی جلو می‌رود؛ Bot تمرینی رتبه را دستکاری نمی‌کند.", color = Color.White.copy(alpha = .55f), fontSize = 10.sp)
        }
        items(rows) { row ->
            val me = row.first == "2631"
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (me) Lime.copy(alpha = .82f) else Color.White.copy(alpha = .9f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(row.first, color = DeepNavy, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(if (me) "😎" else "👤", fontSize = 24.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(row.second, color = DeepNavy, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("⭐", fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(socialEdges: Map<String, SocialEdgeState>) {
    val rivals = socialEdges.values.count { it.relationship == RelationshipType.RIVAL }
    val friends = socialEdges.values.count { it.relationship == RelationshipType.FRIEND }
    val squads = socialEdges.values.count { it.squadRelation == SquadRelation.SAME_SQUAD }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = CircleShape, color = Purple, modifier = Modifier.size(96.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("😎", fontSize = 52.sp) }
        }
        Spacer(Modifier.height(10.dp))
        Text("YOU", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text("LV 21 • 🏆 1,284", color = Gold)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatPanel("🔥", rivals.toString(), "رقیب", Modifier.weight(1f))
            StatPanel("👥", friends.toString(), "رفیق", Modifier.weight(1f))
            StatPanel("🛡️", squads.toString(), "هم‌تیمی", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        GamePanel(
            modifier = Modifier.fillMaxWidth(),
            background = Brush.horizontalGradient(listOf(Color(0xFF193C69), Color(0xFF3B246F)))
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("مدل رابطه v0.9.0", color = Color.White, fontWeight = FontWeight.Black)
                Text("غریبه → رقیب → درخواست ارسالی/دریافتی → رفیق", color = Cyan, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                Text("کلونی، Squad، Block، Mute و Report لایه‌های مستقل‌اند؛ نبرد فقط رابطه رقابتی را تغییر می‌دهد.", color = Color.White.copy(alpha = .62f), fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}

@Composable
private fun GameBottomBar(
    selected: GameScreen,
    onHome: () -> Unit,
    onColony: () -> Unit,
    onBattle: () -> Unit,
    onArmory: () -> Unit,
    onSocial: () -> Unit
) {
    Surface(
        color = Color(0xFF091B35),
        shadowElevation = 14.dp,
        modifier = Modifier.navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 5.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomItem("🏠", "خانه", selected == GameScreen.HOME, Modifier.weight(1f), onHome)
            BottomItem("🏰", "کلونی", selected == GameScreen.COLONY, Modifier.weight(1f), onColony)
            BattleBottomItem(Modifier.weight(1.35f), onBattle)
            BottomItem("🃏", "کارت‌ها", selected == GameScreen.ARMORY, Modifier.weight(1f), onArmory)
            BottomItem("👥", "اجتماعی", selected == GameScreen.SOCIAL, Modifier.weight(1f), onSocial)
        }
    }
}

@Composable
private fun BottomItem(icon: String, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) Purple.copy(alpha = .32f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(icon, fontSize = 22.sp)
        Text(label, color = if (selected) Color.White else Color.White.copy(alpha = .55f), fontSize = 8.sp, fontWeight = if (selected) FontWeight.Black else FontWeight.Medium)
    }
}

@Composable
private fun BattleBottomItem(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(62.dp)
            .shadow(9.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFC9FF39), Color(0xFF7EDC20))))
            .border(2.dp, Color.White.copy(alpha = .5f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚔️", fontSize = 24.sp)
            Text("نبرد", color = Color(0xFF193000), fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun GamePanel(
    modifier: Modifier = Modifier,
    background: Brush,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(24.dp))
    ) {
        content()
    }
}

@Composable
private fun StatPanel(icon: String, value: String, label: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = .08f)
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 19.sp)
            Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
            Text(label, color = Color.White.copy(alpha = .5f), fontSize = 8.sp)
        }
    }
}

@Composable
private fun PrimaryGameButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Lime, Color(0xFF7EDC20))))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color(0xFF173000), fontWeight = FontWeight.Black, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SecondaryGameButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF235CA6), Purple)))
            .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 7.dp))
    }
}

@Composable
private fun TabButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        color = if (selected) Purple else Color.White.copy(alpha = .08f)
    ) {
        Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
}

private fun relationLabel(relation: RelationshipType): String = when (relation) {
    RelationshipType.STRANGER -> "غریبه"
    RelationshipType.RIVAL -> "رقیب"
    RelationshipType.FRIEND_REQUEST_SENT -> "درخواست ارسال شد"
    RelationshipType.FRIEND_REQUEST_RECEIVED -> "درخواست دریافتی"
    RelationshipType.FRIEND -> "رفیق"
}

private fun colonyRelationLabel(relation: ColonyRelation): String = when (relation) {
    ColonyRelation.NONE -> ""
    ColonyRelation.SAME_COLONY -> "هم‌کلونی"
    ColonyRelation.ALLY_COLONY -> "متحد"
    ColonyRelation.ENEMY_COLONY -> "دشمن"
}
