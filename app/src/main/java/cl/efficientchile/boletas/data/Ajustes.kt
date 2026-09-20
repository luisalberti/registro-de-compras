package cl.efficientchile.boletas.data

import android.content.Context

/**
 * Donde vive la direccion de tu planilla.
 *
 * Es un solo dato, asi que va en SharedPreferences y no en un archivo: es
 * justo para lo que sirve.
 *
 * Mientras no haya URL configurada, la app **no toca la red**. Eso es a
 * proposito: alguien que solo quiere registrar boletas en su telefono no
 * tiene por que mandar nada a ninguna parte.
 */
class Ajustes(ctx: Context) {

    private val prefs = ctx.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    var urlPlanilla: String
        get() = prefs.getString("url", "").orEmpty()
        set(v) = prefs.edit().putString("url", v.trim()).apply()

    val haySincronizacion: Boolean get() = urlPlanilla.startsWith("https://")
}
