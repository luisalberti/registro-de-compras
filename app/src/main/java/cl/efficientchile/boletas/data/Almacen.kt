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
    private val respaldo = File(ctx.filesDir, "documentos.bak")

    fun cargar(): List<Documento> {
        leer(archivo)?.let { return it }
        // Si el principal quedo a medio escribir, el respaldo es la version
        // completa anterior. Perder el ultimo documento es molesto; perder
        // los doscientos anteriores es perder el trabajo de un mes.
        leer(respaldo)?.let { return it }
        return emptyList()
    }

    private fun leer(f: File): List<Documento>? = try {
        if (f.exists() && f.length() > 0) Documento.listaDeJson(f.readText()) else null
    } catch (e: Exception) {
        null
    }

    /**
     * Graba entero o no graba.
     *
     * Se escribe a un archivo aparte y recien despues se reemplaza el bueno.
     * Si el telefono se apaga en la mitad, lo que queda escrito a medias es el
     * temporal, y el archivo que la app lee sigue siendo el anterior, completo.
     * Escribir directo sobre el archivo bueno es como se pierde una base de
     * datos entera por un corte de luz.
     */
    fun guardar(lista: List<Documento>) {
        try {
            val temporal = File(archivo.parentFile, "documentos.tmp")
            temporal.writeText(Documento.listaAJson(lista))
            if (archivo.exists()) {
                respaldo.delete()
                archivo.renameTo(respaldo)
            }
            if (!temporal.renameTo(archivo)) {
                // renameTo puede fallar; entonces se copia y se borra el temporal.
                archivo.writeText(temporal.readText())
                temporal.delete()
            }
        } catch (e: Exception) {
            // Mejor seguir con la lista en memoria que caerse al guardar.
        }
    }
}
