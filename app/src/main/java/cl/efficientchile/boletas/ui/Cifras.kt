package cl.efficientchile.boletas.ui

/**
 * 12345 -> "$12.345". Puntos de miles, sin decimales, como el peso chileno.
 *
 * Vive aparte y no dentro de una pantalla porque lo usan varias, y una cifra
 * escrita de dos formas distintas en dos pantallas de la misma app se lee
 * como un error aunque el numero sea el mismo.
 */
internal fun clp(n: Int): String {
    val negativo = n < 0
    val s = kotlin.math.abs(n).toString()
    val sb = StringBuilder()
    for ((i, c) in s.withIndex()) {
        if (i > 0 && (s.length - i) % 3 == 0) sb.append('.')
        sb.append(c)
    }
    return if (negativo) "-$$sb" else "$$sb"
}
