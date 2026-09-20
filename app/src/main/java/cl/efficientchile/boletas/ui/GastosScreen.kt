package cl.efficientchile.boletas.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.efficientchile.boletas.data.Documento
import cl.efficientchile.boletas.util.Periodos

/**
 * Los gastos, ordenados por dia, semana o mes.
 *
 * El criterio de esta pantalla: un total que no se puede desarmar no sirve
 * para revisar nada. Por eso cada periodo se abre y muestra los documentos
 * que lo componen, y debajo lo mas comprado del periodo. Ver "gastaste
 * 340.000 en septiembre" sin poder preguntar en que, es un dato inutil.
 *
 * La barra comparativa de cada periodo se mide contra el periodo mas caro de
 * la lista, no contra una escala inventada: asi el largo significa algo.
 */
@Composable
fun GastosScreen(
    docs: List<Documento>,
    onVolver: () -> Unit,
) {
    var tramo by remember { mutableStateOf(Periodos.Tramo.MES) }
    var abierto by remember { mutableStateOf<String?>(null) }

    val grupos = remember(docs.size, tramo) { Periodos.agrupar(docs, tramo) }
    val techo = remember(grupos) { grupos.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1 }
    val total = remember(grupos) { grupos.sumOf { it.total } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis gastos") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Marino, titleContentColor = Blanco),
                actions = { TextButton(onClick = onVolver) { Text("Volver", color = Blanco) } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {

            // El acumulado del conjunto, arriba. Es la cifra que se mira
            // primero, asi que va sola y grande.
            Surface(color = Marino, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text("Total registrado", color = CyanPalido,
                        style = MaterialTheme.typography.bodySmall)
                    Text(clp(total), color = Blanco,
                        style = MaterialTheme.typography.displaySmall)
                    Text("${docs.size} documentos", color = CyanPalido,
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    Periodos.Tramo.DIA to "Día",
                    Periodos.Tramo.SEMANA to "Semana",
                    Periodos.Tramo.MES to "Mes",
                ).forEach { (t, etiqueta) ->
                    val sel = tramo == t
                    Surface(
                        onClick = { tramo = t; abierto = null },
                        color = if (sel) CyanPalido else Blanco,
                        border = BorderStroke(if (sel) 2.dp else 1.dp,
                            if (sel) CyanProfundo else Borde),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f).height(44.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(etiqueta, color = if (sel) Marino else Tinta,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }

            if (grupos.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Todavía no hay nada que resumir.",
                        style = MaterialTheme.typography.titleMedium, color = TintaSuave)
                }
                return@Column
            }

            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(grupos, key = { it.clave.ifEmpty { "sin-fecha" } }) { g ->
                    Periodo(
                        g = g,
                        techo = techo,
                        abierto = abierto == g.clave.ifEmpty { "sin-fecha" },
                        onTocar = {
                            val k = g.clave.ifEmpty { "sin-fecha" }
                            abierto = if (abierto == k) null else k
                        },
                    )
                    HorizontalDivider()
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun Periodo(
    g: Periodos.Grupo,
    techo: Int,
    abierto: Boolean,
    onTocar: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Surface(onClick = onTocar, color = Blanco, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(g.titulo, style = MaterialTheme.typography.titleMedium, color = Marino)
                        Text(g.subtitulo, style = MaterialTheme.typography.bodySmall,
                            color = TintaSuave)
                    }
                    Text(clp(g.total), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = Tinta)
                    Icon(
                        if (abierto) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        null, tint = TintaSuave)
                }
                Spacer(Modifier.height(8.dp))
                // La barra va sobre gris claro y se llena en cyan profundo:
                // sobre blanco el cyan claro no alcanza el minimo de contraste
                // y la barra se pierde.
                Box(
                    Modifier.fillMaxWidth().height(6.dp)
                        .clip(MaterialTheme.shapes.small).background(Nieve)
                ) {
                    Box(
                        Modifier.fillMaxHeight()
                            .fillMaxWidth(g.total.toFloat() / techo.toFloat())
                            .clip(MaterialTheme.shapes.small).background(CyanProfundo)
                    )
                }
            }
        }

        AnimatedVisibility(visible = abierto) {
            Column(Modifier.fillMaxWidth().background(Nieve).padding(16.dp)) {

                val compras = Periodos.masComprado(g.documentos)
                if (compras.isNotEmpty()) {
                    Text("Lo que más pesó", style = MaterialTheme.typography.titleSmall,
                        color = Marino)
                    Spacer(Modifier.height(6.dp))
                    compras.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(c.descripcion, style = MaterialTheme.typography.bodyMedium,
                                    color = Tinta)
                                Text("${c.cantidad} unidades · ${c.veces} veces",
                                    style = MaterialTheme.typography.bodySmall, color = TintaSuave)
                            }
                            Text(clp(c.monto), style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold, color = Tinta)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                } else {
                    Text(
                        "Estos documentos no traen detalle de productos, solo el monto.",
                        style = MaterialTheme.typography.bodySmall, color = TintaSuave)
                    Spacer(Modifier.height(14.dp))
                }

                Text("Documentos del período", style = MaterialTheme.typography.titleSmall,
                    color = Marino)
                Spacer(Modifier.height(6.dp))
                g.documentos.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                buildString {
                                    append(d.tipo)
                                    if (d.numero.isNotEmpty()) append("  N° ${d.numero}")
                                },
                                style = MaterialTheme.typography.bodyMedium, color = Tinta)
                            val sub = buildString {
                                if (d.fecha.isNotEmpty()) append(d.fecha)
                                if (d.items.isNotEmpty()) {
                                    if (isNotEmpty()) append(" · ")
                                    append("${d.items.size} productos")
                                }
                            }
                            if (sub.isNotEmpty()) Text(sub,
                                style = MaterialTheme.typography.bodySmall, color = TintaSuave)
                        }
                        Text(d.total?.let { clp(it) } ?: "—",
                            style = MaterialTheme.typography.bodyMedium, color = Tinta)
                    }
                }
            }
        }
    }
}
