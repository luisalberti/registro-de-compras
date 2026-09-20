package cl.efficientchile.boletas.export

import cl.efficientchile.boletas.data.Documento
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Escribe un .xlsx de verdad, a mano.
 *
 * Un xlsx es un zip con unas cuantas partes XML. No hay libreria de xlsx que
 * compile sin sorpresas en Android, asi que se arma con java.util.zip, que ya
 * viene con Android. La estructura exacta de este archivo —las mismas partes,
 * el mismo XML— se valido abriendola con un lector de Excel real antes de
 * escribir esto, y los montos abren como numeros que Excel suma, no como
 * texto.
 */
object Excel {

    private val COLUMNAS = listOf(
        "Fecha", "Tipo", "N Documento", "RUT emisor",
        "Neto", "IVA", "Total", "Tarjeta", "Forma pago", "Avisos",
    )
    // Cuales columnas son numeros. Importa: si el total va como texto, Excel
    // no lo suma, y todo el punto de exportar es poder sumar.
    private val ES_NUMERO = setOf(4, 5, 6)

    // La segunda hoja: una fila por producto comprado, no por documento.
    // Repite fecha y numero de documento en cada fila a proposito, para que
    // se pueda filtrar y hacer tabla dinamica sin tener que cruzar hojas.
    private val COL_DETALLE = listOf(
        "Fecha", "Tipo", "N Documento", "Descripcion",
        "Cantidad", "Valor unitario", "Valor total",
    )

    fun escribir(destino: File, docs: List<Documento>) {
        ZipOutputStream(destino.outputStream().buffered()).use { z ->
            parte(z, "[Content_Types].xml", CONTENT_TYPES)
            parte(z, "_rels/.rels", RELS)
            parte(z, "xl/workbook.xml", WORKBOOK)
            parte(z, "xl/_rels/workbook.xml.rels", WB_RELS)
            parte(z, "xl/styles.xml", STYLES)
            parte(z, "xl/worksheets/sheet1.xml", hoja(docs))
            parte(z, "xl/worksheets/sheet2.xml", hojaDetalle(docs))
        }
    }

    private fun parte(z: ZipOutputStream, nombre: String, contenido: String) {
        z.putNextEntry(ZipEntry(nombre))
        z.write(contenido.toByteArray(Charsets.UTF_8))
        z.closeEntry()
    }

    private fun esc(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;")

    /** 0 -> A, 1 -> B, ... 26 -> AA. */
    private fun letra(indice: Int): String {
        var i = indice + 1
        val sb = StringBuilder()
        while (i > 0) {
            val r = (i - 1) % 26
            sb.insert(0, ('A' + r))
            i = (i - 1) / 26
        }
        return sb.toString()
    }

    private fun celdaTexto(col: Int, fila: Int, valor: String, estilo: Int): String {
        val ref = "${letra(col)}$fila"
        val s = if (estilo != 0) " s=\"$estilo\"" else ""
        if (valor.isEmpty()) return "<c r=\"$ref\"$s/>"
        return "<c r=\"$ref\"$s t=\"inlineStr\"><is><t xml:space=\"preserve\">" +
            "${esc(valor)}</t></is></c>"
    }

    private fun celdaNumero(col: Int, fila: Int, valor: Int?): String {
        val ref = "${letra(col)}$fila"
        return if (valor == null) "<c r=\"$ref\"/>" else "<c r=\"$ref\"><v>$valor</v></c>"
    }

    /**
     * Un valor unitario puede traer decimales (3.268,91 en una factura), asi
     * que no se puede redondear a entero: la suma dejaria de cuadrar con el
     * neto. Va con punto decimal porque el XML de xlsx siempre usa punto,
     * independiente del idioma con que se abra despues.
     */
    private fun celdaDecimal(col: Int, fila: Int, valor: Double?): String {
        val ref = "${letra(col)}$fila"
        if (valor == null) return "<c r=\"$ref\"/>"
        val redondeado = Math.round(valor * 100.0) / 100.0
        val txt = if (redondeado == Math.floor(redondeado)) {
            redondeado.toLong().toString()
        } else {
            redondeado.toString()
        }
        return "<c r=\"$ref\"><v>$txt</v></c>"
    }

    private fun hoja(docs: List<Documento>): String {
        val filas = StringBuilder()

        // Encabezado en negrita (estilo 1).
        filas.append("<row r=\"1\">")
        COLUMNAS.forEachIndexed { c, t -> filas.append(celdaTexto(c, 1, t, 1)) }
        filas.append("</row>")

        docs.forEachIndexed { i, d ->
            val fila = i + 2
            val valores: List<Any?> = listOf(
                d.fecha, d.tipo, d.numero, d.rut,
                d.neto, d.iva, d.total, d.tarjeta, d.formaPago, d.avisos,
            )
            filas.append("<row r=\"$fila\">")
            valores.forEachIndexed { c, v ->
                filas.append(
                    if (c in ES_NUMERO) celdaNumero(c, fila, v as? Int)
                    else celdaTexto(c, fila, (v as? String).orEmpty(), 0)
                )
            }
            filas.append("</row>")
        }

        return envolver(filas)
    }

    private fun hojaDetalle(docs: List<Documento>): String {
        val filas = StringBuilder()
        filas.append("<row r=\"1\">")
        COL_DETALLE.forEachIndexed { c, t -> filas.append(celdaTexto(c, 1, t, 1)) }
        filas.append("</row>")

        var fila = 1
        docs.forEach { d ->
            d.items.forEach { it ->
                fila++
                filas.append("<row r=\"$fila\">")
                filas.append(celdaTexto(0, fila, d.fecha, 0))
                filas.append(celdaTexto(1, fila, d.tipo, 0))
                filas.append(celdaTexto(2, fila, d.numero, 0))
                filas.append(celdaTexto(3, fila, it.descripcion, 0))
                filas.append(celdaNumero(4, fila, it.cantidad))
                filas.append(celdaDecimal(5, fila, it.valorUnitario))
                filas.append(celdaDecimal(6, fila, it.valorTotal))
                filas.append("</row>")
            }
        }
        return envolver(filas)
    }

    private fun envolver(filas: StringBuilder): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
        "<sheetData>$filas</sheetData></worksheet>"

    // --- Partes fijas del archivo, iguales para todo xlsx ------------------

    private const val CONTENT_TYPES =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
        "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
        "<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
        "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
        "</Types>"

    private const val RELS =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
        "</Relationships>"

    private const val WORKBOOK =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
        "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
        "<sheets><sheet name=\"Documentos\" sheetId=\"1\" r:id=\"rId1\"/>" +
        "<sheet name=\"Detalle\" sheetId=\"2\" r:id=\"rId2\"/></sheets></workbook>"

    private const val WB_RELS =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
        "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>" +
        "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
        "</Relationships>"

    private const val STYLES =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
        "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
        "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
        "<fills count=\"1\"><fill><patternFill patternType=\"none\"/></fill></fills>" +
        "<borders count=\"1\"><border/></borders>" +
        "<cellStyleXfs count=\"1\"><xf/></cellStyleXfs>" +
        "<cellXfs count=\"2\"><xf/><xf fontId=\"1\" applyFont=\"1\"/></cellXfs>" +
        "</styleSheet>"
}
