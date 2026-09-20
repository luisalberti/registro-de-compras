package cl.efficientchile.boletas.util

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Lee una boleta, factura o voucher: los totales y el detalle de lo comprado.
 *
 * La regla que ordena todo: **esto rellena campos, no guarda nada**. Lo que
 * sale de aca se muestra en pantalla y la persona lo confirma. Un lector que
 * se equivoca en el monto y guarda solo es peor que no tener lector, porque
 * nadie revisa un campo que ya venia lleno.
 *
 * ## Como encuentra los totales: por BLOQUE, no por etiqueta suelta
 *
 * Neto, IVA y total viven juntos en un racimo de lineas cerca del final, en
 * dos columnas: la etiqueta a la izquierda y el monto a la derecha, y siempre
 * en ese orden de arriba hacia abajo. Buscar dentro de ese bloque —y no por
 * todo el papel— es lo que evita que un "TOTAL" de otra parte se robe el
 * lugar: en la boleta de Super Bodega Acuenta, el "= 8" de "TOTAL NUMERO DE
 * ARTIC VEND" se colaba como total de la compra.
 *
 * ## Como sabe si leyo bien
 *
 * Dos comprobaciones, y las dos se avisan cuando fallan:
 *   - neto + IVA tiene que dar el total
 *   - la suma de los items tiene que dar el neto (factura) o el total (boleta)
 *
 * ## Calibrado con papel de verdad
 *
 * Doce documentos reales: facturas de ESTEL y Alex Ruz Cid, vouchers GetNet y
 * Transbank de varios comercios, boletas del SII de cuatro emisores, y la de
 * Super Bodega Acuenta. Entre ellas una con el total emborronado, una con un
 * digito comido por la arruga y una con dos copias en la misma tira.
 */
object LectorBoleta {

    private const val TASA_IVA = 0.19

    /** Una linea del detalle: que se compro, cuantos y a que precio. */
    data class Item(
        val descripcion: String,
        val cantidad: Int,
        val valorUnitario: Double,
        val valorTotal: Double,
    )

    data class Lectura(
        val total: Int? = null,
        val neto: Int? = null,
        val iva: Int? = null,
        val numero: String? = null,
        val fecha: String? = null,
        val hora: String? = null,
        val rut: String? = null,
        val ultimos4: String? = null,
        val items: List<Item> = emptyList(),
        /** El nombre del comercio, para que la planilla pueda aprenderlo. */
        val comercio: String = "",
        /** Categoria del cashflow que la app propone, ver Categorias. */
        val categoria: String = Categorias.POR_DEFECTO.nombre,
        /** false = no reconocio el comercio y cayo en la de por defecto. */
        val categoriaSegura: Boolean = false,
        val avisos: List<String> = emptyList(),
    ) {
        val sirve: Boolean get() = total != null
    }

    private const val MONTO = """\$?\s*(\d{1,3}(?:[.,]\d{3})+|\d+)"""
    private const val NUMERO = """(\d{1,3}(?:\.\d{3})+|\d{1,12})"""
    // Un numero con decimales: "630,29", "3.268,91", "1.050", "210".
    private const val CIFRA = """\d{1,3}(?:[.,]\d{3})*(?:,\d{1,2})?|\d+"""

    /** Las palabras que forman el bloque de totales. */
    private val RE_CLAVE = Regex(
        """\bTOTAL\b|\bSUBTOTAL\b|\bNETO\b|\bAFECTO\b|\bEXENTO\b|\bIVA\b|\bCOMPRA\b|\bIMPORTE\b|\bMONTO\b""")

    /* Lineas que nunca son un item, aunque traigan numeros.

       Ojo con las palabras genericas: "CAJA" estaba aca por el "Caja: 0004"
       del supermercado, y descartaba "1 CAJA ESTANCA 200 X 100 X 70" de la
       factura de ESTEL, que es un producto. Ahora se exige que venga seguida
       de numero o dos puntos, que es como aparece cuando si es cabecera. */
    private val RE_CABECERA = Regex(
        """\bRUT\b|R\.U\.T|\bFECHA\b|\bHORA\b|\bCAJA\s*[:.]?\s*\d|\bBOL\b|\bBOLETA\b|\bFACTURA\b""" +
        """|\bTELEFONO\b|\bFONO\b|\bDIRECCION\b|\bGIRO\b|\bCOMUNA\b|\bCIUDAD\b""" +
        """|\bSUCURSAL\b|\bRAZON\b|\bEMISION\b|\bVENCIMIENTO\b|\bCONDICION\b""" +
        """|\bFORMA DE PAGO\b|\bS\.?I\.?I\b|\bCODIGO\s*:|\bCANT\b|\bDESCRIPCION\b""" +
        """|\bARTICULO\b|\bMATRIZ\b|\bLOCAL\b|\bNOTA\b|\bITEM\b|\bVALOR U\b|\bDSCTO\b|\bDESCTO\b""")

    private val RE_PLATA = Regex(
        """\bTOTAL\b|\bIVA\b|\bNETO\b|\bMONTO\b|\bSUBTOTAL\b|\bCOMPRA\b|\bIMPORTE\b|\bAFECTO\b|\bEXENTO\b""")

    private val RE_MONTO = Regex(MONTO)
    private val RE_RUT = Regex("""\b(\d{1,2}\.\d{3}\.\d{3}-[\dK])\b""")
    private val RE_RUT_EN_LINEA = Regex("""\d{1,2}\.\d{3}\.\d{3}-[\dK]""")
    private val RE_FECHA = Regex("""\b(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})\b""")
    private val RE_HORA = Regex("""\b(\d{1,2}):(\d{2})""")
    private val RE_TARJETA = Regex("""[*X]+\s*(\d{4})\b""")
    private val RE_IVA = Regex("""\bIVA\b""")
    private val RE_SIN_IVA = Regex("""SIN IVA|EXENT""")
    private val RE_TIENE_DIGITO = Regex("""\d""")

    /* TOTAL, pero no los falsos totales: "TOTAL AFECTO" es el neto, "TOTAL
       IVA" el impuesto, "TOTAL NUMERO DE ARTIC" un conteo de articulos. */
    private val RE_TOTAL = Regex(
        """\bTOTAL\b(?!\s*(?:NETO|IVA|AFECTO|EXENTO|NUMERO|NRO|BRUTO))""")

    // Las cuatro formas de imprimir una linea del detalle.
    private val RE_ITEM_D = Regex("""^(\d{1,4})\s*[Xx]\s*($CIFRA)\s+($CIFRA)\s+($CIFRA)\s*$""")
    private val RE_ITEM_A = Regex("""^(\d{1,4})\s*[Xx]\s*($CIFRA)\s+(.+?)\$?\s*($CIFRA)\s*$""")
    private val RE_ITEM_C = Regex("""^(\d{1,4})\s+(.+?)\s+\$?($CIFRA)\s+\$?($CIFRA)\s+\$?($CIFRA)\s*$""")
    private val RE_ITEM_B = Regex("""^(\d{8,14})\s+(.+?)\$?\s*($CIFRA)\s*$""")

    /* Las dos formas mas comunes, y las que mas costo ver: muchas boletas
       imprimen el detalle SIN cantidad, arriba del bloque de totales, en dos
       o tres columnas.

         tres columnas:  7801  CAJA ESTANCA 200X100   3.269
         dos columnas:   PAN HALLULLA KG              1.590

       Son patrones sueltos a proposito, porque el formato es pobre. Lo que
       impide que se traguen media boleta no es la expresion sino esProducto,
       que exige letras de verdad, descarta cabeceras, fechas, RUT y cualquier
       linea con dos puntos, y rechaza los numeros que no pueden ser un precio.
       Sin esa guarda, un voucher de tarjeta (que no tiene detalle) saldria
       lleno de productos inventados. */
    private val RE_ITEM_E = Regex(
        """^([A-Z0-9][A-Z0-9\-./]{2,17})\s+([A-Z].*?)\s+\$?($CIFRA)\s*$""")
    private val RE_ITEM_F = Regex("""^([A-Z].*?)\s+\$?($CIFRA)\s*$""")

    private val RE_LETRA = Regex("""[A-Z]""")
    private val RE_FECHA_U_HORA = Regex("""\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{1,2}:\d{2}""")
    private val RE_COD_BARRAS = Regex("""^\s*\d{8,14}\s*""")
    private val RE_DESDE_PESO = Regex("""\$.*$""")
    private val RE_ESPACIOS = Regex("""\s+""")

    private val ETIQUETAS = listOf(
        """\bBOLETA\b""", """\bBOL\b""", """\bFOLIO\b""",
        """\bN(?:RO|UM|º|°)?\b""", """\bOPERACION\b""", """\bCOMPROBANTE\b""",
        """\bCOD\.?\s*AUT""", """\bAUTORIZACION\b""", """\bAPROBACION\b""",
        """\bTRANSACCION\b""", """\bTICKET\b""",
    )

    private fun sinTildes(s: String): String = s
        .replace('Á', 'A').replace('É', 'E').replace('Í', 'I')
        .replace('Ó', 'O').replace('Ú', 'U').replace('Ñ', 'N')
        .replace('á', 'A').replace('é', 'E').replace('í', 'I')
        .replace('ó', 'O').replace('ú', 'U').replace('ñ', 'N')

    private fun normalizar(lineas: List<String>): List<String> =
        lineas
            .map { sinTildes(it).uppercase().replace(RE_ESPACIOS, " ").trim() }
            .filter { it.isNotEmpty() }

    /** "1.030,29" -> 1030.29 ; "6.303" -> 6303.0 ; "590" -> 590.0 */
    private fun cifra(s: String): Double? {
        var t = s.trim()
        t = if (t.contains(',')) t.replace(".", "").replace(",", ".")
            else t.replace(".", "")
        return t.toDoubleOrNull()
    }

    private fun montos(linea: String): List<Int> =
        RE_MONTO.findAll(linea)
            .mapNotNull { it.groupValues[1].replace(".", "").replace(",", "").toIntOrNull() }
            .filter { it > 0 }
            .toList()

    /** El ULTIMO monto de la linea: en "IVA 19% $2.395" el 19 va antes. */
    private fun monto(linea: String): Int? = montos(linea).lastOrNull()

    /**
     * Donde empieza el racimo final de lineas de totales.
     *
     * Se agrupan las lineas con palabra clave permitiendo huecos de una linea,
     * y se toma el racimo mas grande; si empatan, el de mas abajo, que es el
     * de los totales de verdad.
     */
    private fun inicioBloqueTotales(L: List<String>): Int {
        val idx = L.indices.filter { RE_CLAVE.containsMatchIn(L[it]) }
        if (idx.isEmpty()) return L.size
        val racimos = mutableListOf<MutableList<Int>>()
        var act = mutableListOf(idx[0])
        for (i in idx.drop(1)) {
            if (i - act.last() <= 2) act.add(i)
            else { racimos.add(act); act = mutableListOf(i) }
        }
        racimos.add(act)
        val mejor = racimos.maxWithOrNull(
            compareBy({ it.size }, { it.last() }))!!
        return mejor.first()
    }

    private fun limpiarDesc(d: String): String =
        d.replace(RE_COD_BARRAS, "")
            .replace(RE_DESDE_PESO, "")
            .replace(RE_ESPACIOS, " ")
            .trim(' ', '.', ':', '-', '$')

    /**
     * El filtro que hace utilizables a los patrones sueltos.
     *
     * Sin esto, "nombre + precio" se traga la direccion, el telefono y el
     * numero de boleta. La regla es al reves de lo que parece: no se trata de
     * reconocer un producto (no hay forma) sino de descartar todo lo que
     * seguro NO lo es.
     */
    private fun esProducto(desc: String, precioTexto: String, precio: Double): Boolean {
        if (RE_CABECERA.containsMatchIn(desc)) return false
        if (RE_PLATA.containsMatchIn(desc)) return false
        if (RE_FECHA_U_HORA.containsMatchIn(desc)) return false
        if (RE_RUT_EN_LINEA.containsMatchIn(desc)) return false
        // Dos puntos es la marca de una etiqueta: "Caja: 0004", "Fono: 332...".
        if (desc.contains(':')) return false
        // Un nombre de producto tiene letras. "12 34" no es un producto.
        if (RE_LETRA.findAll(desc).count() < 3) return false
        // Un numero largo y pelado, sin puntos de miles, es un folio o un
        // codigo de barras, no un precio: en Chile $123.456 lleva puntos.
        val t = precioTexto.trim()
        if (!t.contains('.') && !t.contains(',') && t.length >= 6) return false
        return precio >= 10.0 && precio <= 90_000_000.0
    }

    /** El detalle: lo que se compro, antes del bloque de totales. */
    private fun leerItems(L: List<String>, fin: Int): List<Item> {
        val zona = L.subList(0, fin.coerceIn(0, L.size))
        val items = mutableListOf<Item>()
        var i = 0
        while (i < zona.size) {
            val l = zona[i]
            if (RE_CABECERA.containsMatchIn(l) || !RE_TIENE_DIGITO.containsMatchIn(l)) {
                i++; continue
            }

            // D) descripcion arriba, "5 x 210 0 1.050" abajo.
            if (i + 1 < zona.size) {
                val m = RE_ITEM_D.find(zona[i + 1])
                if (m != null) {
                    val c = m.groupValues[1].toIntOrNull()
                    val u = cifra(m.groupValues[2])
                    val t = cifra(m.groupValues[4])
                    val d = limpiarDesc(l)
                    if (c != null && u != null && t != null && d.isNotEmpty()) {
                        items.add(Item(d, c, u, t)); i += 2; continue
                    }
                }
            }

            // A) "4X590 HAMBURGUESA VACUNO$ 2.360"
            val ma = RE_ITEM_A.find(l)
            if (ma != null) {
                val c = ma.groupValues[1].toIntOrNull()
                val u = cifra(ma.groupValues[2])
                val d = limpiarDesc(ma.groupValues[3])
                val t = cifra(ma.groupValues[4])
                if (c != null && u != null && t != null &&
                    esProducto(d, ma.groupValues[4], t)) {
                    items.add(Item(d, c, u, t)); i++; continue
                }
            }

            // C) "10 CORDON H05V ... $630,29 $0 $6.303"
            val mc = RE_ITEM_C.find(l)
            if (mc != null) {
                val c = mc.groupValues[1].toIntOrNull()
                val d = limpiarDesc(mc.groupValues[2])
                val u = cifra(mc.groupValues[3])
                val t = cifra(mc.groupValues[5])
                if (c != null && u != null && t != null &&
                    esProducto(d, mc.groupValues[5], t)) {
                    items.add(Item(d, c, u, t)); i++; continue
                }
            }

            // B) "7801552008222 PAPAS FRITAS $ 2.000"
            val mb = RE_ITEM_B.find(l)
            if (mb != null) {
                val d = limpiarDesc(mb.groupValues[2])
                val t = cifra(mb.groupValues[3])
                if (t != null && esProducto(d, mb.groupValues[3], t)) {
                    items.add(Item(d, 1, t, t)); i++; continue
                }
            }

            // E) Tres columnas, sin cantidad: "7801 CAJA ESTANCA 200X100 3.269"
            val me = RE_ITEM_E.find(l)
            if (me != null) {
                val sku = me.groupValues[1]
                val d = limpiarDesc(me.groupValues[2])
                val t = cifra(me.groupValues[3])
                // El SKU tiene que traer al menos un digito: si no, la primera
                // palabra del nombre se estaria yendo como codigo.
                if (t != null && RE_TIENE_DIGITO.containsMatchIn(sku) &&
                    esProducto(d, me.groupValues[3], t)) {
                    items.add(Item("$d [$sku]", 1, t, t)); i++; continue
                }
            }

            // F) Dos columnas, sin cantidad: "PAN HALLULLA KG 1.590"
            val mf = RE_ITEM_F.find(l)
            if (mf != null) {
                val d = limpiarDesc(mf.groupValues[1])
                val t = cifra(mf.groupValues[2])
                if (t != null && esProducto(d, mf.groupValues[2], t)) {
                    items.add(Item(d, 1, t, t)); i++; continue
                }
            }
            i++
        }
        return items
    }

    /**
     * Entrada preferida: las lineas ya rehechas por posicion (ver Ocr.lineas).
     *
     * Existe esta forma —y no solo la de texto plano— porque la posicion de
     * cada palabra es informacion que el papel si tiene y que el texto plano
     * bota. En un documento de dos columnas, "TOTAL AFECTO" y su monto pueden
     * terminar a diez lineas de distancia si se confia en el orden de bloques
     * de ML Kit.
     */
    fun leer(lineas: List<String>): Lectura {
        val L = normalizar(lineas)
        val avisos = mutableListOf<String>()
        val inicio = inicioBloqueTotales(L)
        val B = (inicio until L.size).toList()

        // ---- total: el mayor de las lineas TOTAL de verdad, en el bloque ---
        val candTotal = mutableListOf<Int>()
        for (i in B) if (RE_TOTAL.containsMatchIn(L[i])) candTotal += montos(L[i])
        var total: Int? = candTotal.maxOrNull()
            ?: enBloque(L, B, """\bIMPORTE\b""")
            ?: enBloque(L, B, """\bMONTO\b(?!\s*VENTA)""")

        // ---- neto: primero AFECTO (termino del SII), luego el resto --------
        // Sin SUBTOTAL: en unas boletas es el neto y en otras el total.
        var neto: Int? = enBloque(L, B, """\bAFECTO\b""")
            ?: enBloque(L, B, """\bNETO\b|\bCOMPRA\b|\bMONTO VENTA\b""")

        var iva: Int? = null
        for (i in B) {
            val l = L[i]
            if (RE_IVA.containsMatchIn(l) && !RE_SIN_IVA.containsMatchIn(l)) {
                val v = monto(l)
                if (v != null && v != 19) { iva = v; break }
            }
        }

        // ---- red de seguridad por posicion, dentro del bloque -------------
        /* neto, IVA y total van seguidos, con IVA ~= 19% del neto y sumando
           el total. Si las etiquetas fallaron o no cuadran, manda el trio. */
        val seq = mutableListOf<Int>()
        for (i in B) for (v in montos(L[i])) if (v >= 100) seq.add(v)
        for (x in 0..seq.size - 3) {
            val a = seq[x]; val b = seq[x + 1]; val c = seq[x + 2]
            val esperado = (a * TASA_IVA).roundToInt()
            if (a > b && abs(a + b - c) <= 2 &&
                abs(b - esperado) <= max(2, (a * 0.01).roundToInt())) {
                val noCuadra = neto == null || iva == null || total == null ||
                    abs((neto ?: 0) + (iva ?: 0) - (total ?: 0)) > 2
                if (noCuadra) { neto = a; iva = b; total = c }
                break
            }
        }

        // ---- el detalle ---------------------------------------------------
        val items = leerItems(L, inicio)

        // ---- numero: en todo el documento, suele ir arriba ----------------
        val candidatos = mutableListOf<String>()
        for (et in ETIQUETAS) {
            val re = Regex(et + """[^0-9]{0,20}?""" + NUMERO)
            for (l in L) {
                if (RE_RUT_EN_LINEA.containsMatchIn(l)) continue
                if (RE_PLATA.containsMatchIn(l)) continue
                re.find(l)?.let { candidatos.add(it.groupValues[1].replace(".", "")) }
            }
        }
        val numero = candidatos.firstOrNull { it.length >= 3 } ?: candidatos.firstOrNull()

        // ---- fecha, hora, RUT, tarjeta ------------------------------------
        var fecha: String? = null
        var hora: String? = null
        for (l in L) {
            if (fecha == null) {
                // "21 DE MAYO 71-73-75" se leia como fecha y daba 2075-73-71.
                for (m in RE_FECHA.findAll(l)) {
                    val d = m.groupValues[1].toInt()
                    val mes = m.groupValues[2].toInt()
                    var a = m.groupValues[3].toInt()
                    if (d !in 1..31 || mes !in 1..12) continue
                    if (a < 100) a += 2000
                    if (a !in 2000..2100) continue
                    fecha = "%04d-%02d-%02d".format(a, mes, d); break
                }
            }
            if (hora == null) RE_HORA.find(l)?.let { m ->
                hora = "%02d:%s".format(m.groupValues[1].toInt(), m.groupValues[2])
            }
        }
        val rut = L.firstNotNullOfOrNull { RE_RUT.find(it)?.groupValues?.get(1) }
        val u4 = L.firstNotNullOfOrNull { RE_TARJETA.find(it)?.groupValues?.get(1) }

        // ---- cuadratura ---------------------------------------------------
        if (total != null && neto != null && iva != null && total < neto) {
            avisos += "El total se leyó mal ($total). Se usó neto + IVA."
            total = neto + iva
        }
        val t = total
        if (t != null && neto != null && iva != null) {
            if (abs((neto + iva) - t) > 2)
                avisos += "El neto y el IVA no suman el total. Revísalos."
        } else if (t != null && neto == null && iva != null) {
            neto = t - iva; avisos += "Neto calculado como total menos IVA."
        } else if (t != null && neto == null) {
            neto = (t / (1 + TASA_IVA)).roundToInt(); iva = t - neto!!
            avisos += "Neto e IVA calculados desde el total."
        } else if (t == null && neto != null && iva != null) {
            total = neto + iva; avisos += "Total calculado desde neto + IVA."
        }

        /* La prueba de fuego del detalle: la suma de los items tiene que dar
           el neto (en una factura, donde los precios van sin IVA) o el total
           (en una boleta, donde van con IVA). Si no da ninguno de los dos,
           algo se leyo mal y hay que decirlo. */
        if (items.isNotEmpty()) {
            val suma = items.sumOf { it.valorTotal }.roundToInt()
            val cuadra = (neto != null && abs(suma - neto!!) <= 2) ||
                (total != null && abs(suma - total!!) <= 2)
            if (!cuadra) {
                avisos += "Los ${items.size} productos suman $suma, que no calza " +
                    "con el neto ni con el total. Revisa el detalle."
            }
        }

        if (total == null) avisos += "No se encontró el total. Escríbelo a mano."
        if (numero == null) avisos += "No se encontró el número. Escríbelo a mano."
        if (items.isEmpty()) avisos += "No se pudo leer el detalle de productos."

        val propuesta = Categorias.proponer(L)
        return Lectura(total, neto, iva, numero, fecha, hora, rut, u4, items,
            Categorias.comercio(L),
            propuesta.categoria.nombre, propuesta.segura, avisos)
    }

    /** Forma antigua, por si llega texto plano. Prefiere la de lineas. */
    fun leer(texto: String): Lectura = leer(texto.lineSequence().toList())

    /** Primer monto del bloque cuya linea lleve la etiqueta. */
    private fun enBloque(L: List<String>, B: List<Int>, patron: String): Int? {
        val re = Regex(patron)
        for (i in B) {
            val l = L[i]
            if (re.containsMatchIn(l)) {
                monto(l)?.let { return it }
                // Etiqueta sin monto: la maquina lo imprimio abajo.
                if (i + 1 < L.size && !RE_CLAVE.containsMatchIn(L[i + 1])) {
                    monto(L[i + 1])?.let { return it }
                }
            }
        }
        return null
    }
}
