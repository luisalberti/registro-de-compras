package cl.efficientchile.boletas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.efficientchile.boletas.data.Documento

/**
 * La pantalla principal: la pila de documentos ya registrados, con lo que
 * cada uno suma, y los dos botones que importan: escanear otro y exportar.
 *
 * El total acumulado va arriba, grande, porque es lo que el usuario quiere
 * saber de un vistazo mientras registra una pila: cuanto lleva sumado.
 */
@Composable
fun HomeScreen(
    docs: List<Documento>,
    onEscanear: () -> Unit,
    onExportar: () -> Unit,
    onGastos: () -> Unit,
    onBorrar: (Documento) -> Unit,
    exportando: Boolean,
) {
    val sumaTotal = docs.sumOf { it.total ?: 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Boletas y vouchers") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Marino, titleContentColor = Blanco),
                actions = {
                    if (docs.isNotEmpty()) {
                        TextButton(onClick = onGastos) { Text("Mis gastos", color = Blanco) }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onEscanear,
                containerColor = Marino, contentColor = Blanco,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Escanear") },
            )
        },
        bottomBar = {
            if (docs.isNotEmpty()) {
                Surface(shadowElevation = 8.dp, color = Blanco) {
                    Button(
                        onClick = onExportar,
                        enabled = !exportando,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanProfundo),
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                    ) {
                        if (exportando) CircularProgressIndicator(
                            Modifier.size(20.dp), strokeWidth = 2.dp, color = Blanco)
                        else Text("Exportar Excel (${docs.size})",
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (docs.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Todavía no hay documentos.",
                        style = MaterialTheme.typography.titleMedium, color = TintaSuave)
                    Spacer(Modifier.height(8.dp))
                    Text("Aprieta Escanear y fotografía la primera boleta.",
                        style = MaterialTheme.typography.bodyMedium, color = TintaSuave)
                }
                return@Column
            }

            // La franja del total lleva a los gastos: es el gesto natural.
            // Quien mira el acumulado es porque quiere desarmarlo.
            Surface(onClick = onGastos, color = CyanPalido) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("${docs.size} documentos", color = TintaSuave)
                        Text("Total acumulado · ver por día, semana y mes",
                            style = MaterialTheme.typography.bodySmall, color = TintaSuave)
                    }
                    Text(clp(sumaTotal), style = MaterialTheme.typography.headlineMedium,
                        color = Marino)
                }
            }

            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(docs, key = { it.id }) { d ->
                    Fila(d, onBorrar)
                    HorizontalDivider()
                }
                item { Spacer(Modifier.height(90.dp)) }   // espacio para el FAB
            }
        }
    }
}

@Composable
private fun Fila(d: Documento, onBorrar: (Documento) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.tipo, style = MaterialTheme.typography.titleSmall, color = Marino)
                if (d.numero.isNotEmpty()) {
                    Text("  N° ${d.numero}", style = MaterialTheme.typography.bodySmall,
                        color = TintaSuave)
                }
            }
            val sub = buildString {
                if (d.fecha.isNotEmpty()) append(d.fecha)
                if (d.rut.isNotEmpty()) { if (isNotEmpty()) append(" · "); append(d.rut) }
            }
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall,
                color = TintaSuave)
            if (d.avisos.isNotEmpty()) {
                Text("⚠ ${d.avisos}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
        Text(d.total?.let { clp(it) } ?: "—",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = { onBorrar(d) }) {
            Icon(Icons.Default.Delete, "Borrar", tint = MaterialTheme.colorScheme.error)
        }
    }
}
