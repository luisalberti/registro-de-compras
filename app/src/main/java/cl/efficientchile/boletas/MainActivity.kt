package cl.efficientchile.boletas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxSize
import cl.efficientchile.boletas.data.Documento
import cl.efficientchile.boletas.export.Excel
import cl.efficientchile.boletas.ui.EscanearScreen
import cl.efficientchile.boletas.ui.HomeScreen
import cl.efficientchile.boletas.ui.RevisarScreen
import cl.efficientchile.boletas.ui.TemaInventario
import cl.efficientchile.boletas.util.Compartir
import cl.efficientchile.boletas.util.LectorBoleta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed class Pantalla {
    data object Inicio : Pantalla()
    data object Escanear : Pantalla()
    data class Revisar(val lectura: LectorBoleta.Lectura) : Pantalla()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TemaInventario { Surface(Modifier.fillMaxSize()) { AppRoot() } } }
    }
}

@Composable
private fun AppRoot() {
    val ctx = LocalContext.current.applicationContext as App
    val almacen = ctx.almacen
    val scope = rememberCoroutineScope()

    // La lista arranca de lo guardado en el telefono: cerrar la app no pierde
    // lo registrado.
    val docs = remember { mutableStateListOf<Documento>().apply { addAll(almacen.cargar()) } }
    var pantalla by remember { mutableStateOf<Pantalla>(Pantalla.Inicio) }
    var exportando by remember { mutableStateOf(false) }

    fun persistir() = almacen.guardar(docs.toList())

    when (val p = pantalla) {
        is Pantalla.Inicio -> HomeScreen(
            docs = docs,
            exportando = exportando,
            onEscanear = { pantalla = Pantalla.Escanear },
            onBorrar = { d -> docs.remove(d); persistir() },
            onExportar = {
                exportando = true
                scope.launch {
                    try {
                        val archivo = withContext(Dispatchers.IO) {
                            val marca = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale("es", "CL"))
                                .format(Date())
                            val f = File(ctx.cacheDir, "boletas_$marca.xlsx")
                            Excel.escribir(f, docs.toList())
                            f
                        }
                        Compartir.excel(ctx, archivo)
                    } finally {
                        exportando = false
                    }
                }
            },
        )

        is Pantalla.Escanear -> EscanearScreen(
            onLeido = { lectura -> pantalla = Pantalla.Revisar(lectura) },
            onCancelar = { pantalla = Pantalla.Inicio },
        )

        is Pantalla.Revisar -> RevisarScreen(
            lectura = p.lectura,
            onGuardar = { doc ->
                docs.add(0, doc)     // el mas nuevo arriba
                persistir()
                // Vuelve directo a la camara: registrar una pila es escanear
                // una tras otra, no volver al inicio entre cada una.
                pantalla = Pantalla.Escanear
            },
            onCancelar = { pantalla = Pantalla.Inicio },
        )
    }
}
