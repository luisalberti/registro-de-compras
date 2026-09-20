package cl.efficientchile.boletas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cl.efficientchile.boletas.data.Documento
import cl.efficientchile.boletas.util.Categorias
import cl.efficientchile.boletas.util.Formato
import cl.efficientchile.boletas.util.LectorBoleta

/**
 * Revisar y corregir lo que se leyo, antes de guardarlo.
 *
 * Este es el paso que hace que el lector sea util y no peligroso. Todo lo que
 * saco de la foto llega aca, editable, y con las advertencias a la vista. El
 * usuario confirma; recien ahi se guarda. Un numero que nadie miro es peor
 * que un campo vacio.
 */
@Composable
fun RevisarScreen(
    lectura: LectorBoleta.Lectura,
    onGuardar: (Documento) -> Unit,
    onCancelar: () -> Unit,
) {
    var tipo by remember { mutableStateOf(if (lectura.ultimos4 != null) "Voucher" else "Boleta") }
    var numero by remember { mutableStateOf(lectura.numero ?: "") }
    var rut by remember { mutableStateOf(lectura.rut ?: "") }
    var neto by remember { mutableStateOf(lectura.neto?.toString() ?: "") }
    var iva by remember { mutableStateOf(lectura.iva?.toString() ?: "") }
    var total by remember { mutableStateOf(lectura.total?.toString() ?: "") }
    var fecha by remember { mutableStateOf(lectura.fecha ?: "") }
    val tarjeta = lectura.ultimos4 ?: ""

    // El detalle se puede podar. Una linea que el lector invento —un pedazo de
    // la direccion leido como producto— tiene que poder botarse aca, porque
    // despues se va al Excel y ensucia el analisis. No se puede editar el
    // texto: corregir letra por letra en el telefono es mas lento que
    // arreglarlo despues en la planilla.
    val items = remember { mutableStateListOf<LectorBoleta.Item>().apply { addAll(lectura.items) } }

    // La categoria viene propuesta por el lector. Se muestra y se confirma:
    // clasificar mal y guardar en silencio ensucia el cashflow para siempre.
    var categoria by remember { mutableStateOf(Categorias.porNombre(lectura.categoria)) }
    var eligiendo by remember { mutableStateOf(false) }
    val sumaItems = items.sumOf { Math.round(it.valorTotal) }.toInt()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Revisa lo que se leyó") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Marino, titleContentColor = Blanco),
            )
        },
        bottomBar = {
            // Igual que en la pantalla de inicio: sin navigationBarsPadding el
            // boton de guardar queda tapado por la barra del telefono.
            Surface(
                shadowElevation = 8.dp, color = Blanco,
                modifier = Modifier.navigationBarsPadding(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onGuardar(
                                Documento(
                                    id = System.currentTimeMillis(),
                                    fecha = fecha.trim(),
                                    tipo = tipo,
                                    numero = numero.trim(),
                                    rut = rut.trim(),
                                    neto = neto.filter { it.isDigit() }.toIntOrNull(),
                                    iva = iva.filter { it.isDigit() }.toIntOrNull(),
                                    total = total.filter { it.isDigit() }.toIntOrNull(),
                                    tarjeta = tarjeta,
                                    formaPago = if (tarjeta.isNotEmpty()) "tarjeta" else "",
                                    avisos = lectura.avisos.joinToString(" · "),
                                    items = items.toList(),
                                    categoria = categoria.nombre,
                                    categoriaFila = categoria.fila,
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text("Guardar en ${categoria.nombre}",
                        style = MaterialTheme.typography.labelLarge) }
                    TextButton(onClick = onCancelar, modifier = Modifier.fillMaxWidth()) {
                        Text("Descartar")
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (lectura.avisos.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Revisa esto antes de guardar", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error)
                        lectura.avisos.forEach {
                            Text("· $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Surface(
                color = if (lectura.categoriaSegura) CyanPalido else Nieve,
                shape = MaterialTheme.shapes.medium,
                border = androidx.compose.foundation.BorderStroke(
                    2.dp, if (lectura.categoriaSegura) CyanProfundo else Borde),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        if (lectura.categoriaSegura) "Se va a guardar en"
                        else "No reconocí el comercio. Se va a guardar en",
                        style = MaterialTheme.typography.bodySmall, color = TintaSuave)
                    Spacer(Modifier.height(2.dp))
                    Text("${categoria.grupo}  ›  ${categoria.nombre}",
                        style = MaterialTheme.typography.titleMedium, color = Marino)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { eligiendo = true }) {
                            Text("Cambiar categoría")
                        }
                    }
                }
            }

            Text("¿Qué documento es?", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Boleta", "Factura", "Voucher").forEach { op ->
                    val sel = tipo == op
                    Surface(
                        onClick = { tipo = op },
                        color = if (sel) CyanPalido else Blanco,
                        border = androidx.compose.foundation.BorderStroke(
                            if (sel) 2.dp else 1.dp, if (sel) CyanProfundo else Borde),
                        shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Text(op, color = if (sel) Marino else Tinta,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }

            Campo("N° de documento u operación", numero, KeyboardType.Number) {
                numero = Formato.documento(it)
            }
            val rutMalo = rut.isNotBlank() && !Formato.rutValido(rut)
            OutlinedTextField(
                rut, { rut = Formato.rut(it) },
                label = { Text("RUT del emisor") }, placeholder = { Text("76.853.513-2") },
                isError = rutMalo,
                supportingText = if (rutMalo) { { Text("El dígito verificador no calza.") } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Campo("Neto", neto, KeyboardType.Number) { neto = Formato.monto(it) }
            Campo("IVA", iva, KeyboardType.Number) { iva = Formato.monto(it) }
            Campo("Total", total, KeyboardType.Number) { total = Formato.monto(it) }
            Campo("Fecha (yyyy-mm-dd)", fecha, KeyboardType.Number) {
                fecha = it.filter { c -> c.isDigit() || c == '-' }.take(10)
            }
            if (tarjeta.isNotEmpty()) {
                Text("Tarjeta terminada en $tarjeta",
                    style = MaterialTheme.typography.bodySmall, color = TintaSuave)
            }

            if (items.isNotEmpty()) {
                HorizontalDivider()
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Qué se compró", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    Text("${items.size} líneas", style = MaterialTheme.typography.bodySmall,
                        color = TintaSuave)
                }

                // La prueba de que el detalle se leyo bien: la suma de las
                // lineas tiene que dar el neto o el total. Si no da, se dice
                // aca, con el numero a la vista, y no en una advertencia
                // generica que nadie puede verificar.
                val netoNum = neto.filter { it.isDigit() }.toIntOrNull()
                val totalNum = total.filter { it.isDigit() }.toIntOrNull()
                val cuadra = (netoNum != null && Math.abs(sumaItems - netoNum) <= 2) ||
                    (totalNum != null && Math.abs(sumaItems - totalNum) <= 2)
                Surface(
                    color = if (cuadra) CyanPalido else MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (cuadra) "Las líneas suman ${clp(sumaItems)} y calza con el documento."
                        else "Las líneas suman ${clp(sumaItems)} y no calza con el neto ni " +
                            "con el total. Bota la línea que sobre o corrige el monto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (cuadra) Marino else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp),
                    )
                }

                items.toList().forEach { it ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(it.descripcion, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${it.cantidad} × ${clp(Math.round(it.valorUnitario).toInt())}" +
                                    "  =  ${clp(Math.round(it.valorTotal).toInt())}",
                                style = MaterialTheme.typography.bodySmall, color = TintaSuave)
                        }
                        TextButton(onClick = { items.remove(it) }) {
                            Text("Botar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Text(
                "Corrige lo que haga falta. Nada se guarda hasta que aprietes Guardar.",
                style = MaterialTheme.typography.bodySmall, color = TintaSuave)
            Spacer(Modifier.height(60.dp))
        }

        if (eligiendo) {
            SelectorCategoria(
                actual = categoria,
                onElegir = { categoria = it },
                onCerrar = { eligiendo = false },
            )
        }
    }
}

/**
 * La lista completa de categorias, agrupada como en la planilla.
 *
 * Se muestran las 23 y no solo las probables: cuando el usuario viene a
 * cambiar la categoria es justamente porque la propuesta no servia, y
 * esconderle opciones en ese momento es lo peor que se puede hacer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorCategoria(
    actual: Categorias.Categoria,
    onElegir: (Categorias.Categoria) -> Unit,
    onCerrar: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onCerrar, containerColor = Blanco) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text("¿Dónde va este gasto?",
                style = MaterialTheme.typography.titleMedium, color = Marino,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            var grupoAnterior = ""
            Categorias.TODAS.forEach { c ->
                if (c.grupo != grupoAnterior) {
                    grupoAnterior = c.grupo
                    Text(c.grupo, style = MaterialTheme.typography.bodySmall,
                        color = CyanProfundo, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(
                            start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp))
                }
                Surface(
                    onClick = { onElegir(c); onCerrar() },
                    color = if (c.nombre == actual.nombre) CyanPalido else Blanco,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(c.nombre, style = MaterialTheme.typography.bodyLarge,
                        color = Tinta,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun Campo(
    etiqueta: String, valor: String, tipo: KeyboardType, onCambio: (String) -> Unit,
) {
    OutlinedTextField(
        valor, onCambio, label = { Text(etiqueta) },
        keyboardOptions = KeyboardOptions(keyboardType = tipo),
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
}
