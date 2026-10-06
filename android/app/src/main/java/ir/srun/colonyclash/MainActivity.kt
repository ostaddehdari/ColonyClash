package ir.srun.colonyclash

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import ir.srun.colonyclash.game.ColorWar10
import ir.srun.colonyclash.game.ColorWarAction
import ir.srun.colonyclash.matchmaking.DeepLinkRoute
import androidx.compose.material3.NavigationBarItem

private enum class Screen { HOME, MATCHMAKING, COLONY, TERRITORY, WAR, CHALLENGE, GAME, SHOP, PROFILE }

class MainActivity : ComponentActivity() {
    private var onContactSelected: ((String, String) -> Unit)? = null
    private var deepLinkRoute by mutableStateOf<DeepLinkRoute?>(null)

    private val contactPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri: Uri? = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) {
            contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) onContactSelected?.invoke(c.getString(0) ?: "", c.getString(1) ?: "")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLinkRoute = DeepLinkRoute.parse(intent?.data)
        setContent {
            ColonyApp(
                deepLinkRoute = deepLinkRoute,
                onDeepLinkConsumed = { deepLinkRoute = null },
                onPickContact = { callback -> onContactSelected = callback; pickContact() },
                onShare = ::shareText
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkRoute = DeepLinkRoute.parse(intent.data)
    }

    private fun pickContact() = contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))

    private fun shareText(text: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, getString(R.string.share)))
    }
}

@Composable
private fun ColonyApp(deepLinkRoute:DeepLinkRoute?, onDeepLinkConsumed:()->Unit, onPickContact: (((String, String) -> Unit) -> Unit), onShare: (String) -> Unit) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var invitePayload by remember { mutableStateOf<String?>(null) }
    var contactLabel by remember { mutableStateOf<String?>(null) }
    var pendingMatchRoute by remember { mutableStateOf<DeepLinkRoute?>(null) }
    LaunchedEffect(deepLinkRoute) {
        if(deepLinkRoute is DeepLinkRoute.MatchInvite || deepLinkRoute is DeepLinkRoute.Challenge) { pendingMatchRoute=deepLinkRoute; screen=Screen.MATCHMAKING }
        if(deepLinkRoute is DeepLinkRoute.WarInvite) { screen=Screen.WAR }
        if(deepLinkRoute is DeepLinkRoute.Territory) { screen=Screen.TERRITORY }
        if(deepLinkRoute!=null) onDeepLinkConsumed()
    }
    val inviteMessage = stringResource(R.string.invite_message)
    val bg = Brush.verticalGradient(listOf(Color(0xFF07111D), Color(0xFF18154A), Color(0xFF461B68)))

    Box(Modifier.fillMaxSize().background(bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(24.dp))
            Header(screen)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when(screen) {
                    Screen.HOME -> HomeScreen(
                        onChallenge = { pendingMatchRoute=null; screen = Screen.MATCHMAKING },
                        onColony = { screen = Screen.COLONY },
                        onShop = { screen = Screen.SHOP },
                        onInvite = {
                            onPickContact { name, _ ->
                                contactLabel = name
                                invitePayload = "colonyclash://join/colony/DEMO-INVITE"
                            }
                        }
                    )
                    Screen.MATCHMAKING -> MatchmakingScreen(pendingMatchRoute,onPlay={ screen=Screen.GAME },onChallengeHub={screen=Screen.CHALLENGE})
                    Screen.COLONY -> ColonyScreen(
                        onInvite = { invitePayload = "colonyclash://join/colony/DEMO-INVITE" },
                        onChallenge = { screen = Screen.CHALLENGE },
                        onWar = { screen = Screen.WAR },
                        onTerritory = { screen = Screen.TERRITORY }
                    )
                    Screen.TERRITORY -> TerritoryScreen(onBack={screen=Screen.COLONY},onWar={screen=Screen.WAR})
                    Screen.WAR -> ColonyWarScreen(onBack={screen=Screen.COLONY},onPlay={screen=Screen.GAME},onShare=onShare)
                    Screen.CHALLENGE -> ChallengeScreen(onShare, onPlay = { screen = Screen.GAME })
                    Screen.GAME -> ColorWarGameScreen(onExit = { screen = Screen.CHALLENGE })
                    Screen.SHOP -> ShopScreen()
                    Screen.PROFILE -> ProfileScreen()
                }
            }
            NavigationBar(containerColor = Color.White.copy(alpha = .07f), tonalElevation = 0.dp) {
                NavItem("🏠", stringResource(R.string.home), screen == Screen.HOME) { screen = Screen.HOME }
                NavItem("🏰", stringResource(R.string.colony), screen == Screen.COLONY) { screen = Screen.COLONY }
                NavItem("⚔", stringResource(R.string.challenge), screen == Screen.MATCHMAKING || screen == Screen.CHALLENGE || screen == Screen.GAME) { pendingMatchRoute=null; screen = Screen.MATCHMAKING }
                NavItem("🛒", stringResource(R.string.shop), screen == Screen.SHOP) { screen = Screen.SHOP }
                NavItem("👤", stringResource(R.string.profile), screen == Screen.PROFILE) { screen = Screen.PROFILE }
            }
            Text("v0.8.0 • ${BuildConfig.MARKET}", color = Color.White.copy(alpha=.35f), fontSize=10.sp, modifier=Modifier.align(Alignment.CenterHorizontally).padding(8.dp))
        }

        invitePayload?.let { payload ->
            QrDialog(payload, contactLabel, onDismiss = { invitePayload = null }, onShare = {
                onShare("${contactLabel ?: "Friend"}, $inviteMessage\n$payload")
            })
        }
    }
}

@Composable private fun Header(screen:Screen) {
    Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), color=Color.White, fontSize=26.sp, fontWeight=FontWeight.Black)
            Text(when(screen){
                Screen.HOME -> stringResource(R.string.tagline)
                Screen.MATCHMAKING -> stringResource(R.string.matchmaking_title)
                Screen.COLONY -> stringResource(R.string.colony_hub)
                Screen.TERRITORY -> stringResource(R.string.territory_map)
                Screen.WAR -> stringResource(R.string.war_live)
                Screen.CHALLENGE -> stringResource(R.string.challenge_line)
                Screen.GAME -> stringResource(R.string.color_war_title)
                Screen.SHOP -> stringResource(R.string.shop_title)
                Screen.PROFILE -> stringResource(R.string.player_card)
            }, color=Color(0xFFD8D8FF), fontSize=12.sp)
        }
        Surface(shape=CircleShape, color=Color(0xFFFFC857).copy(alpha=.18f)) { Text("🔥 12", color=Color(0xFFFFDF87), modifier=Modifier.padding(horizontal=12.dp,vertical=8.dp),fontWeight=FontWeight.Bold) }
    }
}

@Composable private fun HomeScreen(onChallenge:()->Unit,onColony:()->Unit,onShop:()->Unit,onInvite:()->Unit) {
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Metric("⚡ ${stringResource(R.string.power)}", "120", Modifier.weight(1f))
            Metric("💎 ${stringResource(R.string.gems)}", "0", Modifier.weight(1f))
        }
        Button(onClick=onChallenge,modifier=Modifier.fillMaxWidth().height(66.dp).padding(top=8.dp),shape=RoundedCornerShape(22.dp)) {
            Text("🔥 ${stringResource(R.string.quick_challenge)}",fontSize=17.sp,fontWeight=FontWeight.Black)
        }
        Spacer(Modifier.height(14.dp))
        ActionCard("🏰", stringResource(R.string.my_colony), stringResource(R.string.colony_hint), onColony)
        Spacer(Modifier.height(10.dp))
        ActionCard("➕", stringResource(R.string.invite_friend), stringResource(R.string.invite_hint), onInvite)
        Spacer(Modifier.height(10.dp))
        ActionCard("👥", stringResource(R.string.friends), "12 ${stringResource(R.string.online_now)}", {})
        Spacer(Modifier.height(10.dp))
        ActionCard("🛒", stringResource(R.string.shop), stringResource(R.string.shop_hint), onShop)
    }
}

@Composable private fun MatchmakingScreen(route:DeepLinkRoute?,onPlay:()->Unit,onChallengeHub:()->Unit) {
    var mode by remember { mutableStateOf("LIVE") }
    var queueState by remember { mutableStateOf("idle") }
    val incomingCode = when(route){ is DeepLinkRoute.MatchInvite -> route.code; is DeepLinkRoute.Challenge -> route.code; else -> null }
    Column {
        if(incomingCode!=null) {
            Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFFFC857).copy(alpha=.15f)),shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("🔥 ${stringResource(R.string.direct_challenge)}",color=Color.White,fontWeight=FontWeight.Black,fontSize=18.sp)
                    Text("${stringResource(R.string.challenge_code)}: $incomingCode",color=Color(0xFFFFDF87),modifier=Modifier.padding(top=5.dp))
                    Spacer(Modifier.height(10.dp))
                    Button(onClick=onPlay,modifier=Modifier.fillMaxWidth()){ Text("⚔ ${stringResource(R.string.accept_and_play)}") }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(mode=="LIVE") Button(onClick={mode="LIVE"},modifier=Modifier.weight(1f)){Text("🔴 LIVE")}
            else OutlinedButton(onClick={mode="LIVE"},modifier=Modifier.weight(1f)){Text("🔴 LIVE")}
            if(mode=="ASYNC") Button(onClick={mode="ASYNC"},modifier=Modifier.weight(1f)){Text("⏳ ASYNC")}
            else OutlinedButton(onClick={mode="ASYNC"},modifier=Modifier.weight(1f)){Text("⏳ ASYNC")}
        }
        Spacer(Modifier.height(10.dp))
        Card(colors=CardDefaults.cardColors(containerColor=Color.White.copy(alpha=.09f)),shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Text("🎨",fontSize=42.sp)
                Text(stringResource(R.string.color_war_title),color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Black)
                Text(stringResource(R.string.skill_match_hint),color=Color.White.copy(alpha=.55f),fontSize=11.sp,textAlign=TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Button(onClick={ queueState=if(queueState=="searching") "matched" else "searching" },modifier=Modifier.fillMaxWidth().height(54.dp)) {
                    Text(when(queueState){"searching"->"⏳ ${stringResource(R.string.searching_rival)}";"matched"->"✅ ${stringResource(R.string.rival_found)}";else->"⚡ ${stringResource(R.string.quick_match)}"},fontWeight=FontWeight.Black)
                }
                if(queueState=="matched") {
                    Spacer(Modifier.height(8.dp)); Button(onClick=onPlay,modifier=Modifier.fillMaxWidth()){Text("🎮 ${stringResource(R.string.enter_match)}")}
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        ActionCard("👥",stringResource(R.string.challenge_friend),stringResource(R.string.challenge_friend_hint),onChallengeHub)
        Spacer(Modifier.height(8.dp))
        ActionCard("↩",stringResource(R.string.reconnect_match),stringResource(R.string.reconnect_hint),onPlay)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            MiniStat(stringResource(R.string.rating),"1,284",Modifier.weight(1f))
            MiniStat(stringResource(R.string.region),"GLOBAL",Modifier.weight(1f))
            MiniStat(stringResource(R.string.online),"12",Modifier.weight(1f))
        }
    }
}

@Composable private fun ColonyScreen(onInvite:()->Unit,onChallenge:()->Unit,onWar:()->Unit,onTerritory:()->Unit) {
    Column {
        Card(colors=CardDefaults.cardColors(containerColor=Color.White.copy(alpha=.10f)),shape=RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("🐺",fontSize=42.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("NIGHT WOLVES",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Black)
                        Text("#night-wolves • 38/50",color=Color(0xFFD9D6FF))
                    }
                    Text("LV 7",color=Color(0xFFFFDF87),fontWeight=FontWeight.Black)
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    MiniStat(stringResource(R.string.war_points),"8,420",Modifier.weight(1f))
                    MiniStat(stringResource(R.string.season),"#18",Modifier.weight(1f))
                    MiniStat(stringResource(R.string.members),"38",Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.roles),color=Color.White,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        RoleRow("👑","Reza","Owner")
        RoleRow("⚔","Sara","Captain")
        RoleRow("📣","Ali","Recruiter")
        RoleRow("🛡","Mina","Defender")
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick=onInvite,modifier=Modifier.weight(1f)){Text("➕ ${stringResource(R.string.invite)}")}
            Button(onClick=onWar,modifier=Modifier.weight(1f)){Text("🔥 ${stringResource(R.string.colony_war)}")}
        }
        Spacer(Modifier.height(8.dp))
        ActionCard("🗺",stringResource(R.string.territory_map),stringResource(R.string.territory_hint),onTerritory)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick=onChallenge,modifier=Modifier.fillMaxWidth()){Text("⚔ ${stringResource(R.string.challenge)}") }
    }
}

@Composable private fun TerritoryScreen(onBack:()->Unit,onWar:()->Unit) {
    Column {
        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF35D0BA).copy(alpha=.13f)),shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Column { Text("🌍 ${stringResource(R.string.season_map_title)}",color=Color.White,fontWeight=FontWeight.Black,fontSize=18.sp); Text("GENESIS • 18 ${stringResource(R.string.days_left)}",color=Color.White.copy(alpha=.6f),fontSize=11.sp) }
                    Text("#18",color=Color(0xFFFFDF87),fontWeight=FontWeight.Black,fontSize=22.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    MiniStat(stringResource(R.string.territories_held),"2",Modifier.weight(1f))
                    MiniStat(stringResource(R.string.season_points),"1,240",Modifier.weight(1f))
                    MiniStat(stringResource(R.string.fortification),"II",Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.territory_hint),color=Color.White.copy(alpha=.7f),fontSize=11.sp,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        TerritoryRow(listOf(
            Triple("🚪",stringResource(R.string.territory_north_gate),"mine"),
            Triple("🎓",stringResource(R.string.territory_campus),"neutral"),
            Triple("⚓",stringResource(R.string.territory_harbor),"rival")
        ))
        TerritoryRow(listOf(
            Triple("🛍",stringResource(R.string.territory_bazaar),"mine"),
            Triple("🔥",stringResource(R.string.territory_forge),"contested"),
            Triple("🏟",stringResource(R.string.territory_arena),"rival")
        ))
        TerritoryRow(listOf(
            Triple("🌴",stringResource(R.string.territory_oasis),"neutral"),
            Triple("🌈",stringResource(R.string.territory_neon),"rival"),
            Triple("🌳",stringResource(R.string.territory_garden),"neutral")
        ))
        TerritoryRow(listOf(
            Triple("🗼",stringResource(R.string.territory_tower),"rival"),
            Triple("🏰",stringResource(R.string.territory_citadel),"rival"),
            Triple("⛰",stringResource(R.string.territory_summit),"neutral")
        ))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick=onBack,modifier=Modifier.weight(1f)){Text(stringResource(R.string.back))}
            Button(onClick=onWar,modifier=Modifier.weight(1f)){Text("⚔ ${stringResource(R.string.defend_or_attack)}")}
        }
        Spacer(Modifier.height(7.dp))
        Text("🥇 FIRE SCORPIONS  4,860   •   🥈 NIGHT WOLVES  4,520   •   🥉 BLUE FOX  3,980",color=Color.White.copy(alpha=.65f),fontSize=10.sp,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
    }
}

@Composable private fun TerritoryRow(items:List<Triple<String,String,String>>) {
    Row(Modifier.fillMaxWidth().padding(vertical=3.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            val tint=when(item.third){"mine"->Color(0xFF38D6B7);"rival"->Color(0xFFFF667D);"contested"->Color(0xFFFFC857);else->Color.White.copy(alpha=.08f)}
            Card(colors=CardDefaults.cardColors(containerColor=tint.copy(alpha=if(item.third=="neutral") .08f else .16f)),shape=RoundedCornerShape(16.dp),modifier=Modifier.weight(1f).height(66.dp)) {
                Column(Modifier.fillMaxSize().padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    Text(item.first,fontSize=18.sp); Text(item.second,color=Color.White,fontWeight=FontWeight.Bold,fontSize=9.sp,maxLines=1); Text(when(item.third){"mine"->"🛡 II";"rival"->"⚔";"contested"->"🔥";else->"○"},fontSize=9.sp)
                }
            }
        }
    }
}

@Composable private fun ColonyWarScreen(onBack:()->Unit,onPlay:()->Unit,onShare:(String)->Unit) {
    var round by remember { mutableStateOf(2) }
    var left by remember { mutableStateOf(1) }
    var right by remember { mutableStateOf(0) }
    val code="WAR7F2K9"
    val link="colonyclash://war/$code"
    val warChallengeMessage = stringResource(R.string.war_challenge_message)

    Column {
        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFFF3B63).copy(alpha=.15f)),shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Text("🔥 ${stringResource(R.string.war_live)}",color=Color.White,fontWeight=FontWeight.Black,fontSize=20.sp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                    Column(horizontalAlignment=Alignment.CenterHorizontally){Text("🐺",fontSize=38.sp);Text("NIGHT WOLVES",color=Color.White,fontWeight=FontWeight.Bold)}
                    Text("$left  :  $right",color=Color(0xFFFFDF87),fontSize=32.sp,fontWeight=FontWeight.Black)
                    Column(horizontalAlignment=Alignment.CenterHorizontally){Text("🦂",fontSize=38.sp);Text("FIRE SCORPIONS",color=Color.White,fontWeight=FontWeight.Bold)}
                }
                Text("${stringResource(R.string.round)} $round / 5 • Color War 10",color=Color.White.copy(alpha=.7f),modifier=Modifier.padding(top=10.dp))
                Spacer(Modifier.height(12.dp))
                Button(onClick=onPlay,modifier=Modifier.fillMaxWidth()){Text("🎮 ${stringResource(R.string.watch_or_play)}") }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            MiniStat("👀 ${stringResource(R.string.spectators)}","128",Modifier.weight(1f))
            MiniStat("🏆 ${stringResource(R.string.mvp)}","Sara",Modifier.weight(1f))
            MiniStat("⚡ ${stringResource(R.string.war_points)}","+100",Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.live_emotes),color=Color.White,fontWeight=FontWeight.Bold)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){ listOf("🔥","😂","👏","💀","❤️","🤡").forEach{ e-> OutlinedButton(onClick={},contentPadding=PaddingValues(8.dp)){Text(e)} } }
        Spacer(Modifier.height(10.dp))
        ActionCard("🥇","Sara • 214 MVP","2 rounds • 2 wins",{})
        ActionCard("🥈","Ali • 108 MVP","1 round • 1 win",{})
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton(onClick=onBack,modifier=Modifier.weight(1f)){Text(stringResource(R.string.back))}
            OutlinedButton(onClick={onShare("$warChallengeMessage\n$link")},modifier=Modifier.weight(1f)){Text("📤 ${stringResource(R.string.share)}") }
            Button(onClick={ round=1; left=0; right=0 },modifier=Modifier.weight(1f)){Text("↩ ${stringResource(R.string.revenge)}") }
        }
    }
}

@Composable private fun ChallengeScreen(onShare:(String)->Unit,onPlay:()->Unit) {
    val code="R8X7F2K"
    val link="colonyclash://challenge/$code"
    val challengeMessage = stringResource(R.string.challenge_message)
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFFF416C).copy(alpha=.16f)),shape=RoundedCornerShape(26.dp)) {
            Column(Modifier.fillMaxWidth().padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Text("⚔",fontSize=46.sp)
                Text(stringResource(R.string.if_you_dare),color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Black,textAlign=TextAlign.Center)
                Text("Color War 10 • LIVE",color=Color(0xFFFFD7E2),modifier=Modifier.padding(top=6.dp))
                Spacer(Modifier.height(16.dp))
                Text(code,color=Color(0xFFFFDF87),fontSize=28.sp,fontWeight=FontWeight.Black)
                Text(stringResource(R.string.challenge_code),color=Color.White.copy(alpha=.6f),fontSize=11.sp)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick=onPlay,modifier=Modifier.weight(1f)) { Text("🎮 ${stringResource(R.string.play_now)}") }
                    OutlinedButton(onClick={onShare("$challengeMessage\n$link")},modifier=Modifier.weight(1f)) { Text("📤 ${stringResource(R.string.share_challenge)}") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.incoming_challenges),color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.Start))
        Spacer(Modifier.height(8.dp))
        IncomingChallenge("@mohammad","Color War","02:41")
        IncomingChallenge("NIGHT BOYS","Treasure Flip 10","08:12")
    }
}



@Composable private fun ColorWarGameScreen(onExit:()->Unit) {
    val engine = remember { ColorWar10() }
    var game by remember { mutableStateOf(engine.initialState()) }
    val (p1, p2) = engine.score(game)
    val finished = engine.isFinished(game)
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Metric("🟣 ${stringResource(R.string.you)}", p1.toString(), Modifier.weight(1f))
            Metric("🎯 ${stringResource(R.string.moves)}", "${game.actions}/${engine.maxActions}", Modifier.weight(1f))
            Metric("🟠 ${stringResource(R.string.rival)}", p2.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.color_war_rule), color=Color.White.copy(alpha=.72f), fontSize=12.sp, textAlign=TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
            repeat(5) { row ->
                Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    repeat(5) { col ->
                        val cell = game.board[row][col]
                        val cellColor = when(cell) { 1 -> Color(0xFF9C6BFF); 2 -> Color(0xFFFF8A4C); else -> Color.White.copy(alpha=.10f) }
                        Surface(
                            onClick = {
                                if (!finished && cell == 0) {
                                    runCatching { game = engine.apply(game, ColorWarAction(row,col)) }
                                }
                            },
                            modifier=Modifier.size(54.dp),
                            shape=RoundedCornerShape(14.dp),
                            color=cellColor
                        ) { Box(contentAlignment=Alignment.Center) { Text(if(cell==0) "·" else if(cell==1) "1" else "2", color=Color.White, fontWeight=FontWeight.Black) } }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        val status = if(finished) {
            when { p1>p2 -> stringResource(R.string.you_win); p2>p1 -> stringResource(R.string.rival_wins); else -> stringResource(R.string.draw) }
        } else if(game.turn==1) stringResource(R.string.your_turn) else stringResource(R.string.rival_turn)
        Text(status, color=Color(0xFFFFDF87), fontWeight=FontWeight.Black, fontSize=18.sp)
        if(game.lastCaptured>0) Text("+${game.lastCaptured} ${stringResource(R.string.captured)}", color=Color.White.copy(alpha=.7f))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick=onExit,modifier=Modifier.weight(1f)){Text(stringResource(R.string.back))}
            Button(onClick={game=engine.initialState()},modifier=Modifier.weight(1f)){Text("↻ ${stringResource(R.string.rematch)}") }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.sdk_preview_note), color=Color.White.copy(alpha=.4f), fontSize=10.sp, textAlign=TextAlign.Center)
    }
}

@Composable private fun ShopScreen() {
    var tab by remember { mutableStateOf(0) }
    val tabs=listOf(stringResource(R.string.boosters),stringResource(R.string.social_gifts),stringResource(R.string.colony_upgrades))
    Column {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Metric("💎 ${stringResource(R.string.gems)}","1,250",Modifier.weight(1f))
            Metric("🪙 ${stringResource(R.string.coins)}","4,800",Modifier.weight(1f))
            Metric("⚡ ${stringResource(R.string.power)}","120",Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            tabs.forEachIndexed { index,title ->
                if(tab==index) Button(onClick={tab=index},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=4.dp)){Text(title,fontSize=9.sp,maxLines=1)}
                else OutlinedButton(onClick={tab=index},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=4.dp)){Text(title,fontSize=9.sp,maxLines=1)}
            }
        }
        Spacer(Modifier.height(10.dp))
        when(tab){
            0 -> { StoreItem("❤️","Extra Life","15 💎","+1",{}); StoreItem("🛡","Shield","20 💎","Block one hit",{}); StoreItem("↩","Retry","25 💎","Retry one move",{}); StoreItem("⏱","+3 Seconds","15 💎","More time",{}) }
            1 -> { StoreItem("☕","Coffee","20 💎","+20 Energy • +5 Love",{}); StoreItem("🌹","Rose","15 💎","+8 Love",{}); StoreItem("🧸","Teddy Bear","80 💎","+10 Energy • +30 Love",{}); StoreItem("👑","Crown","500 💎","Premium social gift",{}) }
            else -> { StoreItem("👥","+10 ${stringResource(R.string.member_slots)}","250 💎","Grow your colony",{}); StoreItem("👥","+25 ${stringResource(R.string.member_slots)}","550 💎","Grow faster",{}); StoreItem("✨","Animated Banner","400 💎","Colony status upgrade",{}) }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.billing_note),color=Color.White.copy(alpha=.45f),fontSize=10.sp,lineHeight=14.sp)
    }
}

@Composable private fun StoreItem(icon:String,title:String,price:String,subtitle:String,onBuy:()->Unit){
    Card(colors=CardDefaults.cardColors(containerColor=Color.White.copy(alpha=.08f)),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().padding(vertical=4.dp)){
        Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){
            Text(icon,fontSize=28.sp); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)){Text(title,color=Color.White,fontWeight=FontWeight.Bold);Text(subtitle,color=Color.White.copy(alpha=.55f),fontSize=11.sp)}
            Button(onClick=onBuy,contentPadding=PaddingValues(horizontal=12.dp,vertical=6.dp)){Text(price,fontSize=11.sp)}
        }
    }
}

@Composable private fun ProfileScreen() {
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        Surface(shape=CircleShape,color=Color.White.copy(alpha=.10f),modifier=Modifier.size(88.dp)) { Box(contentAlignment=Alignment.Center){Text("😎",fontSize=48.sp)} }
        Spacer(Modifier.height(10.dp))
        Text("Mohammad Ali",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Black)
        Text("@mohammadali",color=Color(0xFFD9D6FF))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            MiniStat(stringResource(R.string.wins),"183",Modifier.weight(1f))
            MiniStat(stringResource(R.string.rating),"1,284",Modifier.weight(1f))
            MiniStat(stringResource(R.string.streak),"12",Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        ActionCard("🏰","NIGHT WOLVES","Captain • 8,420 ${stringResource(R.string.war_points)}",{})
        Spacer(Modifier.height(10.dp))
        ActionCard("👥","FIRE SQUAD","6/8 ${stringResource(R.string.members)}",{})
    }
}

@Composable private fun QrDialog(payload:String,label:String?,onDismiss:()->Unit,onShare:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,confirmButton={Button(onClick=onShare){Text("📤 ${stringResource(R.string.share)}")}},dismissButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.close))}},title={Text(stringResource(R.string.invite_ready))},text={
        Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.fillMaxWidth()) {
            if(!label.isNullOrBlank()) Text(label,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            QrImage(payload)
            Spacer(Modifier.height(8.dp))
            Text(payload,fontSize=10.sp,textAlign=TextAlign.Center)
        }
    })
}

@Composable private fun QrImage(text:String) {
    val bitmap= remember(text) { qrBitmap(text, 420) }
    Image(bitmap.asImageBitmap(),contentDescription="QR",modifier=Modifier.size(210.dp).background(Color.White).padding(8.dp))
}

private fun qrBitmap(text:String,size:Int):Bitmap {
    val matrix=MultiFormatWriter().encode(text,BarcodeFormat.QR_CODE,size,size)
    return Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888).apply {
        for(y in 0 until size) for(x in 0 until size) setPixel(x,y,if(matrix[x,y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
}

@Composable private fun NavItem(icon:String,label:String,selected:Boolean,onClick:()->Unit) {
    NavigationBarItem(selected=selected,onClick=onClick,icon={Text(icon,fontSize=20.sp)},label={Text(label,fontSize=9.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Color.White,selectedTextColor=Color.White,unselectedIconColor=Color.White.copy(alpha=.55f),unselectedTextColor=Color.White.copy(alpha=.55f),indicatorColor=Color.White.copy(alpha=.10f)))
}
@Composable private fun Metric(label:String,value:String,modifier:Modifier){ Card(modifier=modifier,colors=CardDefaults.cardColors(containerColor=Color.White.copy(alpha=.10f)),shape=RoundedCornerShape(18.dp)){Column(Modifier.fillMaxWidth().padding(14.dp)){Text(label,color=Color.White,fontWeight=FontWeight.Bold);Text(value,color=Color(0xFFFFDF87),fontWeight=FontWeight.Black,fontSize=18.sp)}} }
@Composable private fun MiniStat(label:String,value:String,modifier:Modifier){Column(modifier,horizontalAlignment=Alignment.CenterHorizontally){Text(value,color=Color.White,fontWeight=FontWeight.Black,fontSize=16.sp);Text(label,color=Color.White.copy(alpha=.55f),fontSize=10.sp)}}
@Composable private fun ActionCard(icon:String,title:String,subtitle:String,onClick:()->Unit){Card(onClick=onClick,colors=CardDefaults.cardColors(containerColor=Color.White.copy(alpha=.08f)),shape=RoundedCornerShape(18.dp)){Row(Modifier.fillMaxWidth().padding(15.dp),verticalAlignment=Alignment.CenterVertically){Text(icon,fontSize=26.sp);Spacer(Modifier.width(12.dp));Column{Text(title,color=Color.White,fontWeight=FontWeight.Bold);Text(subtitle,color=Color.White.copy(alpha=.55f),fontSize=11.sp)}}}}
@Composable private fun RoleRow(icon:String,name:String,role:String){Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){Text(icon,fontSize=20.sp);Spacer(Modifier.width(10.dp));Text(name,color=Color.White,modifier=Modifier.weight(1f));Text(role,color=Color(0xFFFFDF87),fontSize=11.sp)}}
@Composable private fun IncomingChallenge(from:String,game:String,time:String){Card(colors=CardDefaults.cardColors(containerColor=Color.White.copy(alpha=.07f)),shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().padding(vertical=4.dp)){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Text("🔥",fontSize=22.sp);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(from,color=Color.White,fontWeight=FontWeight.Bold);Text(game,color=Color.White.copy(alpha=.55f),fontSize=11.sp)};Text(time,color=Color(0xFFFFDF87),fontSize=11.sp)}}}

