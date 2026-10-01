package ar.ignacio.duna

/** Lee consultas DNS que llegan por el túnel y arma los paquetes de respuesta. */
object Paquetes {

    class Consulta(
        val ipOrigen: ByteArray,
        val ipDestino: ByteArray,
        val puertoOrigen: Int,
        val puertoDestino: Int,
        val dns: ByteArray,
        val dominio: String
    )

    fun leerConsulta(p: ByteArray): Consulta? {
        if (p.size < 28) return null
        if ((p[0].toInt() shr 4) != 4) return null            // solo IPv4
        val ihl = (p[0].toInt() and 0x0f) * 4
        if (p[9].toInt() != 17) return null                     // solo UDP
        val total = minOf(u16(p, 2), p.size)
        if (total < ihl + 8 + 12) return null
        val puertoOrigen = u16(p, ihl)
        val puertoDestino = u16(p, ihl + 2)
        if (puertoDestino != 53) return null
        val dns = p.copyOfRange(ihl + 8, total)
        val dominio = leerNombre(dns) ?: return null
        return Consulta(p.copyOfRange(12, 16), p.copyOfRange(16, 20), puertoOrigen, puertoDestino, dns, dominio)
    }

    private fun u16(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xff) shl 8) or (b[i + 1].toInt() and 0xff)

    private fun leerNombre(dns: ByteArray): String? {
        val partes = mutableListOf<String>()
        var i = 12
        while (i < dns.size) {
            val largo = dns[i].toInt() and 0xff
            if (largo == 0) return partes.joinToString(".").lowercase()
            if ((largo and 0xC0) != 0 || i + 1 + largo > dns.size) return null
            partes.add(String(dns, i + 1, largo, Charsets.US_ASCII))
            i += largo + 1
        }
        return null
    }

    /** Respuesta "ese dominio no existe" (NXDOMAIN). */
    fun respuestaBloqueo(q: ByteArray): ByteArray {
        var i = 12
        while (i < q.size && q[i].toInt() != 0) i += (q[i].toInt() and 0xff) + 1
        val fin = minOf(i + 5, q.size)
        val r = q.copyOf(fin)
        r[2] = ((q[2].toInt() and 0x79) or 0x80).toByte()
        r[3] = 0x83.toByte()
        for (k in 6 until 12) r[k] = 0
        return r
    }

    /** Envuelve una respuesta DNS en un paquete IPv4/UDP de vuelta hacia la app. */
    fun armarRespuesta(c: Consulta, dns: ByteArray): ByteArray {
        val total = 28 + dns.size
        val p = ByteArray(total)
        p[0] = 0x45.toByte()
        p[2] = (total shr 8).toByte()
        p[3] = total.toByte()
        p[8] = 64.toByte()
        p[9] = 17.toByte()
        System.arraycopy(c.ipDestino, 0, p, 12, 4)
        System.arraycopy(c.ipOrigen, 0, p, 16, 4)
        val suma = checksum(p, 0, 20)
        p[10] = (suma shr 8).toByte()
        p[11] = suma.toByte()
        val largoUdp = 8 + dns.size
        p[20] = (c.puertoDestino shr 8).toByte(); p[21] = c.puertoDestino.toByte()
        p[22] = (c.puertoOrigen shr 8).toByte(); p[23] = c.puertoOrigen.toByte()
        p[24] = (largoUdp shr 8).toByte(); p[25] = largoUdp.toByte()
        System.arraycopy(dns, 0, p, 28, dns.size)
        return p
    }

    private fun checksum(b: ByteArray, desde: Int, largo: Int): Int {
        var s = 0
        var i = desde
        while (i < desde + largo) {
            s += ((b[i].toInt() and 0xff) shl 8) or (b[i + 1].toInt() and 0xff)
            i += 2
        }
        while ((s shr 16) != 0) s = (s and 0xffff) + (s shr 16)
        return s.inv() and 0xffff
    }
}
