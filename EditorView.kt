package cz.padstudio

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import java.io.File
import kotlin.math.hypot
import kotlin.math.min

class EditorView(ctx: Context, private val store: LayoutStore) : View(ctx) {
    var buttons: MutableList<PadButton> = store.load()
    var selected: PadButton? = null
        private set
    var onSelectionChanged: ((PadButton?) -> Unit)? = null
    /** Sem se později napojí emulátor: (akce, stisknuto?) */
    var onKey: ((String, Boolean) -> Unit)? = null

    private val pointerTargets = HashMap<Int, PadButton>()
    private val bitmaps = HashMap<String, Bitmap>()
    private var dx = 0f
    private var dy = 0f

    var editMode = true
        set(v) { field = v; pointerTargets.clear(); invalidate() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.WHITE }
    private val sel = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = 0xFFFFEB3B.toInt() }
    private val img = Paint(Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }

    private fun colorFor(a: String) = when (a) {
        "A" -> 0xFFE53935.toInt(); "B" -> 0xFFFDD835.toInt()
        "X" -> 0xFF1E88E5.toInt(); "Y" -> 0xFF43A047.toInt()
        else -> 0xFF607D8B.toInt()
    }

    private fun label(a: String) = when (a) {
        "UP" -> "▲"; "DOWN" -> "▼"; "LEFT" -> "◀"; "RIGHT" -> "▶"
        "SELECT" -> "SEL"; "C_UP" -> "C▲"; "C_DOWN" -> "C▼"; "C_LEFT" -> "C◀"; "C_RIGHT" -> "C▶"
        else -> a
    }

    private fun bitmapFor(b: PadButton): Bitmap? {
        val n = b.image ?: return null
        return bitmaps[n] ?: BitmapFactory.decodeFile(File(store.imageDir, n).path)?.also { bitmaps[n] = it }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val m = min(w, h)
        c.drawColor(0xFF15181E.toInt())
        text.color = 0x33FFFFFF; text.textSize = h * 0.06f
        c.drawText("Obraz hry", w / 2, h / 2, text)
        for (b in buttons) {
            val cx = b.x * w; val cy = b.y * h; val r = b.size * m / 2
            val down = !editMode && pointerTargets.containsValue(b)
            val a = ((if (down) 1f else b.alpha) * 255).toInt()
            val bmp = bitmapFor(b)
            if (bmp != null) {
                img.alpha = a
                c.drawBitmap(bmp, null, RectF(cx - r, cy - r, cx + r, cy + r), img)
            } else {
                fill.color = colorFor(b.action); fill.alpha = a
                c.drawCircle(cx, cy, r, fill)
                ring.alpha = a; c.drawCircle(cx, cy, r, ring)
                text.color = Color.WHITE; text.alpha = a; text.textSize = r * 0.8f
                c.drawText(label(b.action), cx, cy + r * 0.28f, text)
            }
            if (editMode && b === selected) c.drawCircle(cx, cy, r + 8f, sel)
        }
        if (!editMode) {
            text.color = 0xAAFFFFFF.toInt(); text.textSize = h * 0.04f; text.textAlign = Paint.Align.LEFT
            c.drawText(pointerTargets.values.joinToString(" ") { it.action }, w * 0.02f, h * 0.2f, text)
            text.textAlign = Paint.Align.CENTER
        }
    }

    private fun hit(px: Float, py: Float): PadButton? {
        val m = min(width, height).toFloat()
        return buttons.lastOrNull { hypot(px - it.x * width, py - it.y * height) <= it.size * m / 2 }
    }

    private fun select(b: PadButton?) { selected = b; onSelectionChanged?.invoke(b) }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val b = hit(e.getX(i), e.getY(i))
                if (b != null) {
                    pointerTargets[e.getPointerId(i)] = b
                    if (editMode) { select(b); dx = b.x * width - e.getX(i); dy = b.y * height - e.getY(i) }
                    else onKey?.invoke(b.action, true)
                } else if (editMode && e.actionMasked == MotionEvent.ACTION_DOWN) select(null)
            }
            MotionEvent.ACTION_MOVE -> if (editMode) {
                for (i in 0 until e.pointerCount) {
                    val b = pointerTargets[e.getPointerId(i)] ?: continue
                    if (b !== selected) continue
                    b.x = ((e.getX(i) + dx) / width).coerceIn(0.02f, 0.98f)
                    b.y = ((e.getY(i) + dy) / height).coerceIn(0.02f, 0.98f)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> release(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> pointerTargets.keys.toList().forEach { release(it) }
        }
        invalidate()
        return true
    }

    private fun release(id: Int) {
        val b = pointerTargets.remove(id) ?: return
        if (editMode) save()
        else if (!pointerTargets.containsValue(b)) onKey?.invoke(b.action, false)
    }

    fun save() = store.save(buttons)

    fun addButton(action: String) {
        val b = PadButton(LayoutStore.newId(), action, 0.5f, 0.35f, 0.14f)
        buttons.add(b); select(b); save(); invalidate()
    }

    fun deleteSelected() {
        val b = selected ?: return
        b.image?.let { File(store.imageDir, it).delete(); bitmaps.remove(it) }
        buttons.remove(b); select(null); save(); invalidate()
    }

    fun mirror() { buttons.forEach { it.x = 1f - it.x }; save(); invalidate() }

    fun setImage(b: PadButton, name: String) {
        bitmaps.remove(name); b.image = name; save(); invalidate()
    }
}
