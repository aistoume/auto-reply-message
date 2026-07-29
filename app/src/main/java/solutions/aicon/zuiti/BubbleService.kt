package solutions.aicon.zuiti

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * 悬浮球 —— 嘴替的全部入口。
 *
 * 点一下：截当前聊天 → 情绪轮盘 → 选档 → 出回复卡。
 * 拖动可以挪位置，长按收起。截屏走 MediaProjection，系统每次会话要一次
 * 授权；这个 app 不读通知、不装无障碍、不替机主按发送。
 */
class BubbleService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val ACTION_STOP = "solutions.aicon.zuiti.STOP"
        const val ACTION_GRANT_AND_CAPTURE = "solutions.aicon.zuiti.GRANT_AND_CAPTURE"
        private const val CHANNEL_ID = "zuiti_bubble"
        private const val NOTIF_ID = 2001

        @Volatile
        var isRunning = false
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private var bubble: View? = null
    private var wheel: ToneWheelView? = null
    private var projection: MediaProjection? = null
    private var capture: ScreenCaptureManager? = null

    /** 最近一次截图，换档位重出时不必重截。 */
    private var lastShotB64: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startAsForeground(withProjection = intent?.action == ACTION_GRANT_AND_CAPTURE)
        if (bubble == null) addBubble()
        isRunning = true

        if (intent?.action == ACTION_GRANT_AND_CAPTURE) {
            val code = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            @Suppress("DEPRECATION")
            val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
            if (data != null) {
                val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                projection = mgr.getMediaProjection(code, data)?.also { p ->
                    // API 34+：必须先注册回调再建 VirtualDisplay
                    p.registerCallback(object : MediaProjection.Callback() {
                        override fun onStop() { detachProjection() }
                    }, Handler(Looper.getMainLooper()))
                    capture = ScreenCaptureManager(this, p)
                }
                if (capture != null) startFlow()
            }
        }
        return START_STICKY
    }

    private fun detachProjection() {
        capture?.release(); capture = null
        projection = null
    }

    private fun startAsForeground(withProjection: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        val stopPi = android.app.PendingIntent.getService(
            this, 0, Intent(this, BubbleService::class.java).setAction(ACTION_STOP),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .addAction(Notification.Action.Builder(null, getString(R.string.notif_stop), stopPi).build())
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val type = if (withProjection)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            else ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            startForeground(NOTIF_ID, notif, type)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    // ── 悬浮球 ─────────────────────────────────────────────────────────

    private fun addBubble() {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val ball = TextView(this).apply {
            text = "💬"
            textSize = 24f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(24, 24, 27))
                setStroke(dp(2), Color.rgb(245, 158, 11))
            }
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            dp(56), dp(56), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - dp(72)
            y = resources.displayMetrics.heightPixels / 2
        }

        var startX = 0; var startY = 0
        var touchX = 0f; var touchY = 0f; var moved = false
        ball.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y
                    touchX = e.rawX; touchY = e.rawY; moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX).toInt(); val dy = (e.rawY - touchY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    lp.x = startX + dx; lp.y = startY + dy
                    runCatching { windowManager.updateViewLayout(ball, lp) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onBubbleTap()
                    true
                }
                else -> false
            }
        }
        bubble = ball
        windowManager.addView(ball, lp)
    }

    private fun onBubbleTap() {
        if (capture == null) {
            // 本次会话还没有截屏授权 —— 弹一次系统授权，回来继续
            startActivity(
                Intent(this, ProjectionConsentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        startFlow()
    }

    /** 截屏 → 情绪轮盘。 */
    private fun startFlow() {
        val cap = capture ?: return
        ReplyCard.dismiss(windowManager)
        bubble?.visibility = View.INVISIBLE
        cap.captureOnce { bmp ->
            bubble?.visibility = View.VISIBLE
            if (bmp == null) {
                detachProjection()
                toast(getString(R.string.projection_expired))
                return@captureOnce
            }
            scope.launch {
                lastShotB64 = withContext(Dispatchers.IO) {
                    BitmapIO.toBase64Png(BitmapIO.downscale(bmp, 1280)).also { bmp.recycle() }
                }
                openWheel()
            }
        }
    }

    private fun openWheel() {
        if (wheel != null) return
        val b = bubble ?: return
        val loc = IntArray(2); b.getLocationOnScreen(loc)
        val v = ToneWheelView(
            this, loc[0] + b.width / 2f, loc[1] + b.height / 2f,
            ToneConfig.load(this),
            onSelect = { tone -> closeWheel(); runTone(tone) },
            onDismiss = { closeWheel() },
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        wheel = v
        runCatching { windowManager.addView(v, lp) }
    }

    private fun closeWheel() {
        wheel?.let { runCatching { windowManager.removeView(it) } }
        wheel = null
    }

    // ── 出稿 ───────────────────────────────────────────────────────────

    private fun runTone(tone: Tone) {
        val b64 = lastShotB64 ?: return
        if (Prefs.activeKey(this).isBlank()) {
            toast(getString(R.string.need_key)); return
        }
        Prefs.setLastTone(this, tone.id)
        ReplyCard.showStatus(this, windowManager, getString(R.string.card_thinking, tone.name))
        scope.launch {
            val draft = withContext(Dispatchers.IO) { ZuitiEngine.draft(this@BubbleService, b64, tone) }
            if (draft == null) {
                ReplyCard.showStatus(this@BubbleService, windowManager, getString(R.string.card_failed))
                return@launch
            }
            ReplyCard.show(
                this@BubbleService, windowManager, draft, tone, null,
                ToneConfig.load(this@BubbleService),
                onTone = { t -> runTone(t) },              // 换档即时重出
                onInsert = { text -> insert(text) },
            )
        }
    }

    /** 通过嘴替键盘把正文填进当前输入框；发送键仍由机主自己按。 */
    private fun insert(text: String) {
        val kbd = ZuitiKeyboardService.instance
        if (kbd == null) {
            toast(getString(R.string.need_keyboard))
            runCatching {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showInputMethodPicker()
            }
            return
        }
        if (kbd.commit(text)) {
            ReplyCard.dismiss(windowManager)
            toast(getString(R.string.inserted))
        } else {
            toast(getString(R.string.no_input_field))
        }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        isRunning = false
        closeWheel()
        ReplyCard.dismiss(windowManager)
        bubble?.let { runCatching { windowManager.removeView(it) } }
        capture?.release(); projection?.stop()
        scope.cancel()
        super.onDestroy()
    }
}
