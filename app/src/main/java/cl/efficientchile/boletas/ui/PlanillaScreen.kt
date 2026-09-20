package cl.efficientchile.boletas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.efficientchile.boletas.util.Categorias
import cl.efficientchile.boletas.util.Nube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Mirar y corregir la planilla desde el teléfono.
 *
 * ## Por qué los números se piden y no se calculan aquí
 *
 * La app podría sumar sus propios documentos y mostrar un total. Sería más
 * rápido y estaría mal: en la planilla también hay movimientos tecleados en
 * el computador, gastos fijos y sueldos que nunca pasaron por el teléfono.
 * Un resumen que solo ve la mitad de la plata no es un resumen. Por eso el
 * total lo calcula la planilla, que es la que tiene todo.
 *
 * ## Corregir desde acá
 *
 * Una categoría equivocada se arregla en dos toques, y esa corrección viaja
 * de vuelta al aprendizaje: la próxima boleta de ese comercio ya sale bien.
 * Es el lugar donde la app se vuelve más lista sola.
 */
@Composable
fun PlanillaScreen(url: String, onVolver: () -> Unit) {
    val scope = rememberCoroutineScope()
    val hoy = remember { Calendar.getInstance() }
    var anio by remember { mutableStateOf(hoy.get(Calendar.YEAR)) }
    var mes by remember { mutableStateOf(hoy.get(Calendar.MONTH) + 1) }

    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var resumen by remember { mutableStateOf<Nube.Resumen?>(null) }
    val movs = remember { mutableStateListOf<Nube.Movimiento>() }
    var editando by remember { mutableStateOf<Nube.Movimiento?>(null) }
    var trabajando by remember { mutableStateOf(false) }

    suspend fun recargar() {
        cargando = true; error = null
        val r = withContext(Dispatchers.IO) { Nube.resumen(url, anio, mes) }
        val m = withContext(Dispatchers.IO) { Nube.movimientos(url, 40) }
        resumen = r
        movs.clear(); movs.addAll(m)
        if (r == null) error = "No pude leer la planilla. Revisa la conexión."
        cargando = false
    }

    LaunchedEffect(anio, mes) { recargar() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi planilla") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Marino, titleContentColor = Blanco),
                actions = {
                    IconButton(onClick = { scope.launch { recargar() } }) {
                        Icon(Icons.Default.Refresh, "Actualizar", tint = Blanco)
                    }
                    TextButton(onClick = onVolver) { Text("Volver", color = Blanco) }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {

            // ---- el mes que se está mirando ----
            Surface(color = Marino, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = {
                        if (mes == 1) { mes = 12; anio-- } else mes--
                    }) { Text("‹ anterior", color = CyanPalido) }
                    Text("${MESES[mes - 1]} $anio", color = Blanco,
                        style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = {
                        if (mes == 12) { mes = 1; anio++ } else mes++
                    }) { Text("siguiente ›", color = CyanPalido) }
                }
            }

            if (cargando) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CyanProfundo)
                }
            }

            error?.let {
                Surface(color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(14.dp))
                }
            }

            resumen?.let { r ->
                Surface(color = CyanPalido, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Cifra("Entró", r.ingresos, grande = false)
                        Cifra("Salió", r.egresos, grande = false)
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Cifra("Queda", r.saldo, grande = true)
                        if (r.ahorro > 0 || r.deuda > 0) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                buildString {
                                    if (r.ahorro > 0) append("De eso, ${clp(r.ahorro)} fue ahorro")
                                    if (r.deuda > 0) {
                                        if (isNotEmpty()) append(" · ")
                                        append("${clp(r.deuda)} pago de cuotas")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall, color = TintaSuave)
                        }
                    }
                }
                if (r.categorias.isNotEmpty()) {
                    Text("En qué se fue este mes",
                        style = MaterialTheme.typography.titleSmall, color = Marino,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                    r.categorias.take(5).forEach { (cat, monto) ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(cat, style = MaterialTheme.typography.bodyMedium, color = Tinta)
                            Text(clp(monto), style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold, color = Tinta)
                        }
                    }
                }
            }

            Text("Últimos movimientos · toca uno para corregirlo",
                style = MaterialTheme.typography.titleSmall, color = Marino,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))

            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(movs, key = { it.id }) { m ->
                    Surface(onClick = { editando = m }, color = Blanco,
                        modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(m.categoria, style = MaterialTheme.typography.bodyLarge,
                                    color = Marino)
                                Text(
                                    buildString {
                                        append(m.fecha)
                                        if (m.detalle.isNotEmpty()) append(" · ${m.detalle}")
                                    },
                                    style = MaterialTheme.typography.bodySmall, color = TintaSuave)
                            }
                            Text(
                                (if (m.tipo.equals("INGRESO", true)) "+" else "") + clp(m.monto),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (m.tipo.equals("INGRESO", true)) Bien else Tinta)
                        }
                    }
                    HorizontalDivider()
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    editando?.let { m ->
        EditarMovimiento(
            mov = m,
            trabajando = trabajando,
            onCerrar = { editando = null },
            onCategoria = { cat ->
                trabajando = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        Nube.editar(url, m.id, cat.nombre, m.detalle)
                    }
                    trabajando = false
                    if (r.ok) { editando = null; recargar() } else error = r.mensaje
                }
            },
            onBorrar = {
                trabajando = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) { Nube.borrar(url, m.id) }
                    trabajando = false
                    if (r.ok) { editando = null; recargar() } else error = r.mensaje
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditarMovimiento(
    mov: Nube.Movimiento,
    trabajando: Boolean,
    onCerrar: () -> Unit,
    onCategoria: (Categorias.Categoria) -> Unit,
    onBorrar: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onCerrar, containerColor = Blanco) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("${mov.fecha}  ·  ${clp(mov.monto)}",
                    style = MaterialTheme.typography.titleMedium, color = Marino)
                if (mov.detalle.isNotEmpty()) {
                    Text(mov.detalle, style = MaterialTheme.typography.bodySmall,
                        color = TintaSuave)
                }
                Spacer(Modifier.height(4.dp))
                Text("Ahora está en ${mov.categoria}. Elige otra y la planilla "
                    + "además aprende dónde va este comercio.",
                    style = MaterialTheme.typography.bodySmall, color = TintaSuave)
            }
            if (trabajando) {
                Box(Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp,
                        color = CyanProfundo)
                }
                return@Column
            }
            Spacer(Modifier.height(8.dp))
            var grupoAnterior = ""
            Categorias.TODAS.forEach { c ->
                if (c.grupo != grupoAnterior) {
                    grupoAnterior = c.grupo
                    Text(c.grupo, style = MaterialTheme.typography.bodySmall,
                        color = CyanProfundo, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(
                            start = 20.dp, end = 20.dp, top = 10.dp, bottom = 2.dp))
                }
                Surface(
                    onClick = { onCategoria(c) },
                    color = if (c.nombre == mov.categoria) CyanPalido else Blanco,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(c.nombre, style = MaterialTheme.typography.bodyLarge, color = Tinta,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 11.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onBorrar, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("Borrar este movimiento de la planilla",
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun Cifra(etiqueta: String, monto: Int, grande: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(etiqueta,
            style = if (grande) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.bodyMedium,
            color = TintaSuave)
        Text(clp(monto),
            style = if (grande) MaterialTheme.typography.headlineMedium
                    else MaterialTheme.typography.titleSmall,
            color = if (grande && monto < 0) Alerta else Marino)
    }
}

private val MESES = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
