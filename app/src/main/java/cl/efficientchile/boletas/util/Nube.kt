package cl.efficientchile.boletas.util

import cl.efficientchile.boletas.data.Documento
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manda una boleta a la planilla de Google, como una fila.
 *
 * ## Por que una fila y no el archivo entero
 *
 * Subir el .xlsx completo cada vez seria subir una foto del momento: dos
 * subidas duplican todo, y no hay forma de saber que fila es nueva. Mandar
 * **un documento a la vez, con su identificador**, deja que la planilla
 * decida: si ya conoce ese id, no hace nada. Reenviar cien veces la misma
 * boleta deja una sola fila.
 *
 * ## Sin cuenta de Google en el telefono
 *
 * El destino es un Apps Script publicado por ti, con acceso "cualquiera con
 * el enlace". La app solo hace un POST a esa URL. No hay login, ni token que
 * caduque, ni biblioteca de Google adentro del APK. El precio es que quien
 * tenga esa URL puede escribir en tu planilla, asi que no se comparte.
 *
 * ## El redirect
 *
 * Apps Script responde 302 y manda a googleusercontent.com. Java no reenvia
 * un POST entre hosts distintos por seguridad, asi que hay que seguirlo a
 * mano con GET, que es lo que Apps Script espera. Sin esto, todo envio
 * parece fallar aunque la fila haya quedado escrita.
 */
object Nube {

    /** Lo que paso al intentar mandar. `reintentable` decide si se guarda pendiente. */
    data class Resultado(val ok: Boolean, val mensaje: String, val reintentable: Boolean)

    private const val ESPERA = 15_000

    fun enviar(url: String, doc: Documento): Resultado = intentar(url, cuerpo(doc))

    /** Un envio de prueba, para el boton "Probar" de la pantalla de ajustes. */
    fun probar(url: String): Resultado =
        intentar(url, JSONObject().apply { put("prueba", true) }.toString())

    private fun cuerpo(doc: Documento): String {
        val o = doc.aJson()
        // La planilla necesita saber que es un documento y no una prueba.
        o.put("tipoMensaje", "documento")
        return o.toString()
    }

    private fun intentar(url: String, json: String): Resultado {
        return try {
            var destino = URL(url)
            var conexion = abrirPost(destino, json)
            var codigo = conexion.responseCode

            // Hasta tres saltos: Apps Script normalmente hace uno solo.
            var saltos = 0
            while (codigo in 300..399 && saltos < 3) {
                val siguiente = conexion.getHeaderField("Location")
                    ?: return Resultado(false, "El servidor redirigio sin decir a donde", true)
                conexion.disconnect()
                destino = URL(siguiente)
                conexion = (destino.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = ESPERA
                    readTimeout = ESPERA
                }
                codigo = conexion.responseCode
                saltos++
            }

            val texto = try {
                val flujo = if (codigo in 200..299) conexion.inputStream else conexion.errorStream
                flujo?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            } catch (e: Exception) {
                ""
            }
            conexion.disconnect()

            when {
                codigo !in 200..299 ->
                    Resultado(false, "La planilla respondio $codigo", codigo >= 500)
                texto.contains("\"ok\":true") || texto.contains("\"ok\": true") ->
                    Resultado(true, "Guardado en la planilla", false)
                texto.contains("Google Drive") || texto.contains("<html", true) ->
                    // Pagina de login: el Apps Script no quedo publicado para
                    // "cualquiera con el enlace".
                    Resultado(
                        false,
                        "La planilla pide iniciar sesion. Vuelve a publicar el " +
                            "script con acceso «Cualquier persona».",
                        false,
                    )
                else -> Resultado(false, "Respuesta que no entiendo: ${texto.take(120)}", false)
            }
        } catch (e: java.net.UnknownHostException) {
            Resultado(false, "Sin conexión", true)
        } catch (e: java.net.SocketTimeoutException) {
            Resultado(false, "La planilla no respondió a tiempo", true)
        } catch (e: Exception) {
            Resultado(false, e.message ?: "No se pudo enviar", true)
        }
    }

    private fun abrirPost(destino: URL, json: String): HttpURLConnection =
        (destino.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = ESPERA
            readTimeout = ESPERA
            instanceFollowRedirects = false   // se siguen a mano, ver arriba
            setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }
}
