package cl.efficientchile.boletas.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Un documento registrado: una boleta, una factura o un voucher.
 *
 * Guarda lo que el lector saco de la foto y el vendedor confirmo. Es lo que
 * se acumula en la lista y lo que termina siendo una fila del Excel.
 *
 * `avisos` viaja con el documento a proposito: si el lector no pudo cuadrar
 * el total, esa advertencia tiene que llegar hasta el Excel, no perderse al
 * guardar. Una cifra que no cuadra y nadie marco es peor que una en blanco.
 */
data class Documento(
    val id: Long,
    val fecha: String,        // yyyy-MM-dd, o vacio si no se leyo
    val tipo: String,         // Boleta | Factura | Voucher
    val numero: String,
    val rut: String,
    val neto: Int?,
    val iva: Int?,
    val total: Int?,
    val tarjeta: String,      // ultimos 4, o vacio
    val formaPago: String,    // efectivo | tarjeta | transferencia | ""
    val avisos: String,
) {
    fun aJson(): JSONObject = JSONObject().apply {
        put("id", id); put("fecha", fecha); put("tipo", tipo)
        put("numero", numero); put("rut", rut)
        put("neto", neto ?: JSONObject.NULL)
        put("iva", iva ?: JSONObject.NULL)
        put("total", total ?: JSONObject.NULL)
        put("tarjeta", tarjeta); put("formaPago", formaPago); put("avisos", avisos)
    }

    companion object {
        fun deJson(o: JSONObject) = Documento(
            id = o.optLong("id"),
            fecha = o.optString("fecha"),
            tipo = o.optString("tipo"),
            numero = o.optString("numero"),
            rut = o.optString("rut"),
            neto = if (o.isNull("neto")) null else o.optInt("neto"),
            iva = if (o.isNull("iva")) null else o.optInt("iva"),
            total = if (o.isNull("total")) null else o.optInt("total"),
            tarjeta = o.optString("tarjeta"),
            formaPago = o.optString("formaPago"),
            avisos = o.optString("avisos"),
        )

        fun listaDeJson(txt: String): List<Documento> {
            if (txt.isBlank()) return emptyList()
            val arr = JSONArray(txt)
            return (0 until arr.length()).map { deJson(arr.getJSONObject(it)) }
        }

        fun listaAJson(lista: List<Documento>): String {
            val arr = JSONArray()
            lista.forEach { arr.put(it.aJson()) }
            return arr.toString()
        }
    }
}
