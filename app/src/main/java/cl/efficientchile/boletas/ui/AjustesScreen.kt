package cl.efficientchile.boletas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.efficientchile.boletas.util.Nube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Conectar la app con tu planilla de Google.
 *
 * Los pasos van escritos aca adentro, y no en un manual aparte, porque esto
 * se configura una sola vez y siempre en el momento en que uno no tiene el
 * manual a mano.
 *
 * El boton Probar existe por una razon concreta: una URL mal pegada, o un
 * script publicado sin acceso publico, falla en silencio. Es mejor
 * enterarse aca, con la planilla abierta al lado, que tres semanas despues
 * al descubrir que no subio nada.
 */
@Composable
fun AjustesScreen(
    urlActual: String,
    onGuardar: (String) -> Unit,
    onVolver: () -> Unit,
) {
    var url by remember { mutableStateOf(urlActual) }
    var probando by remember { mutableStateOf(false) }
    var resultado by remember { mutableStateOf<Nube.Resultado?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Planilla en la nube") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Marino, titleContentColor = Blanco),
                actions = { TextButton(onClick = onVolver) { Text("Volver", color = Blanco) } },
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp, color = Blanco,
                modifier = Modifier.navigationBarsPadding(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            probando = true; resultado = null
                            val u = url.trim()
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { Nube.probar(u) }
                                resultado = r
                                probando = false
                                if (r.ok) onGuardar(u)
                            }
                        },
                        enabled = !probando && url.trim().startsWith("https://"),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        if (probando) CircularProgressIndicator(
                            Modifier.size(20.dp), strokeWidth = 2.dp, color = Blanco)
                        else Text("Probar y guardar",
                            style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(
                        onClick = { url = ""; onGuardar(""); resultado = null },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Desconectar la planilla") }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Cada boleta que registres se va sola a tu planilla de Google. " +
                    "Llegas a la oficina, la abres en el computador y está al día.",
                style = MaterialTheme.typography.bodyMedium, color = Tinta)

            OutlinedTextField(
                url, { url = it; resultado = null },
                label = { Text("Dirección del script") },
                placeholder = { Text("https://script.google.com/macros/s/…/exec") },
                singleLine = false, minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            resultado?.let { r ->
                Surface(
                    color = if (r.ok) CyanPalido else MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (r.ok) "Conectada. Escribí una fila de prueba; bórrala si quieres."
                        else r.mensaje,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (r.ok) Marino else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(14.dp))
                }
            }

            HorizontalDivider()

            Text("Cómo se obtiene esa dirección",
                style = MaterialTheme.typography.titleMedium, color = Marino)
            Paso(1, "En el computador, crea una planilla nueva en Google Sheets.")
            Paso(2, "En el menú, entra a Extensiones y después a Apps Script.")
            Paso(3, "Borra lo que venga y pega el código que te pasaron. Guarda.")
            Paso(4, "Arriba a la derecha, aprieta Implementar y elige " +
                "Nueva implementación. El tipo es Aplicación web.")
            Paso(5, "En «Quién tiene acceso» elige Cualquier persona. " +
                "Este es el paso que más se salta, y sin él la app no puede escribir.")
            Paso(6, "Google te va a pedir permiso una vez. Acéptalo.")
            Paso(7, "Copia la dirección que termina en /exec y pégala aquí arriba.")

            Spacer(Modifier.height(8.dp))
            Surface(color = Nieve, shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Quien tenga esa dirección puede escribir en tu planilla. " +
                        "Guárdala como guardarías una contraseña.",
                    style = MaterialTheme.typography.bodySmall, color = TintaSuave,
                    modifier = Modifier.padding(14.dp))
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun Paso(n: Int, texto: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$n.", style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold, color = CyanProfundo)
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = Tinta)
    }
}
