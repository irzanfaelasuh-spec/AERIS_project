package com.aeris.island

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.audiofx.Visualizer
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as WL
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private fun Context.dp(v: Float) = (v * resources.displayMetrics.density + .5f).toInt()
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun rnd(r: Float) = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, r) } }
private fun vg(v: View, on: Boolean) { v.visibility = if (on) View.VISIBLE else View.GONE }

class Ico(c: Context, private var k: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    fun set(n: Int) { k = n; invalidate() }
    override fun onDraw(cv: Canvas) {
        val s = min(width, height) * .2f
        cv.translate(width / 2f, height / 2f)
        when (k) {
            2 -> cv.drawPath(Path().apply { moveTo(-s * .7f, -s); lineTo(s, 0f); lineTo(-s * .7f, s); close() }, p)
            3 -> { cv.drawRoundRect(-s * .8f, -s, -s * .2f, s, 3f, 3f, p); cv.drawRoundRect(s * .2f, -s, s * .8f, s, 3f, 3f, p) }
            else -> {
                if (k == 1) cv.scale(-1f, 1f)
                cv.drawPath(Path().apply { moveTo(s * .9f, -s * .8f); lineTo(-s * .5f, 0f); lineTo(s * .9f, s * .8f); close() }, p)
                cv.drawRect(-s * .9f, -s * .8f, -s * .55f, s * .8f, p)
            }
        }
    }
}

class Prog(c: Context) : View(c) {
    var v = 0f; var col = Color.WHITE
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(cv: Canvas) {
        val r = height / 2f
        p.color = 0x33FFFFFF; cv.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, p)
        p.color = col; cv.drawRoundRect(0f, 0f, width * v.coerceIn(0f, 1f), height.toFloat(), r, r, p)
    }
}

class Bars(c: Context) : View(c) {
    var n = 5; var col = Color.WHITE; var sens = 1f; var fft: ByteArray? = null
    private val v = FloatArray(9); private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var on = false; private var t = 0f
    private val loop = object : Runnable {
        override fun run() { step(); invalidate(); if (on || v.any { it > .05f }) postOnAnimation(this) }
    }
    fun live(x: Boolean) { val was = on; on = x; if (x && !was) { removeCallbacks(loop); postOnAnimation(loop) } else if (!x && was) postOnAnimation(loop) }
    private fun step() {
        t += .045f; val f = fft
        for (i in 0 until n) {
            var tg = 0f
            if (on) {
                val sim = .25f + .75f * abs(sin(t * (1.3f + i * .47f) + i * 1.9f)) * (.7f + .3f * sin(t * 3.1f + i))
                var real = -1f
                if (f != null && f.size > 80) { val k = 2 + 2 * (1 + i * 3); real = hypot(f[k].toDouble(), f[k + 1].toDouble()).toFloat() }
                tg = if (real >= 3f) (real / 70f * sens).coerceIn(.15f, 1f) else (sim * sens).coerceIn(.12f, 1f)
            }
            v[i] += (tg - v[i]) * .22f
        }
    }
    override fun onDraw(cv: Canvas) {
        p.color = col; val bw = width.toFloat() / (n * 2 - 1)
        for (i in 0 until n) {
            val hh = max(bw, height * (.14f + .86f * v[i])); val x = i * bw * 2
            cv.drawRoundRect(x, (height - hh) / 2, x + bw, (height + hh) / 2, bw / 2, bw / 2, p)
        }
    }
}

class IslandService : Service() {
    companion object { @Volatile var up = false }

    private val hd = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var lp: WL
    private lateinit var root: FrameLayout
    private lateinit var cmp: FrameLayout
    private lateinit var exp: LinearLayout
    private lateinit var cArt: ImageView
    private lateinit var eArt: ImageView
    private lateinit var b1: Bars
    private lateinit var b2: Bars
    private lateinit var tT: TextView
    private lateinit var tS: TextView
    private lateinit var tL: TextView
    private lateinit var prog: Prog
    private lateinit var ctrl: LinearLayout
    private lateinit var icP: Ico
    private lateinit var btnR: TextView
    private lateinit var sendT: TextView
    private lateinit var rRow: LinearLayout
    private lateinit var edit: EditText
    private val bg = GradientDrawable().apply { setColor(Color.BLACK) }

    private var fr = 0f; private var bw = 0f; private var eH = 0
    private var isOpen = false; private var replying = false
    private var anim: ValueAnimator? = null; private var bAnim: ValueAnimator? = null
    private var kind = 0; private var note: Note? = null; private var chg = false; private var lvl = 0
    private var ctls = listOf<MediaController>(); private var ctl: MediaController? = null
    private var title: String? = null; private var artist = ""; private var art: Bitmap? = null; private var dur = 0L
    private var playing = false; private var lastKey = ""; private var lyr = listOf<Pair<Long, String>>(); private var lyLine = ""
    private var viz: Visualizer? = null; private var linked = false; private var added = false

    private val land get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private val sc get() = if (land) Cfg.f("land") / 100f else 1f
    private val hasMedia get() = title != null
    private fun ch() = dp(Cfg.f("h_c") * sc)
    private fun ew() = dp(Cfg.f("w_e") * sc)

    private val col = Runnable { if (!replying) go(false) }
    private val cn by lazy { ComponentName(this, Listener::class.java) }
    private val sl = MediaSessionManager.OnActiveSessionsChangedListener { bind(it) }
    private val retry = Runnable { connect() }
    private val pl = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> hd.post { applyCfg() } }
    private val cb = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) { pull() }
        override fun onPlaybackStateChanged(state: PlaybackState?) { pull() }
        override fun onSessionDestroyed() { pull() }
    }
    private val bat = object : BroadcastReceiver() {
        var last = -1
        override fun onReceive(c: Context, i: Intent) {
            val pg = if (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0) 1 else 0
            lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / max(1, i.getIntExtra(BatteryManager.EXTRA_SCALE, 100))
            if (last != -1 && pg != last && Cfg.b("chg")) { kind = 2; chg = pg == 1; render(); show(Cfg.f("chg_s").toLong() * 1000) }
            last = pg
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            val s = ctl?.playbackState
            if (s != null && hasMedia) {
                val pos = if (s.state == PlaybackState.STATE_PLAYING) s.position + ((SystemClock.elapsedRealtime() - s.lastPositionUpdateTime) * s.playbackSpeed).toLong() else s.position
                if (kind == 0 && dur > 0) { prog.v = pos / dur.toFloat(); prog.invalidate() }
                if (lyr.isNotEmpty()) {
                    val l = lyr.lastOrNull { it.first <= pos + 250 }?.second ?: ""
                    if (l != lyLine) { lyLine = l; tL.alpha = 0f; tL.text = l; tL.animate().alpha(1f).setDuration(240).start() }
                }
            }
            vizSync()
            hd.postDelayed(this, 250)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate(); Cfg.init(applicationContext); up = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("aeris", "AERIS", NotificationManager.IMPORTANCE_MIN))
        val stop = PendingIntent.getService(this, 0, Intent(this, IslandService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "aeris").setSmallIcon(android.R.drawable.presence_online)
            .setContentTitle("AERIS aktif").setContentText("Island berjalan").setOngoing(true).addAction(0, "Stop", stop).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1, n)
        build(); applyCfg()
        try { wm.addView(root, lp); added = true } catch (_: Exception) { stopSelf(); return }
        Hub.sink = { onNote(it) }
        ContextCompat.registerReceiver(this, bat, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        Cfg.sp.registerOnSharedPreferenceChangeListener(pl)
        connect(); hd.post(tick)
    }

    override fun onConfigurationChanged(c: Configuration) { super.onConfigurationChanged(c); if (added) applyCfg() }

    override fun onDestroy() {
        up = false; hd.removeCallbacksAndMessages(null); Hub.sink = null
        try { Cfg.sp.unregisterOnSharedPreferenceChangeListener(pl) } catch (_: Exception) {}
        try { unregisterReceiver(bat) } catch (_: Exception) {}
        try { getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(sl) } catch (_: Exception) {}
        ctls.forEach { try { it.unregisterCallback(cb) } catch (_: Exception) {} }
        stopViz()
        try { wm.removeView(root) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun text(sp: Float, c: Int, bold: Boolean) = TextView(this).apply {
        textSize = sp; setTextColor(c); maxLines = 1; ellipsize = TextUtils.TruncateAt.END; includeFontPadding = false
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun build() {
        root = FrameLayout(this).apply {
            background = bg; clipToOutline = true; outlineProvider = ViewOutlineProvider.BACKGROUND
            setOnClickListener {
                if (isOpen) { hd.removeCallbacks(col); go(false) }
                else if (Cfg.b("tap")) { kind = 0; render(); show(Cfg.f("auto_s").toLong() * 1000) }
            }
            setOnLongClickListener { startActivity(Intent(this@IslandService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }
        }
        cArt = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true; outlineProvider = rnd(dp(6f).toFloat()) }
        b1 = Bars(this)
        cmp = FrameLayout(this).apply {
            addView(cArt, FrameLayout.LayoutParams(dp(22f), dp(22f), Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = dp(9f) })
            addView(b1, FrameLayout.LayoutParams(dp(24f), dp(16f), Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = dp(13f) })
        }
        root.addView(cmp, FrameLayout.LayoutParams(-1, -1))

        eArt = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true; outlineProvider = rnd(dp(12f).toFloat()) }
        tT = text(15f, Color.WHITE, true); tS = text(12.5f, 0x99FFFFFF.toInt(), false)
        b2 = Bars(this)
        val col1 = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; addView(tT)
            addView(tS, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3f) })
        }
        val head = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(eArt, LinearLayout.LayoutParams(dp(54f), dp(54f)))
            addView(col1, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12f); marginEnd = dp(10f) })
            addView(b2, LinearLayout.LayoutParams(dp(28f), dp(22f)))
        }
        tL = text(13.5f, Color.WHITE, true).apply { setLines(2); maxLines = 2 }
        prog = Prog(this)
        val prev = Ico(this, 0).apply { setOnClickListener { ctl?.transportControls?.skipToPrevious(); touch() } }
        val next = Ico(this, 1).apply { setOnClickListener { ctl?.transportControls?.skipToNext(); touch() } }
        icP = Ico(this, 2).apply {
            setOnClickListener {
                val c = ctl ?: return@setOnClickListener
                if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
                touch()
            }
        }
        ctrl = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(prev, LinearLayout.LayoutParams(dp(64f), dp(40f)))
            addView(icP, LinearLayout.LayoutParams(dp(64f), dp(40f)))
            addView(next, LinearLayout.LayoutParams(dp(64f), dp(40f)))
        }
        btnR = text(12f, Color.WHITE, true).apply { text = "BALAS"; letterSpacing = .15f; setPadding(0, dp(12f), 0, dp(4f)); setOnClickListener { editing(true) } }
        edit = EditText(this).apply {
            hint = "Tulis balasan"; setHintTextColor(0x66FFFFFF); setTextColor(Color.WHITE); textSize = 14f; maxLines = 1; setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEND
            background = GradientDrawable().apply { setColor(0x22FFFFFF); cornerRadius = dp(18f).toFloat() }
            setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
            setOnEditorActionListener { _, a, _ -> if (a == EditorInfo.IME_ACTION_SEND) { doSend(); true } else false }
        }
        sendT = text(12f, Color.WHITE, true).apply { text = "KIRIM"; letterSpacing = .15f; setPadding(dp(12f), dp(8f), dp(4f), dp(8f)); setOnClickListener { doSend() } }
        rRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12f), 0, 0)
            addView(edit, LinearLayout.LayoutParams(0, -2, 1f)); addView(sendT)
        }
        exp = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18f), dp(16f), dp(18f), dp(14f))
            addView(head)
            addView(tL, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })
            addView(prog, LinearLayout.LayoutParams(-1, dp(4f)).apply { topMargin = dp(8f) })
            addView(ctrl, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f) })
            addView(btnR); addView(rRow)
        }
        root.addView(exp, FrameLayout.LayoutParams(ew(), -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        lp = WL(dp(100f), dp(36f), WL.TYPE_APPLICATION_OVERLAY,
            WL.FLAG_NOT_FOCUSABLE or WL.FLAG_NOT_TOUCH_MODAL or WL.FLAG_LAYOUT_IN_SCREEN or WL.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) WL.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS else WL.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun applyCfg() {
        val ac = Cfg.accents[Cfg.f("accent").toInt().coerceIn(0, 5)].toInt()
        root.alpha = Cfg.f("op") / 100f
        tL.setTextColor(ac); prog.col = ac; b1.col = ac; b2.col = ac; btnR.setTextColor(ac); sendT.setTextColor(ac)
        b1.n = Cfg.f("bars").toInt(); b2.n = b1.n; b1.sens = Cfg.f("sens"); b2.sens = b1.sens
        exp.layoutParams = FrameLayout.LayoutParams(ew(), -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        lp.y = dp(if (land) Cfg.f("y_l") else Cfg.f("y_p"))
        render()
        if (isOpen) eH = eh()
        bw = 0f; morph()
        if (!Cfg.b("music") && hasMedia) pull()
    }

    private fun eh(): Int {
        exp.measure(View.MeasureSpec.makeMeasureSpec(ew(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        return exp.measuredHeight
    }

    private fun layout() {
        val c = ch(); val w = lerp(bw, ew().toFloat(), fr)
        val hh = lerp(c.toFloat(), eH.toFloat(), fr).coerceAtLeast(c * .8f)
        lp.width = w.toInt().coerceAtLeast(1); lp.height = hh.toInt().coerceAtLeast(1)
        bg.cornerRadius = min(lerp(c / 2f, dp(Cfg.f("r_e")).toFloat(), fr.coerceIn(0f, 1f)), hh / 2f)
        cmp.alpha = (1f - fr * 2.2f).coerceIn(0f, 1f); exp.alpha = ((fr - .45f) * 2f).coerceIn(0f, 1f)
        vg(exp, fr > .02f); vg(cmp, fr < .6f)
        root.visibility = if (!Cfg.b("on") || (Cfg.b("hide_idle") && !hasMedia && fr < .02f && !isOpen)) View.GONE else View.VISIBLE
        if (root.isAttachedToWindow) wm.updateViewLayout(root, lp)
    }

    private fun morph() {
        val tg = dp(Cfg.f("w_c") * sc * (if (hasMedia) 1f else .62f)).toFloat()
        bAnim?.cancel()
        if (bw == 0f || Cfg.b("reduce_anim")) { bw = tg; layout(); return }
        bAnim = ValueAnimator.ofFloat(bw, tg).apply {
            duration = (420 / Cfg.f("anim")).toLong(); interpolator = OvershootInterpolator(.8f)
            addUpdateListener { bw = it.animatedValue as Float; layout() }; start()
        }
    }

    private fun go(o: Boolean) {
        if (!o && replying) editing(false)
        anim?.cancel(); isOpen = o
        if (o) eH = eh()
        val a = ValueAnimator.ofFloat(fr, if (o) 1f else 0f)
        val spd = Cfg.f("anim")
        a.duration = if (Cfg.b("reduce_anim")) 1 else ((if (o) 520L else 380L) / spd).toLong()
        a.interpolator = if (o) OvershootInterpolator(Cfg.f("over")) else PathInterpolator(.4f, 0f, .2f, 1f)
        a.addUpdateListener { fr = it.animatedValue as Float; layout() }
        a.start(); anim = a
    }

    private fun show(ms: Long) {
        if (!Cfg.b("on")) return
        hd.removeCallbacks(col)
        if (!isOpen) { if (Cfg.b("haptic")) root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); go(true) }
        else { eH = eh(); layout() }
        if (ms > 0 && !replying) hd.postDelayed(col, ms)
    }

    private fun touch() { if (isOpen && !replying) { hd.removeCallbacks(col); hd.postDelayed(col, Cfg.f("auto_s").toLong() * 1000) } }

    private fun render() {
        val m = hasMedia; val v = m && playing && Cfg.b("vis")
        vg(cArt, m && Cfg.b("art") && art != null); cArt.setImageBitmap(art)
        vg(b1, m && Cfg.b("vis")); b1.live(v); b2.live(v && kind == 0)
        icP.set(if (playing) 3 else 2)
        val reply = kind == 1 && note?.reply != null && Cfg.b("reply")
        when (kind) {
            1 -> {
                val n = note
                eArt.setImageDrawable(n?.icon); vg(eArt, n?.icon != null)
                tT.text = n?.title ?: ""; tS.text = if (Cfg.b("hide")) "Pesan baru" else n?.text ?: ""
                vg(tL, false); vg(prog, false); vg(ctrl, false); vg(b2, false)
            }
            2 -> {
                vg(eArt, false); tT.text = (if (chg) "Mengisi daya  ·  " else "Pengisian dicabut  ·  ") + lvl + "%"; tS.text = "Baterai"
                prog.v = lvl / 100f; prog.invalidate(); vg(prog, true); vg(tL, false); vg(ctrl, false); vg(b2, false)
            }
            else -> if (m) {
                eArt.setImageBitmap(art); vg(eArt, art != null && Cfg.b("art"))
                tT.text = title ?: ""; tS.text = artist
                vg(tL, Cfg.b("lyr") && lyr.isNotEmpty()); vg(prog, Cfg.b("prog")); vg(ctrl, Cfg.b("ctl")); vg(b2, Cfg.b("vis"))
            } else {
                vg(eArt, false); tT.text = "AERIS"; tS.text = "Menunggu musik atau notifikasi"
                vg(tL, false); vg(prog, false); vg(ctrl, false); vg(b2, false)
            }
        }
        vg(btnR, reply && !replying); vg(rRow, replying && kind == 1)
        if (isOpen && ::root.isInitialized) { eH = eh(); layout() }
    }

    private fun onNote(n: Note) { note = n; kind = 1; replying = false; render(); show(Cfg.f("notif_s").toLong() * 1000) }

    private fun editing(on: Boolean) {
        replying = on
        val imm = getSystemService(InputMethodManager::class.java)
        if (on) {
            hd.removeCallbacks(col); lp.flags = lp.flags and WL.FLAG_NOT_FOCUSABLE.inv()
            vg(rRow, true); vg(btnR, false); sync(); edit.requestFocus(); edit.post { imm.showSoftInput(edit, 0) }
        } else {
            imm.hideSoftInputFromWindow(edit.windowToken, 0); lp.flags = lp.flags or WL.FLAG_NOT_FOCUSABLE
            edit.setText(""); vg(rRow, false); vg(btnR, kind == 1 && note?.reply != null && Cfg.b("reply")); sync()
        }
    }

    private fun sync() { if (isOpen) { eH = eh(); layout() } else if (root.isAttachedToWindow) wm.updateViewLayout(root, lp) }

    private fun doSend() {
        val t = edit.text.toString().trim()
        val a = note?.reply
        if (t.isNotEmpty() && a != null) {
            try {
                val ri = a.remoteInputs; val b = Bundle()
                ri.forEach { b.putCharSequence(it.resultKey, t) }
                val i = Intent(); android.app.RemoteInput.addResultsToIntent(ri, i, b)
                a.actionIntent.send(this, 0, i)
            } catch (_: Exception) {}
        }
        editing(false); hd.postDelayed(col, 700)
    }

    private fun connect() {
        if (linked) return
        try {
            val m = getSystemService(MediaSessionManager::class.java)
            m.addOnActiveSessionsChangedListener(sl, cn, hd)
            linked = true; bind(m.getActiveSessions(cn))
        } catch (_: SecurityException) { hd.postDelayed(retry, 4000) }
    }

    private fun bind(l: List<MediaController>?) {
        ctls.forEach { it.unregisterCallback(cb) }
        ctls = l.orEmpty()
        ctls.forEach { it.registerCallback(cb, hd) }
        pull()
    }

    private fun pull() {
        val c = ctls.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: ctls.firstOrNull()
        ctl = c
        val m = c?.metadata
        val t = m?.getString(MediaMetadata.METADATA_KEY_TITLE)
        var fresh = false
        if (c == null || m == null || t.isNullOrBlank() || !Cfg.b("music")) {
            title = null; playing = false; art = null; lastKey = ""; lyr = emptyList(); lyLine = ""
        } else {
            artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
            title = t; dur = m.getLong(MediaMetadata.METADATA_KEY_DURATION)
            art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
            val key = "$t|$artist"
            if (key != lastKey) { lastKey = key; lyr = emptyList(); lyLine = ""; fresh = true; fetch(artist, t, dur, key) }
        }
        render(); morph(); vizSync()
        if (fresh && playing && Cfg.b("auto") && !(land && Cfg.b("quiet"))) { kind = 0; render(); show(Cfg.f("auto_s").toLong() * 1000) }
    }

    private fun fetch(artist: String, title: String, dur: Long, key: String) {
        if (!Cfg.b("lyr")) return
        Thread {
            try {
                val q = "https://lrclib.net/api/get?artist_name=${Uri.encode(artist)}&track_name=${Uri.encode(title)}" + if (dur > 0) "&duration=${dur / 1000}" else ""
                val c = URL(q).openConnection() as HttpURLConnection
                c.connectTimeout = 6000; c.readTimeout = 6000; c.setRequestProperty("User-Agent", "AERIS/1.0")
                if (c.responseCode == 200) {
                    val s = JSONObject(c.inputStream.bufferedReader().readText()).optString("syncedLyrics", "")
                    val rx = Regex("""\[(\d+):(\d+(?:\.\d+)?)\](.*)""")
                    val out = ArrayList<Pair<Long, String>>()
                    for (ln in s.lineSequence()) {
                        val m = rx.find(ln) ?: continue
                        val tx = m.groupValues[3].trim()
                        if (tx.isNotEmpty()) out.add(((m.groupValues[1].toInt() * 60 + m.groupValues[2].toDouble()) * 1000).toLong() to tx)
                    }
                    hd.post { if (key == lastKey) { lyr = out; render() } }
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun vizSync() {
        val want = playing && Cfg.b("vis") && Perm.mic(this)
        if (want && viz == null) {
            try {
                viz = Visualizer(0).apply {
                    setCaptureSize(128)
                    setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, r: Int) {}
                        override fun onFftDataCapture(v: Visualizer?, d: ByteArray?, r: Int) { b1.fft = d; b2.fft = d }
                    }, Visualizer.getMaxCaptureRate() / 2, false, true)
                    setEnabled(true)
                }
            } catch (_: Throwable) { viz = null }
        } else if (!want && viz != null) stopViz()
    }

    private fun stopViz() {
        try { viz?.setEnabled(false); viz?.release() } catch (_: Throwable) {}
        viz = null
        if (::b1.isInitialized) { b1.fft = null; b2.fft = null }
    }
}
