package cz.padstudio

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.activity.ComponentActivity
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var editor: EditorView
    private lateinit var panel: LinearLayout
    private lateinit var root: FrameLayout
    private var retro: GLRetroView? = null
    private var currentRom: File? = null
    private var n64 = true
    private val pickImage = 1
    private val pickGame = 2
    private val actions = arrayOf(
        "A", "B", "X", "Y", "L", "R", "Z", "START", "SELECT",
        "UP", "DOWN", "LEFT", "RIGHT", "C_UP", "C_DOWN", "C_LEFT", "C_RIGHT", "STICK", "CSTICK"
    )

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        editor = EditorView(this, LayoutStore(this))
        val d = resources.displayMetrics.density
        root = FrameLayout(this)
        root.addView(editor, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        fun btn(t: String, f: (Button) -> Unit) = Button(this).apply {
            text = t; textSize = 12f; setOnClickListener { f(this) }
        }

        val bar = LinearLayout(this).apply { setBackgroundColor(0xAA000000.toInt()) }
        bar.addView(btn("Vybrat hru") {
            if (retro != null) toast("Nejdřív ukonči hru")
            else startActivityForResult(
                Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), pickGame)
        })
        bar.addView(btn("+ Tlačítko") {
            AlertDialog.Builder(this).setItems(actions) { _, i -> editor.addButton(actions[i]) }.show()
        })
        bar.addView(btn("Obrázek") {
            if (editor.selected == null) toast("Nejdřív ťukni na tlačítko")
            else startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), pickImage)
        })
        bar.addView(btn("Smazat") { editor.deleteSelected() })
        bar.addView(btn("Zrcadlit") { editor.mirror() })
        bar.addView(btn("Hrát") { b ->
            editor.editMode = !editor.editMode
            b.text = if (editor.editMode) "Hrát" else "Upravit"
            panel.visibility = View.GONE
        })
        bar.addView(btn("Zástupce") { pinShortcut() })
        bar.addView(btn("Ukončit hru") { recreate() })
        val scroll = HorizontalScrollView(this).apply { addView(bar) }
        root.addView(scroll, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xAA000000.toInt()); setPadding(16, 8, 16, 8)
            visibility = View.GONE
        }
        fun label(t: String) = TextView(this).apply { text = t; setTextColor(Color.WHITE); textSize = 12f }
        fun slider(f: (Int) -> Unit) = SeekBar(this).apply {
            max = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) { if (fromUser) { f(p); editor.invalidate() } }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) { editor.save() }
            })
        }
        val sizeBar = slider { p -> editor.selected?.size = 0.05f + p / 100f * 0.25f }
        val alphaBar = slider { p -> editor.selected?.alpha = 0.1f + p / 100f * 0.9f }
        panel.addView(label("Velikost")); panel.addView(sizeBar)
        panel.addView(label("Průhlednost")); panel.addView(alphaBar)
        root.addView(panel, FrameLayout.LayoutParams((280 * d).toInt(), WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))

        editor.onSelectionChanged = { b ->
            if (b != null && editor.editMode) {
                sizeBar.progress = ((b.size - 0.05f) / 0.25f * 100).toInt().coerceIn(0, 100)
                alphaBar.progress = ((b.alpha - 0.1f) / 0.9f * 100).toInt().coerceIn(0, 100)
                panel.visibility = View.VISIBLE
            } else panel.visibility = View.GONE
        }
        editor.onKey = { a, down -> handleKey(a, down) }
        editor.onAxis = { side, x, y ->
            retro?.sendMotionEvent(
                if (side == 0) GLRetroView.MOTION_SOURCE_ANALOG_LEFT else GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, x, y)
        }
        setContentView(root)

        intent.getStringExtra("rom")?.let { p ->
            File(p).takeIf { it.exists() }?.let { startGame(it) }
        }
    }

    override fun onPause() { super.onPause(); editor.save() }

    private fun keyFor(a: String): Int? = when (a) {
        "A" -> if (n64) KeyEvent.KEYCODE_BUTTON_B else KeyEvent.KEYCODE_BUTTON_A
        "B" -> if (n64) KeyEvent.KEYCODE_BUTTON_Y else KeyEvent.KEYCODE_BUTTON_B
        "X" -> KeyEvent.KEYCODE_BUTTON_X
        "Y" -> KeyEvent.KEYCODE_BUTTON_Y
        "L" -> KeyEvent.KEYCODE_BUTTON_L1
        "R" -> KeyEvent.KEYCODE_BUTTON_R1
        "Z" -> KeyEvent.KEYCODE_BUTTON_L2
        "START" -> KeyEvent.KEYCODE_BUTTON_START
        "SELECT" -> KeyEvent.KEYCODE_BUTTON_SELECT
        "UP" -> KeyEvent.KEYCODE_DPAD_UP
        "DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
        "LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
        "RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
        else -> null
    }

    private fun handleKey(a: String, down: Boolean) {
        val r = retro ?: return
        val v = if (down) 1f else 0f
        val right = GLRetroView.MOTION_SOURCE_ANALOG_RIGHT
        when (a) {
            "C_UP" -> r.sendMotionEvent(right, 0f, -v)
            "C_DOWN" -> r.sendMotionEvent(right, 0f, v)
            "C_LEFT" -> r.sendMotionEvent(right, -v, 0f)
            "C_RIGHT" -> r.sendMotionEvent(right, v, 0f)
            else -> keyFor(a)?.let { r.sendKeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, it) }
        }
    }

    private fun startGame(rom: File) {
        val ext = rom.extension.lowercase()
        n64 = ext in listOf("z64", "n64", "v64")
        if (!n64 && ext != "nds") { toast("Podporuji jen .z64 .n64 .v64 a .nds"); return }
        val prefixes = if (n64) listOf("libmupen64plus") else listOf("libmelonds", "libdesmume")
        val libs = File(applicationInfo.nativeLibraryDir).listFiles()?.map { it.name } ?: emptyList()
        val core = libs.firstOrNull { n -> prefixes.any { n.startsWith(it) } }
        if (core == null) { toast("Jádro pro .$ext v appce chybí"); return }
        val data = GLRetroViewData(this).apply {
            coreFilePath = core
            gameFilePath = rom.absolutePath
            systemDirectory = File(filesDir, "system").apply { mkdirs() }.absolutePath
            savesDirectory = File(filesDir, "saves").apply { mkdirs() }.absolutePath
        }
        val v = GLRetroView(this, data)
        lifecycle.addObserver(v)
        root.addView(v, 0, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        retro = v
        currentRom = rom
        editor.switchLayout(LayoutStore(this, rom.nameWithoutExtension.replace(Regex("[^A-Za-z0-9_-]"), "_")))
        editor.gameRunning = true
    }

    private fun pinShortcut() {
        val rom = currentRom ?: return toast("Nejdřív spusť hru")
        if (Build.VERSION.SDK_INT < 26) return toast("Vyžaduje Android 8 a novější")
        val sm = getSystemService(ShortcutManager::class.java)
        if (!sm.isRequestPinShortcutSupported) return toast("Launcher zástupce nepodporuje")
        val i = Intent(this, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra("rom", rom.absolutePath)
        val info = ShortcutInfo.Builder(this, "game_" + rom.nameWithoutExtension)
            .setShortLabel(rom.nameWithoutExtension.take(20))
            .setIcon(Icon.createWithResource(this, android.R.drawable.ic_media_play))
            .setIntent(i).build()
        sm.requestPinShortcut(info, null)
    }

    private fun queryName(uri: Uri): String? =
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (res != RESULT_OK) return
        val uri = data?.data ?: return
        if (req == pickImage) {
            val b = editor.selected ?: return
            val bmp = decode(uri) ?: return toast("Obrázek se nepodařilo načíst")
            val name = "${b.id}_${System.currentTimeMillis()}.png"
            File(editor.store.imageDir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            editor.setImage(b, name)
        } else if (req == pickGame) {
            toast("Načítám hru…")
            Thread {
                try {
                    val name = (queryName(uri) ?: "hra.z64").replace("/", "_")
                    val f = File(File(filesDir, "roms").apply { mkdirs() }, name)
                    contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                    runOnUiThread { startGame(f) }
                } catch (e: Exception) {
                    runOnUiThread { toast("Chyba: ${e.message}") }
                }
            }.start()
        }
    }

    private fun decode(uri: Uri): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (o.outWidth / s > 512 || o.outHeight / s > 512) s *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = s }
        return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
