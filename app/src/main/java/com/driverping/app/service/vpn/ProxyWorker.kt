package com.driverping.app.service.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * ProxyWorker
 * ------------
 * Motor completo de proxy en espacio de usuario (tun2socks) para Android VPN.
 * Procesa tráfico IP de la interfaz TUN:
 *   1. Protocolo 17 (UDP): Enruta consultas DNS (53) y tráfico UDP mediante DatagramSockets protegidos.
 *   2. Protocolo 6  (TCP): Mantiene sesiones TCP mediante SocketChannels protegidos (vpnService.protect),
 *      sintetizando las respuestas SYN-ACK, ACK, PSH y FIN directamente hacia la interfaz TUN.
 *
 * Esto permite que las apps en la lista blanca (addAllowedApplication) funcionen
 * con conectividad real HTTP/HTTPS/DNS completa mientras el resto de apps quedan bloqueadas.
 */
class ProxyWorker(
    private val vpnService: VpnService,
    private val fd: ParcelFileDescriptor
) {
    private var isRunning = false
    private var proxyThread: Thread? = null
    private val threadPool: ExecutorService = Executors.newCachedThreadPool()

    // Flujos UDP activos
    private val udpSockets = ConcurrentHashMap<String, DatagramSocket>()

    // Sesiones TCP activas
    private class TcpSession(
        val socketChannel: SocketChannel,
        var clientSeq: Long,
        var mySeq: Long,
        var myAck: Long,
        @Volatile var isClosed: Boolean = false
    )

    private val tcpSessions = ConcurrentHashMap<String, TcpSession>()

    fun start() {
        if (isRunning) return
        isRunning = true
        proxyThread = thread(start = true, name = "DriverPing-TunWorker") {
            runLoop()
        }
        startRrcKeepAliveLoop()
    }

    fun stop() {
        isRunning = false
        proxyThread?.interrupt()
        threadPool.shutdownNow()
        udpSockets.values.forEach {
            try { it.close() } catch (_: Exception) {}
        }
        udpSockets.clear()

        tcpSessions.values.forEach {
            try { it.socketChannel.close() } catch (_: Exception) {}
        }
        tcpSessions.clear()
    }

    private fun runLoop() {
        val input = FileInputStream(fd.fileDescriptor)
        val buffer = ByteArray(32767)

        while (isRunning && !Thread.currentThread().isInterrupted) {
            try {
                val length = input.read(buffer)
                if (length > 0) {
                    processIpPacket(buffer, length)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e("ProxyWorker", "Error leyendo del TUN", e)
                }
                break
            }
        }
    }

    private fun processIpPacket(buffer: ByteArray, length: Int) {
        if (length < 20) return
        val bb = ByteBuffer.wrap(buffer, 0, length)
        val versionAndIHL = bb.get().toInt()
        val version = versionAndIHL shr 4

        if (version != 4) return // Solo IPv4

        val ihl = versionAndIHL and 0x0F
        val ipHeaderLen = ihl * 4

        bb.position(9)
        val protocol = bb.get().toInt()

        bb.position(12)
        val srcIp = ByteArray(4)
        bb.get(srcIp)
        val dstIp = ByteArray(4)
        bb.get(dstIp)

        if (protocol == 17) { // UDP
            bb.position(ipHeaderLen)
            val srcPort = bb.short.toInt() and 0xFFFF
            val dstPort = bb.short.toInt() and 0xFFFF
            val udpLen = bb.short.toInt() and 0xFFFF
            bb.short // Checksum UDP

            val payloadLen = udpLen - 8
            if (payloadLen > 0 && bb.position() + payloadLen <= length) {
                val payload = ByteArray(payloadLen)
                bb.get(payload)
                handleUdp(srcIp, srcPort, dstIp, dstPort, payload)
            }
        } else if (protocol == 6) { // TCP
            bb.position(ipHeaderLen)
            val srcPort = bb.short.toInt() and 0xFFFF
            val dstPort = bb.short.toInt() and 0xFFFF
            val seq = bb.int.toLong() and 0xFFFFFFFFL
            val ack = bb.int.toLong() and 0xFFFFFFFFL
            val dataOffsetAndFlags = bb.short.toInt() and 0xFFFF
            val tcpHeaderLen = ((dataOffsetAndFlags shr 12) and 0x0F) * 4
            val flags = dataOffsetAndFlags and 0x3F

            val payloadLen = length - ipHeaderLen - tcpHeaderLen
            val payload = if (payloadLen > 0 && ipHeaderLen + tcpHeaderLen + payloadLen <= length) {
                val data = ByteArray(payloadLen)
                bb.position(ipHeaderLen + tcpHeaderLen)
                bb.get(data)
                data
            } else null

            handleTcp(srcIp, srcPort, dstIp, dstPort, seq, ack, flags, payload)
        }
    }

    // =========================================================================
    // TRÁFICO UDP (DNS / PING)
    // =========================================================================

    private fun handleUdp(
        srcIp: ByteArray, srcPort: Int,
        dstIp: ByteArray, dstPort: Int,
        payload: ByteArray
    ) {
        val srcIpStr = InetAddress.getByAddress(srcIp).hostAddress ?: return
        val dstIpStr = InetAddress.getByAddress(dstIp).hostAddress ?: return
        val key = "$srcIpStr:$srcPort->$dstIpStr:$dstPort"

        threadPool.execute {
            try {
                val socket = udpSockets.getOrPut(key) {
                    DatagramSocket().apply {
                        vpnService.protect(this) // El socket evade la VPN
                    }
                }

                val targetAddress = InetAddress.getByName(dstIpStr)
                val packet = DatagramPacket(payload, payload.size, targetAddress, dstPort)
                socket.send(packet)

                val recvBuffer = ByteArray(32767)
                val recvPacket = DatagramPacket(recvBuffer, recvBuffer.size)
                socket.soTimeout = 3000
                socket.receive(recvPacket)

                injectUdpResponse(dstIp, dstPort, srcIp, srcPort, recvBuffer.copyOfRange(0, recvPacket.length))
            } catch (_: Exception) {
                udpSockets.remove(key)?.close()
            }
        }
    }

    private fun injectUdpResponse(
        srcIp: ByteArray, srcPort: Int,
        dstIp: ByteArray, dstPort: Int,
        payload: ByteArray
    ) {
        val totalLength = 20 + 8 + payload.size
        val packet = ByteBuffer.allocate(totalLength)

        // IP Header (20 bytes)
        packet.put(0x45.toByte())
        packet.put(0x00.toByte())
        packet.putShort(totalLength.toShort())
        packet.putShort(0.toShort())
        packet.putShort(0.toShort())
        packet.put(64.toByte())
        packet.put(17.toByte()) // UDP
        packet.putShort(0.toShort())
        packet.put(srcIp)
        packet.put(dstIp)

        val ipChecksum = computeChecksum(packet.array(), 20)
        packet.putShort(10, ipChecksum.toShort())

        // UDP Header
        packet.putShort(srcPort.toShort())
        packet.putShort(dstPort.toShort())
        packet.putShort((8 + payload.size).toShort())
        packet.putShort(0.toShort())
        packet.put(payload)

        writeToTun(packet.array())
    }

    // =========================================================================
    // TRÁFICO TCP (HTTP / HTTPS / WEBSOCKETS)
    // =========================================================================

    private fun handleTcp(
        srcIp: ByteArray, srcPort: Int,
        dstIp: ByteArray, dstPort: Int,
        seq: Long, ack: Long,
        flags: Int, payload: ByteArray?
    ) {
        val srcIpStr = InetAddress.getByAddress(srcIp).hostAddress ?: return
        val dstIpStr = InetAddress.getByAddress(dstIp).hostAddress ?: return
        val key = "$srcIpStr:$srcPort->$dstIpStr:$dstPort"

        val isSyn = (flags and 0x02) != 0
        val isFin = (flags and 0x01) != 0
        val isRst = (flags and 0x04) != 0

        if (isRst) {
            tcpSessions.remove(key)?.let {
                it.isClosed = true
                try { it.socketChannel.close() } catch (_: Exception) {}
            }
            return
        }

        if (isSyn) {
            // Iniciar nueva conexión TCP
            threadPool.execute {
                try {
                    val channel = SocketChannel.open()
                    channel.configureBlocking(true)
                    channel.socket().keepAlive = true // Mantiene activas las conexiones WebSocket prolongadas de Yummy
                    channel.socket().tcpNoDelay = true // Entrega inmediata de paquetes sin latencia Nagle
                    vpnService.protect(channel.socket()) // Evade la VPN
                    channel.connect(InetSocketAddress(dstIpStr, dstPort))

                    val clientSeq = seq
                    val mySeq = 100000L
                    val myAck = (clientSeq + 1) and 0xFFFFFFFFL

                    val session = TcpSession(channel, clientSeq, mySeq, myAck)
                    tcpSessions[key] = session

                    // Responder SYN-ACK (0x12)
                    injectTcpResponse(dstIp, dstPort, srcIp, srcPort, session.mySeq, session.myAck, 0x12, null)
                    session.mySeq = (session.mySeq + 1) and 0xFFFFFFFFL

                    // Bucle lector del servidor remoto hacia el túnel
                    readRemoteTcp(session, dstIp, dstPort, srcIp, srcPort, key)
                } catch (_: Exception) {
                    // Si falla la conexión, enviamos RST
                    injectTcpResponse(dstIp, dstPort, srcIp, srcPort, ack, (seq + 1) and 0xFFFFFFFFL, 0x04, null)
                }
            }
            return
        }

        val session = tcpSessions[key] ?: return

        if (payload != null && payload.isNotEmpty()) {
            session.myAck = (seq + payload.size) and 0xFFFFFFFFL
            threadPool.execute {
                try {
                    val buf = ByteBuffer.wrap(payload)
                    while (buf.hasRemaining() && !session.isClosed) {
                        session.socketChannel.write(buf)
                    }
                    // Confirmar recepción ACK (0x10)
                    injectTcpResponse(dstIp, dstPort, srcIp, srcPort, session.mySeq, session.myAck, 0x10, null)
                } catch (_: Exception) {
                    tcpSessions.remove(key)
                }
            }
        }

        if (isFin) {
            session.myAck = (seq + 1) and 0xFFFFFFFFL
            injectTcpResponse(dstIp, dstPort, srcIp, srcPort, session.mySeq, session.myAck, 0x11, null)
            session.isClosed = true
            tcpSessions.remove(key)?.let {
                try { it.socketChannel.close() } catch (_: Exception) {}
            }
        }
    }

    private fun readRemoteTcp(
        session: TcpSession,
        srcIp: ByteArray, srcPort: Int,
        dstIp: ByteArray, dstPort: Int,
        key: String
    ) {
        val recvBuf = ByteBuffer.allocate(16384)
        try {
            while (isRunning && !session.isClosed && session.socketChannel.isOpen) {
                recvBuf.clear()
                val read = session.socketChannel.read(recvBuf)
                if (read > 0) {
                    recvBuf.flip()
                    val payload = ByteArray(recvBuf.remaining())
                    recvBuf.get(payload)

                    // Enviar PSH-ACK (0x18) con datos al TUN
                    injectTcpResponse(srcIp, srcPort, dstIp, dstPort, session.mySeq, session.myAck, 0x18, payload)
                    session.mySeq = (session.mySeq + payload.size) and 0xFFFFFFFFL
                } else if (read < 0) {
                    break
                }
            }
        } catch (_: Exception) {
        } finally {
            if (!session.isClosed) {
                session.isClosed = true
                // Enviar FIN-ACK (0x11)
                injectTcpResponse(srcIp, srcPort, dstIp, dstPort, session.mySeq, session.myAck, 0x11, null)
                tcpSessions.remove(key)?.let {
                    try { it.socketChannel.close() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun injectTcpResponse(
        srcIp: ByteArray, srcPort: Int,
        dstIp: ByteArray, dstPort: Int,
        seq: Long, ack: Long,
        flags: Int, payload: ByteArray?
    ) {
        val payloadLen = payload?.size ?: 0
        val totalLength = 20 + 20 + payloadLen
        val packet = ByteBuffer.allocate(totalLength)

        // IP Header (20 bytes)
        packet.put(0x45.toByte())
        packet.put(0x00.toByte())
        packet.putShort(totalLength.toShort())
        packet.putShort(0.toShort())
        packet.putShort(0x4000.toShort()) // Don't Fragment
        packet.put(64.toByte()) // TTL
        packet.put(6.toByte()) // TCP
        packet.putShort(0.toShort()) // IP Checksum
        packet.put(srcIp)
        packet.put(dstIp)

        val ipChecksum = computeChecksum(packet.array(), 20)
        packet.putShort(10, ipChecksum.toShort())

        // TCP Header (20 bytes)
        packet.putShort(srcPort.toShort())
        packet.putShort(dstPort.toShort())
        packet.putInt(seq.toInt())
        packet.putInt(ack.toInt())
        packet.put(0x50.toByte()) // Data Offset (5 * 4 = 20 bytes)
        packet.put(flags.toByte())
        packet.putShort(65535.toShort()) // Window
        packet.putShort(0.toShort()) // TCP Checksum
        packet.putShort(0.toShort()) // Urgent Pointer

        if (payload != null) {
            packet.put(payload)
        }

        // Checksum TCP
        val tcpBlock = packet.array().copyOfRange(20, totalLength)
        val tcpChecksum = computeTcpChecksum(srcIp, dstIp, tcpBlock)
        packet.putShort(36, tcpChecksum.toShort())

        writeToTun(packet.array())
    }

    private fun writeToTun(data: ByteArray) {
        synchronized(fd) {
            try {
                FileOutputStream(fd.fileDescriptor).write(data)
            } catch (_: Exception) {}
        }
    }

    private fun computeChecksum(buf: ByteArray, len: Int): Int {
        var sum = 0L
        var i = 0
        while (i < len - 1) {
            val word = ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < len) {
            sum += (buf[i].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun computeTcpChecksum(srcIp: ByteArray, dstIp: ByteArray, tcpBlock: ByteArray): Int {
        var sum = 0L

        // Pseudo Header TCP
        sum += ((srcIp[0].toInt() and 0xFF) shl 8) or (srcIp[1].toInt() and 0xFF)
        sum += ((srcIp[2].toInt() and 0xFF) shl 8) or (srcIp[3].toInt() and 0xFF)
        sum += ((dstIp[0].toInt() and 0xFF) shl 8) or (dstIp[1].toInt() and 0xFF)
        sum += ((dstIp[2].toInt() and 0xFF) shl 8) or (dstIp[3].toInt() and 0xFF)
        sum += 6 // Protocol TCP
        sum += tcpBlock.size

        var i = 0
        while (i < tcpBlock.size - 1) {
            val word = ((tcpBlock[i].toInt() and 0xFF) shl 8) or (tcpBlock[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < tcpBlock.size) {
            sum += (tcpBlock[i].toInt() and 0xFF) shl 8
        }

        while (sum shr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toInt()
    }

    /**
     * Prevención RRC Idle (Antena Móvil 4G/LTE Activa)
     * --------------------------------------------------
     * Envía un paquete UDP de 1 byte a un servidor Anycast (8.8.8.8) cada 3 segundos.
     * Evita que la interfaz de radio móvil entre en estado de inactividad (RRC Idle),
     * garantizando latencia cero al recibir solicitudes de viaje.
     */
    private fun startRrcKeepAliveLoop() {
        threadPool.execute {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.soTimeout = 2000
                vpnService.protect(socket) // Evade la VPN

                val targetIp = InetAddress.getByName("8.8.8.8")
                val dummyPayload = byteArrayOf(0x01)
                val packet = DatagramPacket(dummyPayload, dummyPayload.size, targetIp, 53)

                while (isRunning) {
                    try {
                        socket.send(packet)
                    } catch (_: Exception) {
                    }
                    Thread.sleep(3000L)
                }
            } catch (e: Exception) {
                Log.e("ProxyWorker", "Error en rutina de RRC Keep-Alive", e)
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }
}
