package ar.ignacio.duna

import android.os.Handler
import android.os.Looper

/** Lo que muestra la pantalla: si está activo, cuántos bloqueó y la actividad reciente. */
object Estado {
    class Entrada(val dominio: String, var bloqueado: Boolean)

    private val principal = Handler(Looper.getMainLooper())
    val recientes = ArrayList<Entrada>()
    var totalBloqueados = 0
    var activo = false
    var alCambiar: (() -> Unit)? = null

    fun registrar(dominio: String, bloqueado: Boolean) {
        principal.post {
            recientes.removeAll { it.dominio == dominio }
            recientes.add(0, Entrada(dominio, bloqueado))
            if (recientes.size > 80) recientes.removeAt(recientes.size - 1)
            if (bloqueado) totalBloqueados++
            alCambiar?.invoke()
        }
    }

    fun encendido(valor: Boolean) {
        principal.post {
            activo = valor
            alCambiar?.invoke()
        }
    }
}
