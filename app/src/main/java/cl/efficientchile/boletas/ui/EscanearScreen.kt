package cl.efficientchile.boletas.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import cl.efficientchile.boletas.util.Fotos
import cl.efficientchile.boletas.util.LectorBoleta
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File

/**
 * La camara para fotografiar la boleta.
 *
 * Toma la foto, la lee en el propio telefono (sin señal, sin costo por uso) y
 * entrega lo que reconocio. No guarda nada: eso lo decide el usuario en la
 * pantalla de revisar, con los campos a la vista.
 */
@Composable
fun EscanearScreen(
    onLeido: (LectorBoleta.Lectura) -> Unit,
    onCancelar: () -> Unit,
) {
    val ctx = LocalContext.current
    val dueno = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var permiso by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED)
    }
    val pedir = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { permiso = it }
    LaunchedEffect(Unit) { if (!permiso) pedir.launch(Manifest.permission.CAMERA) }

    var captura by remember { mutableStateOf<ImageCapture?>(null) }
    var trabajando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fotografía el documento") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Marino, titleContentColor = Blanco),
                actions = { TextButton(onClick = onCancelar) { Text("Cancelar", color = Blanco) } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!permiso) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("La app necesita la cámara para leer las boletas.")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { pedir.launch(Manifest.permission.CAMERA) }) {
                        Text("Dar permiso")
                    }
                }
                return@Column
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c ->
                        val vista = PreviewView(c)
                        val futuro = ProcessCameraProvider.getInstance(c)
                        futuro.addListener({
                            val prov = futuro.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(vista.surfaceProvider)
                            }
                            val cap = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                .build()
                            captura = cap
                            prov.unbindAll()
                            prov.bindToLifecycle(
                                dueno, CameraSelector.DEFAULT_BACK_CAMERA, preview, cap)
                        }, ContextCompat.getMainExecutor(c))
                        vista
                    },
                )
                if (trabajando) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Blanco)
                            Spacer(Modifier.height(12.dp))
                            Text("Leyendo…", color = Blanco)
                        }
                    }
                }
            }

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Apoya el papel en algo plano, que entre entero y sin sombra. " +
                        "Da lo mismo si sale algo torcido.",
                    style = MaterialTheme.typography.bodySmall, color = TintaSuave)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    enabled = !trabajando && captura != null,
                    onClick = {
                        trabajando = true; error = null
                        val crudo = File(ctx.cacheDir, "b_${System.currentTimeMillis()}.jpg")
                        captura!!.takePicture(
                            ImageCapture.OutputFileOptions.Builder(crudo).build(),
                            ContextCompat.getMainExecutor(ctx),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(o: ImageCapture.OutputFileResults) {
                                    scope.launch {
                                        try {
                                            val texto = withContext(Dispatchers.IO) {
                                                val chico = File(ctx.cacheDir,
                                                    "c_${System.currentTimeMillis()}.jpg")
                                                Fotos.comprimir(crudo, chico)
                                                crudo.delete()
                                                val t = reconocer(chico)
                                                chico.delete()   // la foto no se guarda
                                                t
                                            }
                                            onLeido(LectorBoleta.leer(texto))
                                        } catch (e: Exception) {
                                            error = e.message ?: "No se pudo leer la foto"
                                        } finally {
                                            trabajando = false
                                        }
                                    }
                                }
                                override fun onError(e: ImageCaptureException) {
                                    error = e.message ?: "La cámara falló"
                                    trabajando = false
                                }
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Tomar la foto", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

private suspend fun reconocer(foto: File): String {
    val bmp = BitmapFactory.decodeFile(foto.absolutePath)
        ?: throw IllegalStateException("No se pudo abrir la foto")
    val lector = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    return suspendCancellableCoroutine { cont ->
        lector.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { cont.resumeWith(Result.success(it.text)) }
            .addOnFailureListener { cont.resumeWith(Result.failure(it)) }
    }
}
