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
import cl.efficientchile.boletas.ui.AjustesScreen
import cl.efficientchile.boletas.ui.EscanearScreen
import cl.efficientchile.boletas.ui.GastosScreen
import cl.efficientchile.boletas.ui.HomeScreen
import cl.efficientchile.boletas.ui.RevisarScreen
import cl.efficientchile.boletas.ui.TemaInventario
import cl.efficientchile.boletas.util.Compartir
import cl.efficientchile.boletas.util.LectorBoleta
import cl.efficientchile.boletas.util.Nube
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
    data object Gastos : Pantalla()
    data object Ajustes : Pantalla()
    data object Manual : Pantalla()
    data class Revisar(val lectura: LectorBoleta.Lectura) : Pantalla()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        limpiarFotos()
        enableEdgeToEdge()
        setContent { TemaInventario { Surface(Modifier.fillMaxSize()) { AppRoot() } } }
    }

    /**
     * Barre las fotos que hayan quedado dando vueltas.
     *
     * La foto se borra apenas se extrae el texto, asi que en condiciones
     * normales no queda ninguna. Pero si la app se cerro justo mientras leia
     * —bateria, una llamada, el sistema matando el proceso— el archivo quedo
     * ahi. Fotos de boletas acumulandose sin que nadie las pidio no es un
     * problema de espacio: es informacion de compras guardada de mas.
     *
     * Tambien se lleva los .xlsx exportados: ya se compartieron, ya cumplieron.
     */
    private fun limpiarFotos() {
        try {
            cacheDir.listFiles()?.forEach { f ->
                val n = f.name
                if (n.endsWith(".jpg") || n.endsWith(".xlsx")) f.delete()
            }
        } catch (e: Exception) {
            // No poder limpiar la cache jamas debe impedir que la app abra.
        }
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
    var subiendo by remember { mutableStateOf(false) }
    var avisoNube by remember { mutableStateOf<String?>(null) }

    fun persistir() = almacen.guardar(docs.toList())

    /**
     * Manda a la planilla todo lo que todavia no subio.
     *
     * Se llama al guardar una boleta y tambien al abrir la app. Ese segundo
     * caso es el que importa: si escaneaste en la calle sin señal, la boleta
     * quedo pendiente, y lo unico que la subiria es que alguien lo reintente
     * despues. Pedirle al usuario que se acuerde de apretar un boton es
     * pedirle que haga el trabajo del programa.
     */
    suspend fun sincronizar() {
        if (!ctx.ajustes.haySincronizacion) return
        val url = ctx.ajustes.urlPlanilla
        val pendientes = docs.filter { !it.subido }
        if (pendientes.isEmpty()) return
        subiendo = true
        try {
            for (d in pendientes) {
                val r = withContext(Dispatchers.IO) { Nube.enviar(url, d) }
                if (r.ok) {
                    val i = docs.indexOfFirst { it.id == d.id }
                    if (i >= 0) docs[i] = docs[i].copy(subido = true)
                    persistir()
                } else if (r.reintentable) {
                    // Sin señal: no tiene sentido insistir con las que siguen.
                    break
                } else {
                    // Algo esta mal configurado; insistir solo gasta bateria.
                    avisoNube = r.mensaje
                    break
                }
            }
        } finally {
            subiendo = false
        }
    }

    // Al abrir la app se reintenta lo que quedo pendiente de la calle. Va
    // DESPUES de declarar sincronizar(): en Kotlin una funcion local no
    // existe antes de su declaracion.
    LaunchedEffect(Unit) { sincronizar() }

    when (val p = pantalla) {
        is Pantalla.Inicio -> HomeScreen(
            docs = docs,
            exportando = exportando,
            onEscanear = { pantalla = Pantalla.Escanear },
            onGastos = { pantalla = Pantalla.Gastos },
            onAjustes = { pantalla = Pantalla.Ajustes },
            onManual = { pantalla = Pantalla.Manual },
            pendientes = if (ctx.ajustes.haySincronizacion) docs.count { !it.subido } else -1,
            subiendo = subiendo,
            avisoNube = avisoNube,
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

        is Pantalla.Ajustes -> AjustesScreen(
            urlActual = ctx.ajustes.urlPlanilla,
            onGuardar = { u ->
                ctx.ajustes.urlPlanilla = u
                avisoNube = null
                scope.launch { sincronizar() }
            },
            onVolver = { pantalla = Pantalla.Inicio },
        )

        is Pantalla.Gastos -> GastosScreen(
            docs = docs,
            onVolver = { pantalla = Pantalla.Inicio },
        )

        /* Compra a mano: la misma pantalla de revisar, con una lectura vacia
           y la fecha de hoy puesta. Una compra en efectivo sin comprobante
           necesita los mismos campos que una escaneada. */
        is Pantalla.Manual -> RevisarScreen(
            lectura = LectorBoleta.Lectura(
                fecha = SimpleDateFormat("yyyy-MM-dd", Locale("es", "CL")).format(Date())),
            manual = true,
            onGuardar = { doc ->
                docs.add(0, doc)
                persistir()
                scope.launch { sincronizar() }
                // A diferencia del escaneo, aca se vuelve al inicio: teclear
                // una compra es un acto suelto, no una pila.
                pantalla = Pantalla.Inicio
            },
            onCancelar = { pantalla = Pantalla.Inicio },
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
                scope.launch { sincronizar() }
                // Vuelve directo a la camara: registrar una pila es escanear
                // una tras otra, no volver al inicio entre cada una.
                pantalla = Pantalla.Escanear
            },
            onCancelar = { pantalla = Pantalla.Inicio },
        )
    }
}
