package ar.ignacio.duna

import android.content.Context

/** Decide qué dominios se bloquean: lista base + los que agregás o permitís desde la app. */
object Filtro {
    private var base: Set<String> = emptySet()
    @Volatile private var propios: Set<String> = emptySet()
    @Volatile private var permitidos: Set<String> = emptySet()

    fun cargar(ctx: Context) {
        if (base.isEmpty()) {
            base = ctx.assets.open("bloqueados.txt").bufferedReader().readLines()
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toSet()
        }
        val pr = prefs(ctx)
        propios = pr.getStringSet("bloqueados", emptySet())!!.toSet()
        permitidos = pr.getStringSet("permitidos", emptySet())!!.toSet()
    }

    fun bloqueado(dominio: String): Boolean =
        !coincide(dominio, permitidos) && (coincide(dominio, base) || coincide(dominio, propios))

    /** Si estaba bloqueado lo permite, y al revés. */
    fun alternar(ctx: Context, dominio: String) {
        if (bloqueado(dominio)) {
            permitidos = permitidos + dominio
            propios = propios - dominio
        } else {
            propios = propios + dominio
            permitidos = permitidos - dominio
        }
        prefs(ctx).edit()
            .putStringSet("bloqueados", propios)
            .putStringSet("permitidos", permitidos)
            .apply()
    }

    // "ads.ejemplo.com" coincide si en la lista está "ads.ejemplo.com" o "ejemplo.com"
    private fun coincide(dominio: String, lista: Set<String>): Boolean {
        var d = dominio
        while (true) {
            if (d in lista) return true
            val punto = d.indexOf('.')
            if (punto < 0) return false
            d = d.substring(punto + 1)
        }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("duna", Context.MODE_PRIVATE)
}
