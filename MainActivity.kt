package com.example.gifoverlay

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import pl.droidsonroids.gif.GifImageView

class MainActivity : Activity() {
    private lateinit var p: SharedPreferences
    private lateinit var list: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        p = getSharedPreferences("p", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(16))
        }
        root.addView(TextView(this).apply { text = "Animasi Layar Beranda"; textSize = 22f })
        root.addView(Button(this).apply {
            text = "+ Tambah animasi dari penyimpanan HP"
            setOnClickListener { pick() }
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)

        slider(root, "Ukuran", 80, 400, p.getInt("size", 150), { "$it dp" }) { p.edit().putInt("size", it).apply() }
        slider(root, "Kecepatan", 2, 30, p.getInt("speed", 10), { "${it / 10f}x" }) { p.edit().putInt("speed", it).apply() }
        root.addView(TextView(this).apply {
            text = "Tip: geser animasi di beranda untuk memindahkan posisinya."
            setPadding(0, dp(16), 0, 0)
        })
        setContentView(ScrollView(this).apply { addView(root) })
        render()
    }

    private fun slider(root: LinearLayout, title: String, min: Int, max: Int, value: Int,
                       fmt: (Int) -> String, save: (Int) -> Unit) {
        val label = TextView(this).apply { text = "$title: ${fmt(value)}"; setPadding(0, dp(20), 0, 0) }
        val bar = SeekBar(this).apply {
            this.max = max - min; progress = value - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, pr: Int, user: Boolean) {
                    label.text = "$title: ${fmt(pr + min)}"; save(pr + min)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    if (p.getString("active", null) != null) svc("START")  // terapkan langsung
                }
            })
        }
        root.addView(label); root.addView(bar)
    }

    private fun pick() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/gif"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, 1)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req != 1 || res != RESULT_OK || data == null) return
        val uris = mutableListOf<Uri>()
        val clip = data.clipData
        if (clip != null) for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
        else data.data?.let { uris.add(it) }

        val arr = JSONArray(p.getString("gifs", "[]"))
        for (u in uris) {
            try { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            var exists = false
            for (i in 0 until arr.length()) if (arr.getJSONObject(i).getString("uri") == u.toString()) exists = true
            if (!exists) arr.put(JSONObject().put("uri", u.toString()).put("name", nameOf(u)))
        }
        p.edit().putString("gifs", arr.toString()).apply()
        render()
    }

    private fun nameOf(u: Uri): String {
        contentResolver.query(u, null, null, null, null)?.use {
            if (it.moveToFirst()) {
                val i = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) return it.getString(i)
            }
        }
        return u.lastPathSegment ?: "animasi.gif"
    }

    private fun render() {
        list.removeAllViews()
        val arr = JSONArray(p.getString("gifs", "[]"))
        val active = p.getString("active", null)
        if (arr.length() == 0) list.addView(TextView(this).apply {
            text = "Belum ada animasi."; setPadding(0, dp(12), 0, dp(12))
        })
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val uri = o.getString("uri"); val name = o.getString("name")
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(8))
                setOnClickListener { menu(i, uri, name) }
            }
            row.addView(GifImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
                scaleType = ImageView.ScaleType.CENTER_CROP
                try { setImageURI(Uri.parse(uri)) } catch (_: Exception) {}
            })
            row.addView(TextView(this).apply {
                text = name; setPadding(dp(12), 0, dp(12), 0)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            row.addView(TextView(this).apply {
                text = if (uri == active) "AKTIF" else "Nonaktif"
                setTextColor(if (uri == active) 0xFF4CAF50.toInt() else 0xFF9E9E9E.toInt())
            })
            list.addView(row)
        }
    }

    private fun menu(index: Int, uri: String, name: String) {
        AlertDialog.Builder(this).setTitle(name)
            .setItems(arrayOf("Aktif", "Nonaktif", "Hapus")) { _, w ->
                when (w) {
                    0 -> activate(uri)
                    1 -> if (p.getString("active", null) == uri) deactivate()
                    2 -> delete(index, uri)
                }
            }.show()
    }

    private fun activate(uri: String) {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            Toast.makeText(this, "Izinkan \"Tampil di atas aplikasi lain\", lalu pilih Aktif lagi", Toast.LENGTH_LONG).show()
            return
        }
        p.edit().putString("active", uri).apply()
        svc("START"); render()
        Toast.makeText(this, "Aktif. Tekan Home untuk melihatnya.", Toast.LENGTH_SHORT).show()
    }

    private fun deactivate() {
        p.edit().remove("active").apply()
        svc("STOP"); render()
    }

    private fun delete(index: Int, uri: String) {
        if (p.getString("active", null) == uri) deactivate()
        val arr = JSONArray(p.getString("gifs", "[]"))
        arr.remove(index)
        p.edit().putString("gifs", arr.toString()).apply()
        render()
    }

    private fun svc(action: String) {
        val i = Intent(this, OverlayService::class.java).setAction(action)
        if (action == "START") startForegroundService(i) else startService(i)
    }
}
