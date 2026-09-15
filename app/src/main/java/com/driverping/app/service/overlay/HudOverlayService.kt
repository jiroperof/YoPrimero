package com.driverping.app.service.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.driverping.app.util.Constants
import com.driverping.app.util.NotificationHelper
import com.driverping.app.util.WakeLockHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * HudOverlayService
 * -----------------
 *  - Ventana flotante (System Alert Window / Overlay) discreta que muestra
 *    el ping real en milisegundos hacia una IP de prueba (1.1.1.1).
 *  - Medición de "ping": se lanza una consulta DNS a 1.1.1.1:53 (UDP) y se
 *    cronometra la ida+vuelta. Es la forma más fiable de medir latencia sin
 *    permisos root (el ICMP nativo requiere CAP_NET_RAW).
 *  - Mantiene un WakeLock para evitar que la pantalla se apague y el
 *    procesador entre en Doze mientras el Modo Trabajo esté activo.
 *  - El overlay se puede arrastrar por la pantalla.
 */
class HudOverlayService : Service() {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var pingTextView: TextView? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var pingJob: Job? = null
    private val running = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running.set(true)
        WakeLockHelper.acquire(this)
        addOverlay()
        startPingLoop()
        startForegroundCompat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Si ya corriendo no lo reiniciamos
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        running.set(false)
        pingJob?.cancel()
        removeOverlay()
        WakeLockHelper.release()
        scope.cancel()
    }

    // ---------- Overlay ----------

    private fun addOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 140
        }

        val view = TextView(this).apply {
            setBackgroundColor(0xCC0B0B0D.toInt())
            setTextColor(0xFF00E676.toInt())
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(20, 12, 20, 12)
            text = "YoPrimero · Conectando..."
        }
        overlayView = view
        pingTextView = view
        windowManager?.addView(view, params)
        setupDrag(view, params)
    }

    private fun setupDrag(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var dragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Umbral para distinguir tap de arrastre
                    if (Math.abs(event.rawX - initialTouchX) > 10 ||
                        Math.abs(event.rawY - initialTouchY) > 10
                    ) {
                        dragging = true
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Si no fue arrastre y fue un tap, mostramos estado
                    if (!dragging) {
                        val currentState = pingTextView?.text?.toString() ?: ""
                        pingTextView?.text = "YoPrimero Activo 🚀"
                        mainHandler.postDelayed({ pingTextView?.text = currentState }, 2500)
                    }
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        overlayView = null
    }

    // ---------- Medición de ping ----------

    private fun startPingLoop() {
        pingJob = scope.launch {
            while (running.get()) {
                val ping = measurePing()
                val (statusText, textColor) = when {
                    ping in 0..50 -> "Excelente 🟢" to 0xFF00E676.toInt()
                    ping in 51..120 -> "Bueno 🟡" to 0xFFFFD600.toInt()
                    ping > 120 -> "Lento 🔴" to 0xFFFF5252.toInt()
                    else -> "Buscando red..." to 0xFF999999.toInt()
                }
                val text = "YoPrimero · $statusText"
                mainHandler.post {
                    pingTextView?.text = text
                    pingTextView?.setTextColor(textColor)
                }
                delay(Constants.PING_INTERVAL_MS)
            }
        }
    }

    /**
     * Mide la latencia hacia 1.1.1.1 enviando una consulta DNS (UDP) y
     * cronometrando la respuesta. Devuelve ms, o -1 si falla.
     */
    private fun measurePing(): Long {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            val timeout = 2000
            socket.soTimeout = timeout
            // Consulta DNS mínima para el root "." (opcode QUERY, RD)
            val query = byteArrayOf(
                0x12, 0x34,          // ID
                0x01, 0x00,          // flags: RD
                0x00, 0x01,          // QDCOUNT
                0x00, 0x00,          // ANCOUNT
                0x00, 0x00,          // NSCOUNT
                0x00, 0x00,          // ARCOUNT
                0x00,                // QNAME (root)
                0x00, 0x01,          // QTYPE: A
                0x00, 0x01           // QCLASS: IN
            )
            val address = InetAddress.getByName(Constants.PING_HOST)
            val packet = DatagramPacket(query, query.size, address, 53)
            val start = System.nanoTime()
            socket.send(packet)
            val responseBuffer = ByteArray(512)
            socket.receive(DatagramPacket(responseBuffer, responseBuffer.size))
            (System.nanoTime() - start) / 1_000_000L
        } catch (_: Exception) {
            -1L
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    // ---------- Foreground ----------

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationHelper.ensureNotificationChannel(
                this, NotificationHelper.CHANNEL_HUD,
                "HUD de Ping", android.app.NotificationManager.IMPORTANCE_LOW
            )
        }
        val notif = NotificationHelper.buildForegroundNotification(
            this,
            NotificationHelper.CHANNEL_HUD,
            "YoPrimero en activo",
            "HUD de ping activo · wake lock mantenido"
        )
        startForeground(NotificationHelper.NOTIF_HUD_ID, notif)
    }
}
