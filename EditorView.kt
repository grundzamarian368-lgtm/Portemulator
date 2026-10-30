package cz.padstudio

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

class EditorView(ctx: Context, var store: LayoutStore) : View(ctx) {
    var buttons: MutableList<PadButton> = store.load()
    var selected: PadButton? = null
        private set
    var onSelectionChanged: ((PadButton?) -> Unit)? = null
    /** (akce, stisknuto?) */
    var onKey: ((String, Boolean) -> Unit)? = null
    /** páčka: (0 = levá / 1 = pravá, x, y) v rozsahu -1..1 */
    var onAxis: ((Int, Float, Float) -> Unit)? = null

    private val pointerTargets = HashMap<Int, PadButton>()
    private val bitmaps = HashMap<String, Bitmap>()
    private val stick = HashMap<PadButton, PointF>()
    private val camLast = HashMap<Int, PointF>()
    private val camReset = Runnable { onAxis?.invoke(1, 0f, 0f) }
    /** prázdné místo na obrazovce = kamera (pravá páčka) */
    var cameraTouch = true
    private var dx = 0f
    private var dy = 0f

    var gameRunning = false
        set(v) { field = v; invalidate() }

    var editMode = true
        set(v) { field = v; pointerTargets.clear(); stick.clear(); camLast.clear(); invalidate() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.WHITE }
    private val sel = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = 0xFFFFEB3B.toInt() }
    private val img = Paint(Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }

    private fun isStick(b: PadButton) = b.action == "STICK" || b.action == "CSTICK"
    private fun side(b: PadButton) = if (b.action == "STICK") 0 else 1

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
        if (!gameRunning) {
            c.drawColor(0xFF15181E.toInt())
            text.color = 0x33FFFFFF; text.textSize = h * 0.06f
            c.drawText("Obraz hry", w / 2, h / 2, text)
        }
        for (b in buttons) {
            val cx = b.x * w; val cy = b.y * h; val r = b.size * m / 2
            val down = !editMode && pointerTargets.containsValue(b)
            val a = ((if (down) 1f else b.alpha) * 255).toInt()
            if (isStick(b)) {
                fill.color = 0xFF37474F.toInt(); fill.alpha = (a * 0.6f).toInt()
                c.drawCircle(cx, cy, r, fill)
                ring.alpha = a; c.drawCircle(cx, cy, r, ring)
                val o = stick[b]
                fill.color = if (b.action == "STICK") 0xFFECEFF1.toInt() else 0xFFFFC107.toInt(); fill.alpha = a
                c.drawCircle(cx + (o?.x ?: 0f) * r * 0.55f, cy + (o?.y ?: 0f) * r * 0.55f, r * 0.45f, fill)
            } else {
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
            }
            if (editMode && b === selected) c.drawCircle(cx, cy, r + 8f, sel)
        }
    }

    private fun hit(px: Float, py: Float): PadButton? {
        val m = min(width, height).toFloat()
        return buttons.lastOrNull { hypot(px - it.x * width, py - it.y * height) <= it.size * m / 2 }
    }

    private fun select(b: PadButton?) { selected = b; onSelectionChanged?.invoke(b) }

    private fun updateStick(b: PadButton, px: Float, py: Float) {
        val r = b.size * min(width, height) / 2f
        var nx = (px - b.x * width) / r
        var ny = (py - b.y * height) / r
        val len = hypot(nx, ny)
        if (len > 1f) { nx /= len; ny /= len }
        stick[b] = PointF(nx, ny)
        onAxis?.invoke(side(b), nx, ny)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val b = hit(e.getX(i), e.getY(i))
                if (b != null) {
                    pointerTargets[e.getPointerId(i)] = b
                    if (editMode) { select(b); dx = b.x * width - e.getX(i); dy = b.y * height - e.getY(i) }
                    else if (isStick(b)) updateStick(b, e.getX(i), e.getY(i))
                    else onKey?.invoke(b.action, true)
                } else if (editMode) {
                    if (e.actionMasked == MotionEvent.ACTION_DOWN) select(null)
                } else if (gameRunning && cameraTouch) {
                    camLast[e.getPointerId(i)] = PointF(e.getX(i), e.getY(i))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val cam = camLast[e.getPointerId(i)]
                    if (cam != null) { moveCamera(cam, e.getX(i), e.getY(i)); continue }
                    val b = pointerTargets[e.getPointerId(i)] ?: continue
                    if (editMode) {
                        if (b !== selected) continue
                        b.x = ((e.getX(i) + dx) / width).coerceIn(0.02f, 0.98f)
                        b.y = ((e.getY(i) + dy) / height).coerceIn(0.02f, 0.98f)
                    } else if (isStick(b)) updateStick(b, e.getX(i), e.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> release(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> pointerTargets.keys.toList().forEach { release(it) }
        }
        invalidate()
        return true
    }

    private fun moveCamera(last: PointF, x: Float, y: Float) {
        val dx = x - last.x
        val dy = y - last.y
        last.set(x, y)
        if (abs(dx) < 2f && abs(dy) < 2f) return
        fun amp(d: Float): Float =
            if (abs(d) < 2f) 0f else (if (d > 0) 1f else -1f) * (abs(d) / 25f).coerceIn(0.6f, 1f)
        onAxis?.invoke(1, amp(dx), amp(dy))
        removeCallbacks(camReset)
        postDelayed(camReset, 90)
    }

    private fun release(id: Int) {
        if (camLast.remove(id) != null) { onAxis?.invoke(1, 0f, 0f); return }
        val b = pointerTargets.remove(id) ?: return
        if (editMode) { save(); return }
        if (isStick(b)) { stick.remove(b); onAxis?.invoke(side(b), 0f, 0f) }
        else if (!pointerTargets.containsValue(b)) onKey?.invoke(b.action, false)
    }

    fun save() = store.save(buttons)

    fun switchLayout(s: LayoutStore) {
        save()
        store = s; buttons = s.load()
        pointerTargets.clear(); stick.clear(); bitmaps.clear()
        select(null); invalidate()
    }

    fun addButton(action: String) {
        val b = PadButton(LayoutStore.newId(), action, 0.5f, 0.35f, if (action.endsWith("STICK")) 0.26f else 0.14f)
        buttons.add(b); select(b); save(); invalidate()
    }

    fun deleteSelected() {
        val b = selected ?: return
        buttons.remove(b); select(null); save(); invalidate()
    }

    fun resetLayout() {
        buttons = store.defaultLayout(); select(null); save(); invalidate()
    }

    fun mirror() { buttons.forEach { it.x = 1f - it.x }; save(); invalidate() }

    fun setImage(b: PadButton, name: String) {
        bitmaps.remove(name); b.image = name; save(); invalidate()
    }
}
