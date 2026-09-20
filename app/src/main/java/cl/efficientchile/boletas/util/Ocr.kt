package cl.efficientchile.boletas.util

import com.google.mlkit.vision.text.Text
import kotlin.math.abs
import kotlin.math.max

/**
 * Reconstruye las lineas del papel a partir de donde esta cada cosa.
 *
 * ## Por que existe este archivo
 *
 * ML Kit entrega el texto agrupado en BLOQUES, y `Text.text` los concatena en
 * el orden en que los armo. En un documento de dos columnas —la etiqueta a la
 * izquierda, el monto a la derecha— ese orden no sirve: ML Kit puede entregar
 * la columna entera de etiquetas y despues la columna entera de montos, y
 * entonces "TOTAL AFECTO" y "9.487" quedan a diez lineas de distancia.
 *
 * Pero cada linea trae su rectangulo. Con eso se puede rehacer el papel como
 * esta impreso: se juntan las que estan a la misma altura y se ordenan de
 * izquierda a derecha. "TOTAL AFECTO 9.487" vuelve a ser una sola linea,
 * sin importar como ML Kit haya cortado sus bloques.
 *
 * Esto reemplaza adivinar con expresiones regulares algo que venia resuelto
 * en datos que antes se botaban.
 *
 * ## El margen
 *
 * Dos lineas son la misma si sus centros verticales estan a menos del 60% de
 * la altura de una letra. Se usa la altura MEDIANA y no el promedio, porque
 * el nombre del comercio suele ir en letra grande y el promedio se dispara;
 * la mediana la ignora. Probado contra lineas muy apretadas (que no se deben
 * fusionar) y contra una foto torcida (donde si se deben).
 */
object Ocr {

    private data class Caja(
        val texto: String,
        val izquierda: Int,
        val arriba: Int,
        val abajo: Int,
    ) {
        val centro: Double get() = (arriba + abajo) / 2.0
    }

    fun lineas(t: Text): List<String> {
        val cajas = mutableListOf<Caja>()
        for (bloque in t.textBlocks) {
            for (linea in bloque.lines) {
                val r = linea.boundingBox ?: continue
                if (linea.text.isBlank()) continue
                cajas.add(Caja(linea.text, r.left, r.top, r.bottom))
            }
        }
        // Sin rectangulos no hay nada que reconstruir: se devuelve el texto
        // plano, que es lo que se usaba antes. Peor, pero no vacio.
        if (cajas.isEmpty()) return t.text.lines()

        val alturas = cajas.map { it.abajo - it.arriba }.sorted()
        val alto = alturas[alturas.size / 2]
        val margen = max(1.0, alto * 0.6)

        val porAltura = cajas.sortedBy { it.centro }
        val filas = mutableListOf(mutableListOf(porAltura[0]))
        for (c in porAltura.drop(1)) {
            val fila = filas.last()
            val centroFila = fila.sumOf { it.centro } / fila.size
            if (abs(c.centro - centroFila) <= margen) fila.add(c)
            else filas.add(mutableListOf(c))
        }

        return filas.map { fila ->
            fila.sortedBy { it.izquierda }.joinToString(" ") { it.texto }
        }
    }
}
