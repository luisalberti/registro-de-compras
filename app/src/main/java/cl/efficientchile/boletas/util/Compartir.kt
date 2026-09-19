package cl.efficientchile.boletas.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Entrega el Excel al usuario por el menu de compartir de Android.
 *
 * Se usa el menu de compartir en vez de escribir directo en Descargas porque
 * asi el usuario elige que hacer con el archivo —mandarlo por correo, subirlo
 * a Drive, guardarlo— sin que la app pida el permiso de escribir en todo el
 * almacenamiento, que asusta y que aca no hace falta.
 *
 * El archivo sale por FileProvider: pasar un File:// directo a otra app tira
 * FileUriExposedException desde Android 7, y esta corre en 12.
 */
object Compartir {
    fun excel(ctx: Context, archivo: File) {
        val uri = FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", archivo)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, archivo.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(
            Intent.createChooser(intent, "Compartir el Excel")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
