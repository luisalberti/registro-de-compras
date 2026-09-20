package cl.efficientchile.boletas.util

import cl.efficientchile.boletas.data.Documento
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * El puente con la planilla de Google.
 *
 * ## Por que una fila y no el archivo entero
 *
 * Subir el .xlsx completo cada vez seria subir una foto del momento: dos
 * subidas duplican todo. Mandar **un documento a la vez, con su
 * identificador**, deja que la planilla decida: si ya conoce ese id, no hace
 * nada. Reenviar cien veces la misma boleta deja una sola fila.
 *
 * ## Sin cuenta de Google en el telefono
 *
 * El destino es un Apps Script publicado por el usuario, con acceso
 * "cualquiera con el enlace" y una clave propia en la direccion. La app solo
 * hace peticiones a esa URL: no hay login, ni token que caduque, ni
 * biblioteca de Google dentro del APK.
 *
 * ## El redirect
 *
 * Apps Script responde 302 y manda a googleusercontent.com. Java no reenvia
 * un POST entre hosts distintos por seguridad, asi que hay que seguirlo a
 * mano con GET, que es lo que Apps Script espera. Sin esto, todo envio
 * parece fallar aunque la fila haya quedado escrita.
 */
object Nube {

    data class Resultado(
        val ok: Boolean,
        val mensaje: String,
        val reintentable: Boolean,
        val cuerpo: String = "",
    )

    /** Una regla aprendida: si el texto trae esta palabra, va en esta categoria. */
    data class Regla(val palabra: String, val categoria: String)

    /** El resumen de un mes, tal como lo calcula la planilla. */
    data class Resumen(
        val anio: Int, val mes: Int,
        val ingresos: Int, val egresos: Int, val saldo: Int,
        val ahorro: Int, val deuda: Int, val documentos: Int,
        val categorias: List<Pair<String, Int>>,
    )

    /** Un movimiento ya escrito en la planilla, para mirarlo y corregirlo. */
    data class Movimiento(
        val id: String, val fecha: String, val tipo: String,
        val categoria: String, val monto: Int, val detalle: String,
        val origen: String,
    )

    private const val ESPERA = 20_000

    // ---------------------------------------------------------------- enviar

    fun enviar(url: String, doc: Documento, comercio: String, aprender: Boolean): Resultado {
        val o = doc.aJson()
        o.put("accion", "documento")
        o.put("comercio", comercio)
        o.put("aprender", aprender)
        return post(url, o.toString())
    }

    fun probar(url: String): Resultado =
        post(url, JSONObject().put("accion", "prueba").toString())

    fun editar(url: String, id: String, categoria: String, comercio: String): Resultado =
        post(url, JSONObject().apply {
            put("accion", "editar"); put("id", id)
            put("categoria", categoria); put("comercio", comercio)
        }.toString())

    fun borrar(url: String, id: String): Resultado =
        post(url, JSONObject().apply { put("accion", "borrar"); put("id", id) }.toString())

    // --------------------------------------------------------------- consultar

    fun reglas(url: String): List<Regla> {
        val r = get(url, "reglas")
        if (!r.ok) return emptyList()
        return try {
            val arr = JSONObject(r.cuerpo).optJSONArray("reglas") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val p = o.optString("p").trim()
                val c = o.optString("c").trim()
                if (p.isEmpty() || c.isEmpty()) null else Regla(p.uppercase(), c)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun resumen(url: String, anio: Int, mes: Int): Resumen? {
        val r = get(url, "resumen", mapOf("anio" to "$anio", "mes" to "$mes"))
        if (!r.ok) return null
        return try {
            val o = JSONObject(r.cuerpo).getJSONObject("resumen")
            val arr = o.optJSONArray("categorias") ?: JSONArray()
            Resumen(
                anio = o.optInt("anio", anio), mes = o.optInt("mes", mes),
                ingresos = o.optDouble("ingresos", 0.0).toInt(),
                egresos = o.optDouble("egresos", 0.0).toInt(),
                saldo = o.optDouble("saldo", 0.0).toInt(),
                ahorro = o.optDouble("ahorro", 0.0).toInt(),
                deuda = o.optDouble("deuda", 0.0).toInt(),
                documentos = o.optInt("documentos", 0),
                categorias = (0 until arr.length()).map { i ->
                    val c = arr.getJSONObject(i)
                    c.optString("categoria") to c.optDouble("monto", 0.0).toInt()
                },
            )
        } catch (e: Exception) {
            null
        }
    }

    fun movimientos(url: String, cuantos: Int = 30): List<Movimiento> {
        val r = get(url, "movimientos", mapOf("n" to "$cuantos"))
        if (!r.ok) return emptyList()
        return try {
            val arr = JSONObject(r.cuerpo).optJSONArray("movimientos") ?: return emptyList()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Movimiento(
                    id = o.optString("id"), fecha = o.optString("fecha"),
                    tipo = o.optString("tipo"), categoria = o.optString("categoria"),
                    monto = o.optDouble("monto", 0.0).toInt(),
                    detalle = o.optString("detalle"), origen = o.optString("origen"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ------------------------------------------------------------------ red

    private fun post(url: String, json: String): Resultado =
        pedir(url) { destino ->
            (destino.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = ESPERA; readTimeout = ESPERA
                instanceFollowRedirects = false   // se siguen a mano, ver arriba
                setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
        }

    private fun get(url: String, q: String, extra: Map<String, String> = emptyMap()): Resultado {
        // La consulta se agrega a la direccion que ya trae ?clave=...
        val sep = if (url.contains('?')) "&" else "?"
        val cola = buildString {
            append(sep).append("q=").append(URLEncoder.encode(q, "UTF-8"))
            extra.forEach { (k, v) ->
                append("&").append(k).append("=").append(URLEncoder.encode(v, "UTF-8"))
            }
        }
        return pedir(url + cola) { destino ->
            (destino.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = ESPERA; readTimeout = ESPERA
                instanceFollowRedirects = false
            }
        }
    }

    private fun pedir(url: String, abrir: (URL) -> HttpURLConnection): Resultado {
        return try {
            var conexion = abrir(URL(url))
            var codigo = conexion.responseCode
            var saltos = 0
            while (codigo in 300..399 && saltos < 3) {
                val siguiente = conexion.getHeaderField("Location")
                    ?: return Resultado(false, "El servidor redirigió sin decir a dónde", true)
                conexion.disconnect()
                conexion = (URL(siguiente).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = ESPERA; readTimeout = ESPERA
                }
                codigo = conexion.responseCode
                saltos++
            }
            val texto = try {
                val flujo = if (codigo in 200..299) conexion.inputStream else conexion.errorStream
                flujo?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            } catch (e: Exception) { "" }
            conexion.disconnect()

            when {
                codigo !in 200..299 ->
                    Resultado(false, "La planilla respondió $codigo", codigo >= 500)
                texto.contains("\"ok\":true") || texto.contains("\"ok\": true") ->
                    Resultado(true, "Listo", false, texto)
                texto.contains("clave incorrecta") ->
                    Resultado(false, "La clave de la dirección no calza con la del script.",
                        false, texto)
                texto.contains("<html", true) ->
                    Resultado(false,
                        "La planilla pide iniciar sesión. Vuelve a publicar el script " +
                            "con acceso «Cualquier usuario».", false, texto)
                else -> Resultado(false, "Respuesta que no entiendo: ${texto.take(120)}",
                    false, texto)
            }
        } catch (e: java.net.UnknownHostException) {
            Resultado(false, "Sin conexión", true)
        } catch (e: java.net.SocketTimeoutException) {
            Resultado(false, "La planilla no respondió a tiempo", true)
        } catch (e: Exception) {
            Resultado(false, e.message ?: "No se pudo conectar", true)
        }
    }
}
