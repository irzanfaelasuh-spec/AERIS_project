package com.aeris.island

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

val BG = Color(0xFF09090B); val SF = Color(0xFF111114); val LN = Color(0xFF232329)
val TX = Color(0xFFF4F4F5); val DM = Color(0xFF8A8A94); val Mono = FontFamily.Monospace
object UI { var ac by mutableStateOf(Color(0xFF7CF5C8)) }
object St {
    val m = mutableStateMapOf<String, Float>()
    fun v(k: String) = m[k] ?: Cfg.f(k)
    fun set(k: String, x: Float) { m[k] = x; Cfg.set(k, x); if (k == "accent") UI.ac = Color(Cfg.accents[x.toInt().coerceIn(0, 5)]) }
}

class MainActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)
    override fun onCreate(b: Bundle?) {
        super.onCreate(b); Cfg.init(applicationContext)
        UI.ac = Color(Cfg.accents[Cfg.f("accent").toInt().coerceIn(0, 5)])
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = UI.ac, background = BG, surface = SF)) { App(tick.intValue) } }
    }
    override fun onResume() { super.onResume(); tick.intValue++ }
}

@Composable fun App(tick: Int) {
    val ctx = LocalContext.current
    var phase by rememberSaveable { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Crossfade(phase, animationSpec = tween(500), label = "") { p ->
            when (p) {
                0 -> Splash { phase = if (Cfg.b("seen") && Perm.overlay(ctx) && Perm.listener(ctx)) 2 else 1 }
                1 -> Verify(tick) { Cfg.set("seen", 1f); phase = 2 }
                else -> Main(tick)
            }
        }
    }
}

@Composable fun Lbl(t: String, m: Modifier = Modifier) = Text(t, m, color = DM, fontSize = 11.sp, letterSpacing = 2.sp, fontFamily = Mono)

@Composable fun Btn(txt: String, m: Modifier = Modifier, on: Boolean = true, filled: Boolean = false, click: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pr) .95f else 1f, spring(.55f, 700f), label = "")
    val sh = RoundedCornerShape(50)
    Box(
        m.graphicsLayer { scaleX = sc; scaleY = sc; alpha = if (on) 1f else .4f }.clip(sh)
            .background(if (filled) UI.ac else Color.Transparent).border(1.dp, if (filled) UI.ac else Color(0xFF34343C), sh)
            .clickable(interactionSource = src, indication = null, enabled = on, onClick = click)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        Alignment.Center
    ) { Text(txt, color = if (filled) BG else TX, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp) }
}

@Composable fun Splash(done: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(2400, easing = LinearEasing)); delay(200); done() }
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        Canvas(Modifier.size(220.dp, 64.dp)) {
            val p = t.value
            fun ph(a: Float, b: Float) = ((p - a) / (b - a)).coerceIn(0f, 1f)
            val e = FastOutSlowInEasing.transform(ph(.1f, .55f))
            val w = lerp(10.dp.toPx(), 168.dp.toPx(), e); val h = lerp(10.dp.toPx(), 46.dp.toPx(), e)
            val tl = Offset((size.width - w) / 2, (size.height - h) / 2)
            drawRoundRect(Color(0xFF16161A), tl, Size(w, h), CornerRadius(h / 2))
            drawRoundRect(Color(0xFF2C2C33).copy(alpha = e), tl, Size(w, h), CornerRadius(h / 2), style = Stroke(1.dp.toPx()))
            drawCircle(UI.ac.copy(alpha = 1f - ph(.45f, .6f)), 5.dp.toPx(), center)
            val bt = ph(.5f, .85f)
            for (i in 0 until 5) {
                val bh = h * .5f * bt * (.35f + .65f * abs(sin(p * 14f + i * 1.4f)))
                drawRoundRect(UI.ac, Offset(size.width / 2 - 28.dp.toPx() + i * 14.dp.toPx(), size.height / 2 - bh / 2), Size(5.dp.toPx(), max(bh, .1f)), CornerRadius(3.dp.toPx()))
            }
        }
        Spacer(Modifier.height(28.dp))
        val ta = ((t.value - .55f) / .35f).coerceIn(0f, 1f)
        Text("AERIS", Modifier.graphicsLayer { alpha = ta; translationY = (1 - ta) * 12f }, color = TX, fontSize = 15.sp, letterSpacing = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Text("dynamic island untuk android", Modifier.graphicsLayer { alpha = ta * .8f }, color = DM, fontSize = 11.sp, fontFamily = Mono, letterSpacing = 1.sp)
        Spacer(Modifier.height(36.dp))
        Box(Modifier.width(96.dp).height(2.dp).clip(CircleShape).background(LN)) { Box(Modifier.fillMaxHeight().fillMaxWidth(t.value).background(UI.ac)) }
    }
}

class Step(val t: String, val d: String, val req: Boolean, val ok: Boolean, val go: () -> Unit)

@Composable fun Verify(tick: Int, next: () -> Unit) {
    val ctx = LocalContext.current
    var t2 by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1200); t2++ } }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { t2++ }
    val pk = ctx.packageName
    val steps = remember(tick, t2) {
        listOf(
            Step("Tampil di atas aplikasi", "Wajib. Agar island muncul di atas game dan aplikasi lain.", true, Perm.overlay(ctx)) { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$pk"))) },
            Step("Akses notifikasi", "Wajib. Mendeteksi musik, pesan, dan balasan cepat.", true, Perm.listener(ctx)) { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            Step("Izin notifikasi", "Untuk notifikasi layanan latar belakang.", false, Perm.post(ctx)) { if (Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS) },
            Step("Tanpa batasan baterai", "Agar island tetap hidup saat bermain game.", false, Perm.battery(ctx)) { ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pk"))) },
            Step("Mikrofon (visualizer asli)", "Tanpa ini visualizer tetap jalan, tapi simulasi.", false, Perm.mic(ctx)) { perm.launch(Manifest.permission.RECORD_AUDIO) },
            Step("Shizuku", "Opsional. Beri semua izin di atas dengan satu ketukan.", false, Perm.shizukuOk()) { Perm.shizukuAct(ctx) }
        )
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(28.dp)); Lbl("VERIFIKASI  ·  ${steps.count { it.ok }}/${steps.size}")
        Spacer(Modifier.height(8.dp)); Text("Siapkan AERIS", color = TX, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp)); Text("Beberapa izin dibutuhkan agar island tampil di atas game dan membaca musik.", color = DM, fontSize = 14.sp, lineHeight = 20.sp)
        LazyColumn(Modifier.weight(1f).padding(top = 16.dp)) { itemsIndexed(steps) { i, s -> StepRow(i, s) } }
        Btn("NEXT  →", Modifier.fillMaxWidth().padding(vertical = 20.dp), steps.filter { it.req }.all { it.ok }, true, next)
    }
}

@Composable fun StepRow(i: Int, s: Step) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(i * 70L); a.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
    val c by animateColorAsState(if (s.ok) UI.ac else Color.Transparent, tween(300), label = "")
    Column(Modifier.graphicsLayer { alpha = a.value; translationY = (1 - a.value) * 24f }) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(c).border(1.5.dp, if (s.ok) UI.ac else DM, CircleShape))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Row { Text(s.t, color = TX, fontSize = 15.sp, fontWeight = FontWeight.Medium); if (!s.req) Text("   opsional", color = DM, fontSize = 11.sp) }
                Text(s.d, color = DM, fontSize = 12.5.sp, lineHeight = 17.sp)
            }
            Btn(if (s.ok) "AKTIF" else "IZINKAN", on = !s.ok, filled = !s.ok && s.req, click = s.go)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LN))
    }
}

@Composable fun Main(tick: Int) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var term by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(tab, transitionSpec = { (fadeIn(tween(260)) + slideInVertically { it / 20 }) togetherWith fadeOut(tween(120)) }, label = "") { t ->
                    when (t) { 0 -> Home(tick) { term = true }; 1 -> SettingsTab(); 2 -> Guide(); else -> About() }
                }
            }
            Row(Modifier.fillMaxWidth().background(BG)) {
                listOf("HOME", "SETELAN", "PANDUAN", "TENTANG").forEachIndexed { i, n ->
                    val sel = i == tab
                    val c by animateColorAsState(if (sel) TX else DM, tween(250), label = "")
                    val w by animateDpAsState(if (sel) 18.dp else 0.dp, spring(.7f, 400f), label = "")
                    Column(
                        Modifier.weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { tab = i }.padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.height(2.dp).width(w).clip(CircleShape).background(UI.ac)); Spacer(Modifier.height(8.dp))
                        Text(n, color = c, fontSize = 11.sp, letterSpacing = 2.sp, fontFamily = Mono, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        AnimatedVisibility(term, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) { Terminal { term = false } }
    }
}

@Composable fun Info(k: String, v: String, ok: Boolean? = null) = Row(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
    Text(k, Modifier.weight(1f), color = DM, fontSize = 12.sp, fontFamily = Mono, letterSpacing = 1.sp)
    Text((if (ok == true) "● " else if (ok == false) "○ " else "") + v, color = if (ok == true) UI.ac else TX, fontSize = 12.sp, fontFamily = Mono)
}

@Composable fun Home(tick: Int, start: () -> Unit) {
    val ctx = LocalContext.current
    var t2 by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); t2++ } }
    val up = remember(t2) { IslandService.up }
    val ov = remember(tick, t2) { Perm.overlay(ctx) }; val ls = remember(tick, t2) { Perm.listener(ctx) }
    val sz = remember(tick, t2) { Perm.shizukuOk() }; val mic = remember(tick, t2) { Perm.mic(ctx) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            Spacer(Modifier.height(28.dp)); Lbl("AERIS  ·  DYNAMIC ISLAND"); Spacer(Modifier.height(8.dp))
            Text(if (up) "Island menyala." else "Island mati.", color = TX, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp)); Text("Pil hitam di atas layar untuk musik, pesan, dan baterai. Tetap aktif saat main game.", color = DM, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(24.dp))
            Btn(if (up) "STOP" else "START", Modifier.fillMaxWidth().height(64.dp), filled = !up) {
                if (up) { ctx.stopService(Intent(ctx, IslandService::class.java)); t2++ } else start()
            }
            Spacer(Modifier.height(32.dp)); Lbl("SISTEM"); Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(LN))
            Info("SERVICE", if (up) "ONLINE" else "OFFLINE", up); Info("OVERLAY", if (ov) "GRANTED" else "MISSING", ov)
            Info("NOTIF ACCESS", if (ls) "GRANTED" else "MISSING", ls); Info("VISUALIZER", if (mic) "REAL FFT" else "SIMULATED", mic)
            Info("SHIZUKU", if (sz) "CONNECTED" else "OFF", sz); Info("ANDROID", "API ${Build.VERSION.SDK_INT}"); Info("VERSI", "1.0.0")
            Spacer(Modifier.height(24.dp)); Lbl("MODUL CEPAT"); Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(LN))
        }
        items(listOf("music", "notif", "reply", "chg", "lyr", "vis")) { k -> SettingRow(Cfg.all[k]!!) }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable fun SettingRow(p: P) {
    val v = St.v(p.k)
    when (p.kind) {
        0 -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(p.t, Modifier.weight(1f), color = TX, fontSize = 15.sp)
            Switch(v > .5f, { St.set(p.k, if (it) 1f else 0f) }, colors = SwitchDefaults.colors(
                checkedThumbColor = BG, checkedTrackColor = UI.ac, checkedBorderColor = UI.ac,
                uncheckedThumbColor = DM, uncheckedTrackColor = SF, uncheckedBorderColor = LN))
        }
        1 -> Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row {
                Text(p.t, Modifier.weight(1f), color = TX, fontSize = 15.sp)
                Text((if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v)) + p.u, color = UI.ac, fontFamily = Mono, fontSize = 13.sp)
            }
            Slider(v, { x -> St.set(p.k, if (p.hi - p.lo > 10f) x.roundToInt().toFloat() else (x * 10).roundToInt() / 10f) }, valueRange = p.lo..p.hi,
                colors = SliderDefaults.colors(thumbColor = UI.ac, activeTrackColor = UI.ac, inactiveTrackColor = LN))
        }
        else -> Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(p.t, color = TX, fontSize = 15.sp); Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Cfg.accents.forEachIndexed { i, c ->
                    val s by animateFloatAsState(if (v.toInt() == i) 1f else .78f, spring(.6f, 500f), label = "")
                    Box(Modifier.size(32.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape).background(Color(c))
                        .border(if (v.toInt() == i) 2.dp else 0.dp, TX, CircleShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { St.set(p.k, i.toFloat()) })
                }
            }
        }
    }
}

@Composable fun SettingsTab() {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item { Spacer(Modifier.height(28.dp)); Lbl("PENGATURAN  ·  ${Cfg.all.size}"); Spacer(Modifier.height(8.dp)); Text("Atur island", color = TX, fontSize = 30.sp, fontWeight = FontWeight.SemiBold) }
        Cfg.groups.forEach { (n, l) ->
            item { Spacer(Modifier.height(28.dp)); Lbl("$n  ·  ${l.size}"); Spacer(Modifier.height(6.dp)); Box(Modifier.fillMaxWidth().height(1.dp).background(LN)) }
            items(l.size) { SettingRow(l[it]) }
        }
        item {
            Spacer(Modifier.height(28.dp))
            Btn("RESET SEMUA") { Cfg.sp.edit().clear().putFloat("seen", 1f).apply(); St.m.clear(); UI.ac = Color(Cfg.accents[0]) }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable fun Terminal(close: () -> Unit) {
    val ctx = LocalContext.current
    val lines = remember { mutableStateListOf<String>() }
    var done by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf("--:--:--") }
    val ls = rememberLazyListState()
    val blink by rememberInfiniteTransition(label = "").animateFloat(1f, 0f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "")
    BackHandler(done) { close() }
    LaunchedEffect(Unit) { val f = SimpleDateFormat("HH:mm:ss", Locale.getDefault()); while (true) { clock = f.format(Date()); delay(250) } }
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) ls.animateScrollToItem(lines.lastIndex) }
    LaunchedEffect(Unit) {
        val t0 = SystemClock.elapsedRealtime()
        suspend fun p(s: String, d: Long = 230) { lines.add("[%7.3f] %s".format((SystemClock.elapsedRealtime() - t0) / 1000.0, s)); delay(d) }
        fun tag(b: Boolean?) = when (b) { true -> "OK  "; false -> "FAIL"; null -> "--  " }
        val ov = Perm.overlay(ctx); val lis = Perm.listener(ctx); val mic = Perm.mic(ctx)
        p("aeris-init 1.0.0  api ${Build.VERSION.SDK_INT}  ${Build.MODEL}")
        p("config  ${Cfg.all.size} keys loaded")
        p("[${tag(ov)}] overlay window permission")
        p("[${tag(lis)}] notification listener")
        p("[${tag(lis)}] media session bridge")
        p("[${tag(Perm.shizukuOk().takeIf { it })}] shizuku bridge (opsional)")
        p("[${tag(mic.takeIf { it })}] visualizer engine  ${if (mic) "real fft" else "simulated"}")
        p("[${tag(true)}] lyrics resolver  lrclib.net")
        if (ov) {
            p("starting island service ...", 450)
            ContextCompat.startForegroundService(ctx, Intent(ctx, IslandService::class.java))
            p("[OK  ] island online  ◆", 100)
        } else p("[FAIL] abort: izin overlay belum diberikan")
        done = true
    }
    Column(Modifier.fillMaxSize().background(BG).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}.padding(20.dp)) {
        Row { Text("aeris@island ~ boot", Modifier.weight(1f), color = DM, fontFamily = Mono, fontSize = 12.sp); Text(clock, color = UI.ac, fontFamily = Mono, fontSize = 12.sp) }
        Spacer(Modifier.height(14.dp)); Box(Modifier.fillMaxWidth().height(1.dp).background(LN))
        LazyColumn(Modifier.weight(1f).padding(top = 12.dp), state = ls) {
            items(lines.size) { k ->
                val s = lines[k]
                Text(s, color = when { "FAIL" in s -> Color(0xFFFF7A90); "OK  ]" in s -> UI.ac; "--  ]" in s -> DM; else -> TX }, fontFamily = Mono, fontSize = 12.sp, lineHeight = 19.sp)
            }
            item { Text("█", color = UI.ac.copy(alpha = blink), fontFamily = Mono, fontSize = 12.sp) }
        }
        AnimatedVisibility(done) { Btn("LANJUT  →", Modifier.fillMaxWidth(), filled = true, click = close) }
    }
}

@Composable fun Guide() {
    val titles = listOf("Pil hitam di atas layar", "Musik terdeteksi otomatis", "Balas tanpa membuka aplikasi", "Tetap ada saat main game", "Kalau tidak muncul")
    val bodies = listOf(
        "Setelah START, pil hitam muncul di tepi atas layar. Ketuk untuk membuka, ketuk lagi untuk menutup. Tahan lama untuk membuka AERIS.",
        "Putar lagu di Spotify atau YouTube Music. Island membesar 10 detik menampilkan cover, judul, lirik tersinkron, dan progress, lalu mengecil dengan bar visualizer tetap menari.",
        "Saat pesan masuk, ketuk BALAS, ketik, lalu kirim. Tombol hanya muncul jika aplikasinya (WhatsApp, Telegram) menyediakan aksi balas.",
        "Island melayang di atas game dan ikut pindah posisi saat layar landscape. Aktifkan 'Tanpa auto-expand di landscape' agar game tidak terganggu.",
        "Cek tiga hal: izin tampil di atas aplikasi, akses notifikasi, dan baterai tanpa batasan. Di Xiaomi, Oppo, atau Vivo aktifkan juga Autostart."
    )
    val pg = rememberPagerState { 5 }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Lbl("PANDUAN  ·  ${pg.currentPage + 1}/5")
        HorizontalPager(pg, Modifier.weight(1f).padding(top = 12.dp)) { i ->
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Demo(i, Modifier.fillMaxWidth().height(260.dp)); Spacer(Modifier.height(16.dp))
                Text(titles[i], color = TX, fontSize = 22.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp))
                Text(bodies[i], color = DM, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
            repeat(5) { i ->
                val w by animateDpAsState(if (pg.currentPage == i) 22.dp else 6.dp, spring(.8f, 500f), label = "")
                Box(Modifier.padding(3.dp).height(6.dp).width(w).clip(CircleShape).background(if (pg.currentPage == i) UI.ac else LN))
            }
        }
    }
}

@Composable fun Demo(i: Int, m: Modifier) {
    val t by rememberInfiniteTransition(label = "").animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "")
    val ac = UI.ac
    Canvas(m) {
        fun ph(a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
        val ez = FastOutSlowInEasing
        val cx = size.width / 2; val cy = size.height / 2
        val line = Color(0xFF2E2E36); val scr = Color(0xFF1A1A1F)
        if (i == 4) {
            repeat(3) { r ->
                val y = cy - 62.dp.toPx() + r * 46.dp.toPx(); val w = size.width * .82f; val x = (size.width - w) / 2
                drawRoundRect(scr, Offset(x, y), Size(w, 36.dp.toPx()), CornerRadius(12.dp.toPx()))
                drawRoundRect(line, Offset(x, y), Size(w, 36.dp.toPx()), CornerRadius(12.dp.toPx()), style = Stroke(1.dp.toPx()))
                val k = ez.transform(ph(.1f + r * .22f, .3f + r * .22f)); val c = Offset(x + 22.dp.toPx(), y + 18.dp.toPx())
                drawCircle(Color(0xFF3A3A42), 9.dp.toPx(), c, style = Stroke(1.5.dp.toPx())); drawCircle(ac.copy(alpha = k), 9.dp.toPx(), c)
                drawPath(Path().apply { moveTo(c.x - 4.dp.toPx(), c.y); lineTo(c.x - 1.dp.toPx(), c.y + 3.dp.toPx()); lineTo(c.x + 4.dp.toPx(), c.y - 3.dp.toPx()) }, BG.copy(alpha = k), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                drawRoundRect(Color(0xFF4A4A53), Offset(x + 44.dp.toPx(), y + 15.dp.toPx()), Size(w * (.35f + .12f * r), 6.dp.toPx()), CornerRadius(3.dp.toPx()))
            }
            return@Canvas
        }
        val land = i == 3
        val pw = (if (land) 232.dp else 116.dp).toPx(); val phH = (if (land) 116.dp else 232.dp).toPx()
        val x0 = cx - pw / 2; val y0 = cy - phH / 2
        drawRoundRect(scr, Offset(x0, y0), Size(pw, phH), CornerRadius(22.dp.toPx()))
        drawRoundRect(line, Offset(x0, y0), Size(pw, phH), CornerRadius(22.dp.toPx()), style = Stroke(2.dp.toPx()))
        val ex = if (i == 1 || i == 2) ez.transform(ph(.1f, .3f)) - ez.transform(ph(.82f, .96f)) else 0f
        val pwid = lerp(44.dp.toPx(), 100.dp.toPx(), ex); val phgt = lerp(12.dp.toPx(), (if (i == 2) 74.dp else 62.dp).toPx(), ex)
        val px = cx - pwid / 2; val py = y0 + 8.dp.toPx()
        drawRoundRect(Color.Black, Offset(px, py), Size(pwid, phgt), CornerRadius(lerp(phgt / 2, 14.dp.toPx(), ex)))
        fun rr(c: Color, x: Float, y: Float, w: Float, h: Float, r: Float = h / 2) =
            drawRoundRect(c, Offset(px + x.dp.toPx(), py + y.dp.toPx()), Size(w.dp.toPx(), h.dp.toPx()), CornerRadius(r.dp.toPx()))
        fun bars(x: Float, y: Float, hh: Float, n: Int, a: Float = 1f) {
            for (k in 0 until n) { val b = hh.dp.toPx() * (.3f + .7f * abs(sin(t * 18f + k * 1.7f)))
                drawRoundRect(ac.copy(alpha = a), Offset(px + x.dp.toPx() + k * 6.dp.toPx(), py + y.dp.toPx() - b / 2), Size(3.dp.toPx(), b), CornerRadius(2.dp.toPx())) }
        }
        if (i == 0) {
            val r = ph(.4f, .85f)
            drawCircle(ac.copy(alpha = (1f - r) * .8f), 8.dp.toPx() + r * 26.dp.toPx(), Offset(cx, py + phgt / 2), style = Stroke(1.5.dp.toPx()))
            drawCircle(ac.copy(alpha = .5f + .5f * sin(t * 12.5f)), 2.5.dp.toPx(), Offset(px + 9.dp.toPx(), py + phgt / 2))
        } else if (ex < .3f) {
            drawCircle(ac.copy(alpha = .9f), 3.dp.toPx(), Offset(px + 9.dp.toPx(), py + phgt / 2)); bars(pwid / 1.dp.toPx() - 17f, 6f, 8f, 3)
        }
        val a = ((ex - .5f) * 2f).coerceIn(0f, 1f)
        if (a > 0f && i == 1) {
            rr(ac.copy(alpha = a * .9f), 8f, 8f, 20f, 20f, 5f); rr(Color.White.copy(alpha = a * .9f), 34f, 11f, 38f, 5f); rr(Color.White.copy(alpha = a * .4f), 34f, 20f, 26f, 4f)
            rr(ac.copy(alpha = a * .85f), 8f, 34f, 58f, 4f); rr(Color.White.copy(alpha = a * .18f), 8f, 46f, 84f, 3f); rr(ac.copy(alpha = a), 8f, 46f, 84f * (.15f + .7f * t), 3f)
            if (a > .6f) bars(80f, 18f, 12f, 3, a)
        }
        if (a > 0f && i == 2) {
            rr(ac.copy(alpha = a * .9f), 8f, 8f, 16f, 16f, 8f); rr(Color.White.copy(alpha = a * .9f), 30f, 10f, 34f, 5f); rr(Color.White.copy(alpha = a * .45f), 30f, 19f, 54f, 4f)
            rr(Color.White.copy(alpha = a * .12f), 8f, 38f, 66f, 20f, 10f); rr(Color.White.copy(alpha = a * .8f), 16f, 46f, 44f * ph(.35f, .7f), 4f); rr(ac.copy(alpha = a), 78f, 40f, 16f, 16f, 8f)
        }
        if (land) {
            drawRect(line, Offset(x0 + 14.dp.toPx(), y0 + phH - 18.dp.toPx()), Size(pw - 28.dp.toPx(), 2.dp.toPx()))
            drawRoundRect(ac.copy(alpha = .35f), Offset(x0 + 24.dp.toPx() + (pw - 80.dp.toPx()) * t, y0 + phH - 44.dp.toPx()), Size(24.dp.toPx(), 24.dp.toPx()), CornerRadius(6.dp.toPx()))
            drawRoundRect(Color(0xFF34343C), Offset(x0 + pw * .7f, y0 + phH - 34.dp.toPx()), Size(14.dp.toPx(), 14.dp.toPx()), CornerRadius(3.dp.toPx()))
        }
    }
}

@Composable fun About() {
    val ctx = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            Spacer(Modifier.height(28.dp)); Lbl("TENTANG"); Spacer(Modifier.height(8.dp))
            Text("AERIS", color = TX, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 8.sp)
            Spacer(Modifier.height(8.dp)); Text("Dynamic island untuk Android. Musik, lirik, visualizer, balasan cepat, dan baterai dalam satu pil di atas layar, termasuk saat main game.", color = DM, fontSize = 14.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(28.dp)); Box(Modifier.fillMaxWidth().height(1.dp).background(LN))
            Info("VERSI", "1.0.0"); Info("PAKET", "com.aeris.island"); Info("UI", "Jetpack Compose"); Info("LIRIK", "LRCLIB (lrclib.net)"); Info("SHIZUKU API", "Apache-2.0"); Info("LISENSI", "Bebas dipakai")
            Spacer(Modifier.height(24.dp))
            Btn("IZIN APLIKASI") { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }
            Spacer(Modifier.height(32.dp))
        }
    }
}
