package com.aeris.island

import android.Manifest
import android.app.Notification
import android.content.*
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku

/** kind: 0 toggle, 1 slider, 2 choice (accent) */
data class P(val k: String, val t: String, val kind: Int, val d: Float, val lo: Float = 0f, val hi: Float = 1f, val u: String = "")

object Cfg {
    private fun T(k: String, t: String, d: Boolean) = P(k, t, 0, if (d) 1f else 0f)
    private fun S(k: String, t: String, d: Float, lo: Float, hi: Float, u: String = "") = P(k, t, 1, d, lo, hi, u)
    val accents = listOf(0xFF7CF5C8, 0xFF8CC8FF, 0xFFFFC857, 0xFFFF7A90, 0xFFB79CFF, 0xFFFFFFFF)
    val groups: List<Pair<String, List<P>>> = listOf(
        "ISLAND" to listOf(
            T("on", "Island aktif", true), T("boot", "Mulai otomatis saat boot", true), T("hide_idle", "Sembunyikan saat idle", false),
            S("w_c", "Lebar kompak", 126f, 90f, 200f, "dp"), S("h_c", "Tinggi kompak", 36f, 26f, 48f, "dp"),
            S("w_e", "Lebar diperluas", 340f, 260f, 420f, "dp"), S("r_e", "Radius sudut diperluas", 30f, 14f, 44f, "dp"),
            S("y_p", "Posisi vertikal portrait", 10f, 0f, 90f, "dp"), S("y_l", "Posisi vertikal landscape", 6f, 0f, 90f, "dp"),
            S("op", "Opasitas", 100f, 40f, 100f, "%"), P("accent", "Warna aksen", 2, 0f, 0f, 5f)),
        "ANIMASI" to listOf(
            S("anim", "Kecepatan animasi", 1f, .5f, 2f, "x"), S("over", "Efek membal", 1.2f, 0f, 3f),
            T("reduce_anim", "Kurangi animasi", false), T("haptic", "Getaran haptik", true), T("tap", "Ketuk untuk membuka", true)),
        "MUSIK" to listOf(
            T("music", "Deteksi musik", true), T("auto", "Auto-expand saat lagu ganti", true), S("auto_s", "Durasi expand", 10f, 2f, 30f, "dtk"),
            T("lyr", "Lirik tersinkron (LRCLIB)", true), T("art", "Tampilkan cover", true), T("vis", "Visualizer", true),
            S("bars", "Jumlah bar visualizer", 5f, 3f, 9f), S("sens", "Sensitivitas visualizer", 1f, .4f, 2f, "x"),
            T("prog", "Progress bar", true), T("ctl", "Tombol kontrol", true)),
        "NOTIFIKASI" to listOf(
            T("notif", "Notifikasi di island", true), T("msg", "Hanya aplikasi pesan", true), T("reply", "Balas cepat (RemoteInput)", true),
            T("hide", "Sembunyikan isi pesan", false), S("notif_s", "Durasi tampil", 6f, 2f, 20f, "dtk")),
        "BATERAI" to listOf(T("chg", "Popup saat charger dicolok/dicabut", true), S("chg_s", "Durasi popup", 4f, 2f, 10f, "dtk")),
        "GAME" to listOf(S("land", "Skala di landscape", 85f, 50f, 120f, "%"), T("quiet", "Tanpa auto-expand di landscape", false))
    )
    val all by lazy { groups.flatMap { it.second }.associateBy { it.k } }
    lateinit var sp: SharedPreferences
    fun init(c: Context) { if (!::sp.isInitialized) sp = c.applicationContext.getSharedPreferences("aeris", 0) }
    fun f(k: String): Float = sp.getFloat(k, all[k]?.d ?: 0f)
    fun b(k: String) = f(k) > .5f
    fun set(k: String, v: Float) = sp.edit().putFloat(k, v).apply()
}

class Note(val pkg: String, val title: String, val text: String, val reply: Notification.Action?, val icon: Drawable?)
object Hub { var sink: ((Note) -> Unit)? = null }

class Listener : NotificationListenerService() {
    override fun onNotificationPosted(s: StatusBarNotification) {
        Cfg.init(applicationContext)
        if (!Cfg.b("notif") || s.packageName == packageName) return
        val n = s.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0 || n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (n.category == Notification.CATEGORY_TRANSPORT) return
        val reply = n.actions?.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }
        if (Cfg.b("msg") && n.category != Notification.CATEGORY_MESSAGE && reply == null) return
        val e = n.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val ic = try { packageManager.getApplicationIcon(s.packageName) } catch (_: Exception) { null }
        Handler(Looper.getMainLooper()).post { Hub.sink?.invoke(Note(s.packageName, title, text, reply, ic)) }
    }
}

class Boot : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Cfg.init(c)
        if (Cfg.b("boot") && Perm.overlay(c)) ContextCompat.startForegroundService(c, Intent(c, IslandService::class.java))
    }
}

object Perm {
    fun overlay(c: Context) = Settings.canDrawOverlays(c)
    fun listener(c: Context) = NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)
    fun post(c: Context) = Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    fun mic(c: Context) = c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun battery(c: Context) = (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)
    fun shizukuUp() = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
    fun shizukuOk() = shizukuUp() && try { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (_: Throwable) { false }

    private fun sh(cmd: String): Boolean = try {
        val m = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
        m.isAccessible = true
        (m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process).waitFor() == 0
    } catch (_: Throwable) { false }

    fun grant(c: Context): Int {
        val p = c.packageName
        return listOf(
            "appops set $p SYSTEM_ALERT_WINDOW allow",
            "pm grant $p android.permission.POST_NOTIFICATIONS",
            "pm grant $p android.permission.RECORD_AUDIO",
            "cmd notification allow_listener $p/${p}.Listener",
            "dumpsys deviceidle whitelist +$p"
        ).count { sh(it) }
    }

    fun shizukuAct(c: Context) {
        when {
            !shizukuUp() -> {
                val i = c.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app"))
                c.startActivity(i)
            }
            !shizukuOk() -> try { Shizuku.requestPermission(100) } catch (_: Throwable) {}
            else -> grant(c)
        }
    }
}
