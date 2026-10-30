package cz.padstudio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Jedno tlačítko. x, y = střed (0..1 šířky/výšky), size = podíl kratší strany. */
class PadButton(
    val id: String,
    var action: String,
    var x: Float,
    var y: Float,
    var size: Float,
    var alpha: Float = 0.7f,
    var image: String? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("action", action)
        .put("x", x.toDouble()).put("y", y.toDouble())
        .put("size", size.toDouble()).put("alpha", alpha.toDouble())
        .put("image", image ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject) = PadButton(
            o.getString("id"), o.getString("action"),
            o.getDouble("x").toFloat(), o.getDouble("y").toFloat(),
            o.getDouble("size").toFloat(), o.getDouble("alpha").toFloat(),
            if (o.isNull("image")) null else o.getString("image")
        )
    }
}

/** Ukládání rozložení (autosave) + vlastní obrázky tlačítek. */
class LayoutStore(ctx: Context, key: String = "default", private val n64: Boolean = false) {
    private val file = if (key == "default") File(ctx.filesDir, "layout.json") else File(ctx.filesDir, "layout_$key.json")
    val imageDir = File(ctx.filesDir, "buttons").apply { mkdirs() }

    fun load(): MutableList<PadButton> {
        if (!file.exists()) return defaultLayout()
        return try {
            val arr = JSONObject(file.readText()).getJSONArray("buttons")
            MutableList(arr.length()) { PadButton.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) { defaultLayout() }
    }

    fun save(list: List<PadButton>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        val tmp = File(file.parentFile, "layout.tmp")
        tmp.writeText(JSONObject().put("version", 1).put("buttons", arr).toString(2))
        tmp.renameTo(file)
    }

    fun exportJson(): String = if (file.exists()) file.readText() else ""

    fun defaultLayout(): MutableList<PadButton> = if (n64) n64Layout() else ndsLayout()

    private fun n64Layout(): MutableList<PadButton> = mutableListOf(
        PadButton(newId(), "STICK", 0.14f, 0.68f, 0.30f),
        PadButton(newId(), "A", 0.91f, 0.70f, 0.15f),
        PadButton(newId(), "B", 0.81f, 0.82f, 0.14f),
        PadButton(newId(), "C_UP", 0.80f, 0.28f, 0.10f),
        PadButton(newId(), "C_DOWN", 0.80f, 0.56f, 0.10f),
        PadButton(newId(), "C_LEFT", 0.72f, 0.42f, 0.10f),
        PadButton(newId(), "C_RIGHT", 0.88f, 0.42f, 0.10f),
        PadButton(newId(), "L", 0.08f, 0.14f, 0.12f),
        PadButton(newId(), "Z", 0.22f, 0.14f, 0.12f),
        PadButton(newId(), "R", 0.92f, 0.14f, 0.12f),
        PadButton(newId(), "START", 0.50f, 0.88f, 0.10f)
    )

    private fun ndsLayout(): MutableList<PadButton> = mutableListOf(
        PadButton(newId(), "A", 0.92f, 0.62f, 0.14f),
        PadButton(newId(), "B", 0.82f, 0.78f, 0.14f),
        PadButton(newId(), "X", 0.82f, 0.46f, 0.14f),
        PadButton(newId(), "Y", 0.72f, 0.62f, 0.14f),
        PadButton(newId(), "UP", 0.12f, 0.45f, 0.13f),
        PadButton(newId(), "DOWN", 0.12f, 0.79f, 0.13f),
        PadButton(newId(), "LEFT", 0.05f, 0.62f, 0.13f),
        PadButton(newId(), "RIGHT", 0.19f, 0.62f, 0.13f),
        PadButton(newId(), "L", 0.08f, 0.14f, 0.12f),
        PadButton(newId(), "R", 0.92f, 0.14f, 0.12f),
        PadButton(newId(), "SELECT", 0.35f, 0.55f, 0.1f),
        PadButton(newId(), "START", 0.65f, 0.55f, 0.1f)
    )

    companion object {
        fun newId(): String = UUID.randomUUID().toString().take(8)
    }
}
