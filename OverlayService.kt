package com.example.gifoverlay

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.ImageView
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifImageView

class OverlayService : Service() {
    private var wm: WindowManager? = null
    private var view: GifImageView? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, id: Int): Int {
        if (i?.action == "STOP") {
            removeOverlay()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        goForeground()
        showOverlay()
        return START_STICKY
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("gif", "Animasi beranda", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "gif")
            .setContentTitle("Animasi aktif di layar beranda")
            .setContentText("Ketuk untuk membuka menu")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentIntent(open).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    private fun showOverlay() {
        removeOverlay()
        val p = getSharedPreferences("p", MODE_PRIVATE)
        val uri = p.getString("active", null)
        if (uri == null) { stopSelf(); return }
        val v = GifImageView(this)
        try {
            val d = GifDrawable(contentResolver, Uri.parse(uri))
            d.setSpeed(p.getInt("speed", 10) / 10f)   // 1.0 = normal
            v.setImageDrawable(d)
        } catch (e: Exception) { stopSelf(); return }
        v.adjustViewBounds = true
        v.scaleType = ImageView.ScaleType.FIT_CENTER

        val px = (p.getInt("size", 150) * resources.displayMetrics.density).toInt()
        val lp = WindowManager.LayoutParams(
            px, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = p.getInt("x", 100); y = p.getInt("y", 300)
        }

        // Geser GIF dengan jari untuk memindahkan posisinya
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = sx + (e.rawX - tx).toInt(); lp.y = sy + (e.rawY - ty).toInt()
                    wm?.updateViewLayout(v, lp)
                }
                MotionEvent.ACTION_UP -> p.edit().putInt("x", lp.x).putInt("y", lp.y).apply()
            }
            true
        }
        wm = getSystemService(WindowManager::class.java)
        wm?.addView(v, lp)
        view = v
    }

    private fun removeOverlay() {
        view?.let {
            try { wm?.removeView(it) } catch (_: Exception) {}
            (it.drawable as? GifDrawable)?.recycle()
        }
        view = null
    }

    override fun onDestroy() { removeOverlay(); super.onDestroy() }
}
