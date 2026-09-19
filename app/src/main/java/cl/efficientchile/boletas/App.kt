package cl.efficientchile.boletas

import android.app.Application
import cl.efficientchile.boletas.data.Almacen

/** Una sola instancia del almacen para toda la app. */
class App : Application() {
    val almacen: Almacen by lazy { Almacen(this) }
}
