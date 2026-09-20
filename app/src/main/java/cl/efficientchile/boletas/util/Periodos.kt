package cl.efficientchile.boletas.util

import cl.efficientchile.boletas.data.Documento

/**
 * Agrupa los documentos por dia, semana y mes.
 *
 * Las fechas se manejan como numero de dias desde 1970-01-01, con aritmetica
 * entera, y no con Calendar ni con la zona horaria del telefono. El motivo es
 * concreto: un documento guardado con fecha "2026-01-01" tiene que caer en
 * enero aunque el telefono este en otro huso, y Calendar mete la hora del
 * dispositivo en el medio. Aca no hay hora: una fecha es una fecha.
 *
 * El algoritmo de dias<->fecha se probo contra la libreria de fechas de
 * Python en los 4.383 dias entre 2020 y 2031, sin una sola diferencia,
 * incluidos los 29 de febrero y los cambios de anio, que es donde este tipo
 * de cuenta se rompe.
 */
object Periodos {

    enum class Tramo { DIA, SEMANA, MES }

    /**
     * Un periodo con lo que se gasto en el.
     *
     * `documentos` viaja completo porque la pantalla deja abrir el periodo y
     * ver que boletas lo componen: un total que no se puede desarmar no sirve
     * para revisar nada.
     */
    data class Grupo(
        val clave: String,          // ordenable: "2026-09-08", "2026-W2026-09-07", "2026-09"
        val titulo: String,         // "Martes 8 de septiembre"
        val subtitulo: String,      // "3 documentos"
        val total: Int,
        val documentos: List<Documento>,
    )

    private val MESES = arrayOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
    private val MES_CORTO = arrayOf(
        "ene", "feb", "mar", "abr", "may", "jun",
        "jul", "ago", "sep", "oct", "nov", "dic")
    private val DIAS = arrayOf(
        "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

    /** Dias desde 1970-01-01. Algoritmo de calendario proleptico gregoriano. */
    fun dias(y: Int, m: Int, d: Int): Long {
        val yy = if (m <= 2) y - 1 else y
        val era = (if (yy >= 0) yy else yy - 399) / 400
        val yoe = yy - era * 400
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
        val doe = yoe * 365L + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }

    /** El inverso: de numero de dia a (anio, mes, dia). */
    fun civil(z0: Long): Triple<Int, Int, Int> {
        val z = z0 + 719468L
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        return Triple((if (m <= 2) y + 1 else y).toInt(), m.toInt(), d.toInt())
    }

    /** El lunes de la semana de ese dia. El dia 0 (1970-01-01) fue jueves. */
    private fun lunes(z: Long): Long = z - ((z + 3).mod(7L))

    private fun aNumero(fecha: String): Long? {
        // "2026-09-08". Lo que no tenga esta forma no se puede agrupar.
        if (fecha.length < 10) return null
        val y = fecha.substring(0, 4).toIntOrNull() ?: return null
        val m = fecha.substring(5, 7).toIntOrNull() ?: return null
        val d = fecha.substring(8, 10).toIntOrNull() ?: return null
        if (m !in 1..12 || d !in 1..31) return null
        return dias(y, m, d)
    }

    private fun texto(y: Int, m: Int, d: Int) =
        "%04d-%02d-%02d".format(y, m, d)

    /**
     * Agrupa y devuelve los periodos del mas nuevo al mas viejo.
     *
     * Un documento sin fecha legible no se descarta: se junta en un grupo
     * aparte al final. Tiene plata adentro; esconderlo haria que el total de
     * la pantalla no calce con lo que el usuario escaneo, y ese descalce es
     * justo lo que destruye la confianza en un resumen.
     */
    fun agrupar(docs: List<Documento>, tramo: Tramo): List<Grupo> {
        val porClave = LinkedHashMap<String, MutableList<Documento>>()
        val sinFecha = mutableListOf<Documento>()

        docs.forEach { d ->
            val z = aNumero(d.fecha)
            if (z == null) { sinFecha.add(d); return@forEach }
            val (y, m, dd) = civil(z)
            val clave = when (tramo) {
                Tramo.DIA -> texto(y, m, dd)
                Tramo.SEMANA -> {
                    val (ly, lm, ld) = civil(lunes(z))
                    texto(ly, lm, ld)
                }
                Tramo.MES -> "%04d-%02d".format(y, m)
            }
            porClave.getOrPut(clave) { mutableListOf() }.add(d)
        }

        val grupos = porClave.entries
            .sortedByDescending { it.key }
            .map { (clave, lista) ->
                Grupo(
                    clave = clave,
                    titulo = titulo(clave, tramo),
                    subtitulo = if (lista.size == 1) "1 documento" else "${lista.size} documentos",
                    total = lista.sumOf { it.total ?: 0 },
                    documentos = lista,
                )
            }
            .toMutableList()

        if (sinFecha.isNotEmpty()) {
            grupos.add(
                Grupo(
                    clave = "",
                    titulo = "Sin fecha legible",
                    subtitulo = "${sinFecha.size} documentos · ponles la fecha para que entren al resumen",
                    total = sinFecha.sumOf { it.total ?: 0 },
                    documentos = sinFecha,
                )
            )
        }
        return grupos
    }

    private fun titulo(clave: String, tramo: Tramo): String = when (tramo) {
        Tramo.MES -> {
            val y = clave.substring(0, 4).toInt()
            val m = clave.substring(5, 7).toInt()
            "${MESES[m - 1].replaceFirstChar { it.uppercase() }} $y"
        }
        Tramo.DIA -> {
            val z = aNumero(clave)!!
            val (y, m, d) = civil(z)
            "${DIAS[((z + 3).mod(7L)).toInt()]} $d de ${MESES[m - 1]}"
        }
        Tramo.SEMANA -> {
            val z = aNumero(clave)!!
            val (_, m1, d1) = civil(z)
            val (_, m2, d2) = civil(z + 6)
            if (m1 == m2) "$d1 al $d2 de ${MESES[m1 - 1]}"
            else "$d1 ${MES_CORTO[m1 - 1]} al $d2 ${MES_CORTO[m2 - 1]}"
        }
    }

    /**
     * Lo mas comprado dentro de un conjunto de documentos, por plata gastada.
     *
     * Junta por descripcion normalizada —mayusculas y sin espacios de mas—
     * porque el mismo producto sale escrito distinto entre una boleta y otra,
     * y dos filas separadas para lo mismo no le dicen nada a nadie.
     */
    data class Gasto(val descripcion: String, val veces: Int, val cantidad: Int, val monto: Int)

    fun masComprado(docs: List<Documento>, tope: Int = 8): List<Gasto> {
        val acumulado = LinkedHashMap<String, Gasto>()
        docs.forEach { d ->
            d.items.forEach { it ->
                val llave = it.descripcion.uppercase().replace(Regex("\\s+"), " ").trim()
                if (llave.isEmpty()) return@forEach
                val previo = acumulado[llave]
                acumulado[llave] = if (previo == null) {
                    Gasto(llave, 1, it.cantidad, Math.round(it.valorTotal).toInt())
                } else {
                    Gasto(llave, previo.veces + 1, previo.cantidad + it.cantidad,
                        previo.monto + Math.round(it.valorTotal).toInt())
                }
            }
        }
        return acumulado.values.sortedByDescending { it.monto }.take(tope)
    }
}
