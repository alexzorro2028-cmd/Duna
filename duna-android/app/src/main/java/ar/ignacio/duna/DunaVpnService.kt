package ar.ignacio.duna

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

/**
 * "VPN" local: no manda tu tráfico a ningún servidor externo.
 * Solo intercepta las consultas DNS del teléfono y corta las de publicidad.
 */
class DunaVpnService : VpnService() {

    companion object {
        const val ACCION_DETENER = "ar.ignacio.duna.DETENER"
        private const val DNS_FALSO = "10.111.222.2"
        private const val DNS_REAL = "1.1.1.1"
    }

    private var tunel: ParcelFileDescriptor? = null
    private var salida: FileOutputStream? = null
    private var lector: Thread? = null
    private val trabajadores = Executors.newFixedThreadPool(16)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACCION_DETENER) {
            detener()
            stopSelf()
            return START_NOT_STICKY
        }
        if (tunel == null) iniciar()
        return START_STICKY
    }

    private fun iniciar() {
        Filtro.cargar(this)
        val builder = Builder()
            .setSession("Duna")
            .addAddress("10.111.222.1", 32)
            .addDnsServer(DNS_FALSO)
            .addRoute(DNS_FALSO, 32)
            .setBlocking(true)
        try { builder.addDisallowedApplication(packageName) } catch (e: Exception) { }
        val t = builder.establish() ?: return
        tunel = t
        salida = FileOutputStream(t.fileDescriptor)
        Estado.encendido(true)
        lector = Thread { leer(t) }.also { it.start() }
    }

    private fun leer(t: ParcelFileDescriptor) {
        val entrada = FileInputStream(t.fileDescriptor)
        val buf = ByteArray(32767)
        try {
            while (!Thread.currentThread().isInterrupted) {
                val n = entrada.read(buf)
                if (n <= 0) continue
                val paquete = buf.copyOf(n)
                trabajadores.execute { procesar(paquete) }
            }
        } catch (e: Exception) { }
    }

    private fun procesar(paquete: ByteArray) {
        val c = Paquetes.leerConsulta(paquete) ?: return
        if (Filtro.bloqueado(c.dominio)) {
            Estado.registrar(c.dominio, true)
            escribir(Paquetes.armarRespuesta(c, Paquetes.respuestaBloqueo(c.dns)))
            return
        }
        Estado.registrar(c.dominio, false)
        try {
            DatagramSocket().use { s ->
                protect(s)
                s.soTimeout = 5000
                s.send(DatagramPacket(c.dns, c.dns.size, InetAddress.getByName(DNS_REAL), 53))
                val r = ByteArray(4096)
                val dp = DatagramPacket(r, r.size)
                s.receive(dp)
                escribir(Paquetes.armarRespuesta(c, r.copyOf(dp.length)))
            }
        } catch (e: Exception) { }
    }

    @Synchronized
    private fun escribir(p: ByteArray) {
        try { salida?.write(p) } catch (e: Exception) { }
    }

    private fun detener() {
        lector?.interrupt()
        lector = null
        try { tunel?.close() } catch (e: Exception) { }
        tunel = null
        salida = null
        Estado.encendido(false)
    }

    override fun onRevoke() {
        detener()
        super.onRevoke()
    }

    override fun onDestroy() {
        detener()
        trabajadores.shutdownNow()
        super.onDestroy()
    }
}
