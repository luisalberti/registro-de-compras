package cl.efficientchile.boletas.data

import android.content.Context
import java.io.File

/**
 * Guarda la lista de documentos en un archivo del telefono.
 *
 * Un archivo JSON y no una base de datos a proposito: la lista es una sola,
 * se lee entera y se escribe entera, y no hay consultas. Meter Room para esto
 * seria cargar un motor de base de datos para guardar una libreta.
 *
 * Lo que importa: se graba en cada cambio. Registrar treinta boletas y perder
 * las treinta porque la app se cerro antes de exportar seria el peor final
 * posible, y es justo lo que pasa si esto vive solo en memoria.
 */
class Almacen(ctx: Context) {

    private val archivo = File(ctx.filesDir, "documentos.json")

    fun cargar(): List<Documento> =
        try {
            if (archivo.exists()) Documento.listaDeJson(archivo.readText()) else emptyList()
        } catch (e: Exception) {
            // Un archivo corrupto no debe dejar la app sin arrancar. Se
            // empieza vacio; lo peor es perder lo no exportado, no el arranque.
            emptyList()
        }

    fun guardar(lista: List<Documento>) {
        try {
            archivo.writeText(Documento.listaAJson(lista))
        } catch (e: Exception) {
            // Mejor seguir con la lista en memoria que caerse al guardar.
        }
    }
}
