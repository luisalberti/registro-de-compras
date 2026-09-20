package cl.efficientchile.boletas.util

/**
 * Las categorias del cashflow de Luis, y como adivinar cual corresponde.
 *
 * ## De donde salen
 *
 * No son inventadas: son exactamente las filas de la hoja GASTOS <MES> de su
 * planilla personal, en el mismo orden y con el mismo nombre. Por eso viaja
 * tambien el numero de fila: la planilla escribe en la celda que cruza esa
 * fila con el dia del mes, y con el numero a mano no hay que adivinar cual
 * fila es cuando hay dos que se llaman parecido (hay un "OTROS" en AUTO y un
 * "OTROS GASTOS" en OTROS).
 *
 * ## Por que adivina en vez de preguntar
 *
 * Registrar una pila de boletas eligiendo categoria a mano en cada una es
 * justo el trabajo que la app existe para evitar. Pero adivinar y guardar sin
 * mostrar seria peor: una clasificacion equivocada que nadie vio ensucia el
 * analisis para siempre. Por eso se propone, se muestra en pantalla donde va
 * a quedar, y se confirma. Un toque cuando acierta, dos cuando no.
 *
 * ## El metodo
 *
 * Cuenta cuantas palabras clave de cada categoria aparecen en el texto de la
 * boleta —nombre del comercio y productos— y gana la que tenga mas. Es
 * deliberadamente simple: reglas que se pueden leer y corregir valen mas que
 * algo mas listo que nadie entiende cuando se equivoca.
 */
object Categorias {

    data class Categoria(val fila: Int, val grupo: String, val nombre: String)

    /** Las 23 filas de la hoja, en el mismo orden que en la planilla. */
    val TODAS = listOf(
        Categoria(4, "CUENTAS PERSONALES", "PREST. AUTO"),
        Categoria(5, "CUENTAS PERSONALES", "AHORRO CASA"),
        Categoria(6, "HIJOS", "PENSIONES"),
        Categoria(7, "HIJOS", "ROPA Y REGALOS"),
        Categoria(8, "HIJOS", "AHORRO EMERGENCIAS"),
        Categoria(9, "HIJOS", "SALIDAS CON FAMILIA"),
        Categoria(10, "CASA", "COMIDA CASA"),
        Categoria(11, "CASA", "COMIDA PERSONAL"),
        Categoria(12, "CASA", "CUENTAS"),
        Categoria(13, "SALUD", "MEDICAMENTOS"),
        Categoria(14, "SALUD", "ROPA"),
        Categoria(15, "SALUD", "DEPORTE"),
        Categoria(16, "AUTO", "BENCINA"),
        Categoria(17, "AUTO", "OTROS"),
        Categoria(18, "DIVERSION", "COPETE"),
        Categoria(19, "DIVERSION", "WEED"),
        Categoria(20, "DIVERSION", "CARCASA CELULAR"),
        Categoria(21, "DIVERSION", "SALIDA CON AMIGOS"),
        Categoria(22, "OTROS", "CHICHES"),
        Categoria(23, "OTROS", "DONACIONES"),
        Categoria(24, "OTROS", "PRESTAMOS"),
        Categoria(25, "OTROS", "LOCOMOCION"),
        Categoria(26, "OTROS", "OTROS GASTOS"),
    )

    val POR_DEFECTO: Categoria = TODAS.first { it.nombre == "OTROS GASTOS" }

    fun porNombre(n: String): Categoria =
        TODAS.firstOrNull { it.nombre == n } ?: POR_DEFECTO

    /* Las palabras que delatan cada categoria. Solo las que se pueden leer de
       una boleta: PENSIONES, AHORRO o DONACIONES no dejan rastro en un papel
       de compra, asi que no tienen reglas y se eligen a mano cuando toca. */
    private val REGLAS: List<Pair<String, List<String>>> = listOf(
        "BENCINA" to listOf(
            "COPEC", "SHELL", "PETROBRAS", "ARAMCO", "ENEX", "TERPEL",
            "COMBUSTIBLE", "BENCINA", "DIESEL", "PETROLEO",
            "ESTACION DE SERVICIO", "SERVICENTRO"),
        "COMIDA CASA" to listOf(
            "LIDER", "JUMBO", "SANTA ISABEL", "UNIMARC", "TOTTUS", "ACUENTA",
            "SUPERMERCADO", "MAYORISTA", "ALVI", "OK MARKET", "BIG JOHN",
            "VERDULERIA", "CARNICERIA", "PANADERIA", "FERIA", "ABARROTE",
            "MINIMARKET", "ALMACEN"),
        "COMIDA PERSONAL" to listOf(
            "MCDONALD", "BURGER", "DOMINO", "PAPA JOHN", "SUBWAY", "STARBUCKS",
            "JUAN MAESTRO", "DOGGIS", "TELEPIZZA", "PIZZA", "SUSHI", "CAFE",
            "CAFETERIA", "RESTAURANT", "RESTORAN", "SCHOP", "EMPANADA",
            "COMPLETO", "SANDWICH", "PEDIDOSYA", "RAPPI", "UBER EATS"),
        "COPETE" to listOf(
            "BOTILLERIA", "LICOR", "CERVEZA", "VINO", "PISCO", "WHISKY",
            "CERVECERIA", "DESTILERIA"),
        "MEDICAMENTOS" to listOf(
            "FARMACIA", "CRUZ VERDE", "SALCOBRAND", "AHUMADA", "DR SIMI",
            "DOCTOR SIMI", "BOTICA", "CLINICA", "LABORATORIO"),
        "DEPORTE" to listOf(
            "GIMNASIO", "SPORTLIFE", "SMART FIT", "ENERGY FITNESS",
            "DECATHLON", "DEPORTE", "PADEL", "GYM"),
        "ROPA" to listOf(
            "ZARA", "H&M", "FALABELLA", "PARIS", "RIPLEY", "HITES", "CORONA",
            "TRICOT", "CALZADO", "ZAPATERIA", "VESTUARIO"),
        "CUENTAS" to listOf(
            "ENEL", "CGE", "CHILQUINTA", "SAESA", "ESVAL", "AGUAS ANDINAS",
            "ESSBIO", "AGUAS", "LIPIGAS", "ABASTIBLE", "GASCO", "GASVALPO",
            "ENTEL", "MOVISTAR", "WOM", "CLARO", "VTR", "CONTRIBUCION",
            "TESORERIA", "PATENTE"),
        "LOCOMOCION" to listOf(
            "UBER", "CABIFY", "DIDI", "TAXI", "METRO", "PEAJE", "AUTOPISTA",
            "ESTACIONAMIENTO", "PARKING", "PULLMAN", "TURBUS", "TUR BUS",
            "PASAJE"),
        // Esta es la fila OTROS del grupo AUTO: mantencion del auto.
        "OTROS" to listOf(
            "AUTOMOTRIZ", "REPUESTO", "VULCANIZACION", "NEUMATICO",
            "SERVITECA", "LAVADO DE AUTO", "TALLER MECANICO", "LUBRICANTE",
            "REVISION TECNICA"),
        "SALIDAS CON FAMILIA" to listOf(
            "CINEMARK", "CINE HOYTS", "CINEPLANET", "ZOOLOGICO", "MUSEO"),
        "SALIDA CON AMIGOS" to listOf("DISCOTEQUE", "KARAOKE"),
        "ROPA Y REGALOS" to listOf("JUGUETERIA", "JUGUETE", "REGALO"),
        "CHICHES" to listOf(
            "PC FACTORY", "SPDIGITAL", "WINPY", "TECNOLOGIA", "ELECTRONICA",
            "CELULAR", "AUDIFONO", "CARGADOR", "LIBRERIA"),
        "OTROS GASTOS" to listOf(
            "FERRETERIA", "SODIMAC", "EASY", "CONSTRUMART", "IMPERIAL",
            "PREUNIC", "LA POLAR", "MERCADO LIBRE", "CORREOS", "CHILEXPRESS",
            "STARKEN"),
    )

    private val ESPACIOS = Regex("""\s+""")

    /**
     * Propone una categoria a partir de lo que se leyo de la boleta.
     *
     * Devuelve tambien cuantas palabras calzaron: cero significa que no
     * reconocio nada y cayo en la de por defecto, y eso la pantalla lo dice
     * distinto para que el usuario sepa que ahi si tiene que mirar.
     */
    data class Propuesta(val categoria: Categoria, val aciertos: Int) {
        val segura: Boolean get() = aciertos > 0
    }

    fun proponer(lineas: List<String>): Propuesta {
        val texto = lineas.joinToString(" ") { sinTildes(it).uppercase() }
            .replace(ESPACIOS, " ")
        var mejor = POR_DEFECTO
        var puntos = 0
        for ((nombre, palabras) in REGLAS) {
            val p = palabras.count { texto.contains(it) }
            if (p > puntos) { puntos = p; mejor = porNombre(nombre) }
        }
        return Propuesta(mejor, puntos)
    }

    private fun sinTildes(s: String): String = s
        .replace('Á', 'A').replace('É', 'E').replace('Í', 'I')
        .replace('Ó', 'O').replace('Ú', 'U').replace('Ñ', 'N')
        .replace('á', 'A').replace('é', 'E').replace('í', 'I')
        .replace('ó', 'O').replace('ú', 'U').replace('ñ', 'N')
}
