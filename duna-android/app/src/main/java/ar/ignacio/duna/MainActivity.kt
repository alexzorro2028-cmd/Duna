package ar.ignacio.duna

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val cielo = 0xFF1F2B4F.toInt()
    private val noche = 0xFF141C36.toInt()
    private val arena = 0xFFE3B565.toInt()
    private val texto = 0xFFF2E8D8.toInt()
    private val apagado = 0xFF7D86A3.toInt()

    private lateinit var boton: TextView
    private lateinit var estado: TextView
    private lateinit var contador: TextView
    private lateinit var adaptador: BaseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Filtro.cargar(this)
        window.statusBarColor = cielo
        window.navigationBarColor = cielo

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(cielo)
            setPadding(dp(20), dp(28), dp(20), 0)
        }

        raiz.addView(TextView(this).apply {
            text = "Duna"
            textSize = 28f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            setTextColor(texto)
        })

        boton = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { alternarVpn() }
        }
        raiz.addView(boton, LinearLayout.LayoutParams(dp(170), dp(84)).apply { topMargin = dp(24) })

        estado = TextView(this).apply {
            textSize = 16f
            setTextColor(texto)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
        raiz.addView(estado)

        contador = TextView(this).apply {
            textSize = 15f
            setTextColor(arena)
            setPadding(0, dp(4), 0, dp(20))
        }
        raiz.addView(contador)

        raiz.addView(TextView(this).apply {
            text = "Actividad reciente. Tocá un dominio para bloquearlo o permitirlo."
            textSize = 13f
            setTextColor(apagado)
            setPadding(0, 0, 0, dp(6))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        adaptador = object : BaseAdapter() {
            override fun getCount() = Estado.recientes.size
            override fun getItem(pos: Int): Any = Estado.recientes[pos]
            override fun getItemId(pos: Int) = pos.toLong()
            override fun getView(pos: Int, vista: View?, padre: ViewGroup?): View {
                val t = (vista as? TextView) ?: TextView(this@MainActivity).apply {
                    textSize = 15f
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                }
                val e = Estado.recientes[pos]
                t.text = (if (e.bloqueado) "⛔  " else "✓  ") + e.dominio
                t.setTextColor(if (e.bloqueado) arena else texto)
                return t
            }
        }

        val lista = ListView(this).apply {
            adapter = adaptador
            setBackgroundColor(noche)
            divider = null
            setOnItemClickListener { _, _, pos, _ ->
                val e = Estado.recientes[pos]
                Filtro.alternar(this@MainActivity, e.dominio)
                e.bloqueado = Filtro.bloqueado(e.dominio)
                val msj = if (e.bloqueado) "Bloqueado: " else "Permitido: "
                Toast.makeText(this@MainActivity, msj + e.dominio, Toast.LENGTH_SHORT).show()
                adaptador.notifyDataSetChanged()
            }
        }
        raiz.addView(lista, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(raiz)
    }

    override fun onResume() {
        super.onResume()
        Estado.alCambiar = { refrescar() }
        refrescar()
    }

    override fun onPause() {
        Estado.alCambiar = null
        super.onPause()
    }

    private fun refrescar() {
        val activo = Estado.activo
        boton.text = if (activo) "Sí" else "No"
        boton.setTextColor(if (activo) noche else texto)
        boton.background = GradientDrawable().apply {
            cornerRadius = dp(42).toFloat()
            setColor(if (activo) arena else apagado)
        }
        estado.text = if (activo) "Duna está bloqueando anuncios" else "Duna está apagada: los anuncios se ven"
        contador.text = "${Estado.totalBloqueados} anuncios bloqueados"
        adaptador.notifyDataSetChanged()
    }

    private fun alternarVpn() {
        if (Estado.activo) {
            startService(Intent(this, DunaVpnService::class.java).setAction(DunaVpnService.ACCION_DETENER))
        } else {
            val permiso = VpnService.prepare(this)
            if (permiso != null) startActivityForResult(permiso, 1) else iniciarVpn()
        }
    }

    @Deprecated("Se usa la API clásica para no depender de librerías extra")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) iniciarVpn()
    }

    private fun iniciarVpn() {
        startService(Intent(this, DunaVpnService::class.java))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
