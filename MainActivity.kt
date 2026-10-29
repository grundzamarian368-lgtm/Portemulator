package cz.padstudio

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import java.io.File

class MainActivity : Activity() {
    private lateinit var store: LayoutStore
    private lateinit var editor: EditorView
    private lateinit var panel: LinearLayout
    private val pick = 1
    private val actions = arrayOf(
        "A", "B", "X", "Y", "L", "R", "Z", "START", "SELECT",
        "UP", "DOWN", "LEFT", "RIGHT", "C_UP", "C_DOWN", "C_LEFT", "C_RIGHT"
    )

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        store = LayoutStore(this)
        editor = EditorView(this, store)
        val d = resources.displayMetrics.density
        val root = FrameLayout(this)
        root.addView(editor, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        fun btn(t: String, f: (Button) -> Unit) = Button(this).apply {
            text = t; textSize = 12f; setOnClickListener { f(this) }
        }

        val bar = LinearLayout(this).apply { setBackgroundColor(0xAA000000.toInt()) }
        bar.addView(btn("+ Tlačítko") {
            AlertDialog.Builder(this).setItems(actions) { _, i -> editor.addButton(actions[i]) }.show()
        })
        bar.addView(btn("Obrázek") {
            if (editor.selected == null) toast("Nejdřív ťukni na tlačítko")
            else startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), pick)
        })
        bar.addView(btn("Smazat") { editor.deleteSelected() })
        bar.addView(btn("Zrcadlit") { editor.mirror() })
        bar.addView(btn("Hrát") { b ->
            editor.editMode = !editor.editMode
            b.text = if (editor.editMode) "Hrát" else "Upravit"
            panel.visibility = android.view.View.GONE
        })
        bar.addView(btn("Export") {
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, store.exportJson()),
                "Export rozložení"))
        })
        val scroll = HorizontalScrollView(this).apply { addView(bar) }
        root.addView(scroll, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xAA000000.toInt()); setPadding(16, 8, 16, 8)
            visibility = android.view.View.GONE
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
                panel.visibility = android.view.View.VISIBLE
            } else panel.visibility = android.view.View.GONE
        }
        setContentView(root)
    }

    override fun onPause() { super.onPause(); editor.save() }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req != pick || res != RESULT_OK) return
        val uri = data?.data ?: return
        val b = editor.selected ?: return
        val bmp = decode(uri) ?: return toast("Obrázek se nepodařilo načíst")
        val name = "${b.id}.png"
        File(store.imageDir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        editor.setImage(b, name)
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
