package com.pocketdeck.launcher.lan

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/**
 * 物理局域网网卡信息（已过滤回环、蜂窝与 TUN/VPN 虚拟网卡）。
 */
data class LanInterface(
    val interfaceName: String,
    val displayName: String,
    val ipAddress: String,
    val address: Inet4Address,
    val broadcastAddress: Inet4Address?,
    val prefixLength: Int,
    val networkInterface: NetworkInterface,
    val androidNetwork: Network?,
    val kindRank: Int,
) {
    val labelWithIp: String get() = "$displayName · $ipAddress"
}

object LanInterfaces {
    private val IGNORED_IFACE_PREFIXES = listOf(
        "lo", "tun", "tap", "ppp", "wg", "utun", "ipsec",
        "tailscale", "clash", "sing", "mihomo", "v2ray", "xray", "nekobox",
        "rmnet", "ccmni", "pdp", "dummy", "veth", "docker", "sit", "gre", "ip6tnl", "ifb",
    )

    private val PREFERRED_PREFIXES = listOf("wlan", "ap", "softap", "eth", "en", "rndis", "usb", "br")

    /** 列出当前所有可用的物理局域网 IPv4 网卡（如双 Wi-Fi wlan0/wlan1、有线 eth0、热点 ap0）。 */
    fun listAvailable(context: Context): List<LanInterface> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val networkByIface = HashMap<String, Pair<Network, Boolean>>() // iface -> (network, isVpn)
        if (cm != null) {
            runCatching {
                @Suppress("DEPRECATION")
                cm.allNetworks.forEach { net ->
                    val caps = cm.getNetworkCapabilities(net)
                    val isVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                    val isCell = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
                    val lp = cm.getLinkProperties(net)
                    val ifName = lp?.interfaceName
                    if (!ifName.isNullOrBlank()) {
                        networkByIface[ifName] = net to (isVpn || isCell)
                    }
                }
            }
        }

        val result = ArrayList<LanInterface>()
        val rawIfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }.getOrDefault(emptyList())
        for (nif in rawIfaces) {
            val name = nif.name.orEmpty()
            val lower = name.lowercase()
            if (name.isBlank()) continue
            if (IGNORED_IFACE_PREFIXES.any { lower.startsWith(it) }) continue
            val isUp = runCatching { nif.isUp && !nif.isLoopback && !nif.isPointToPoint }.getOrDefault(false)
            if (!isUp) continue

            val netPair = networkByIface[name]
            if (netPair?.second == true) continue // Android 明确标记为 VPN / Cellular

            for (ia in nif.interfaceAddresses) {
                val addr = ia.address as? Inet4Address ?: continue
                if (!isValidLanIpv4(addr, lower)) continue

                val prefix = ia.networkPrefixLength.toInt().coerceIn(8, 30)
                val bcast = (ia.broadcast as? Inet4Address) ?: computeBroadcast(addr, prefix)
                val friendly = friendlyName(lower, name)
                val rank = ifaceRank(lower)

                result += LanInterface(
                    interfaceName = name,
                    displayName = friendly,
                    ipAddress = addr.hostAddress.orEmpty(),
                    address = addr,
                    broadcastAddress = bcast,
                    prefixLength = prefix,
                    networkInterface = nif,
                    androidNetwork = netPair?.first,
                    kindRank = rank,
                )
            }
        }

        return result.sortedWith(
            compareBy<LanInterface>({ it.kindRank }, { it.interfaceName }, { it.ipAddress }),
        )
    }

    /**
     * 打开绑定到指定物理网卡的 TCP Socket，确保在开启 TUN/VPN 时局域网连接不走虚拟隧道。
     */
    fun openBoundSocket(
        lan: LanInterface,
        targetIp: String,
        targetPort: Int,
        connectTimeoutMs: Int = 5000,
        readTimeoutMs: Int = 15000,
    ): Socket {
        val sock = runCatching { lan.androidNetwork?.socketFactory?.createSocket() }.getOrNull() ?: Socket()
        try {
            sock.soTimeout = readTimeoutMs
            runCatching { sock.bind(InetSocketAddress(lan.address, 0)) }
            sock.connect(InetSocketAddress(InetAddress.getByName(targetIp), targetPort), connectTimeoutMs)
            return sock
        } catch (t: Throwable) {
            runCatching { sock.close() }
            throw t
        }
    }

    /**
     * 打开绑定到指定物理网卡的 TLS Socket（用于 LocalSend HTTPS 通信）。
     */
    fun openBoundTlsSocket(
        lan: LanInterface,
        sslContext: SSLContext,
        targetIp: String,
        targetPort: Int,
        connectTimeoutMs: Int = 5000,
        readTimeoutMs: Int = 15000,
    ): SSLSocket {
        val raw = openBoundSocket(lan, targetIp, targetPort, connectTimeoutMs, readTimeoutMs)
        return try {
            val tls = sslContext.socketFactory.createSocket(raw, targetIp, targetPort, true) as SSLSocket
            tls.useClientMode = true
            tls.soTimeout = readTimeoutMs
            tls.startHandshake()
            tls
        } catch (t: Throwable) {
            runCatching { raw.close() }
            throw t
        }
    }

    private fun isValidLanIpv4(addr: Inet4Address, lowerIface: String): Boolean {
        if (addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isMulticastAddress || addr.isAnyLocalAddress) {
            return false
        }
        val b = addr.address
        val b0 = b[0].toInt() and 0xFF
        val b1 = b[1].toInt() and 0xFF
        // 0.x.x.x / 127.x.x.x / 169.254.x.x / Clash fake-ip 198.18.0.0/15
        if (b0 == 0 || b0 == 127) return false
        if (b0 == 169 && b1 == 254) return false
        if (b0 == 198 && (b1 == 18 || b1 == 19)) return false
        // CGNAT 100.64.0.0/10 常见于 Tailscale/ZeroTier 虚拟网，若非明确物理网卡则剔除
        if (b0 == 100 && b1 in 64..127 && PREFERRED_PREFIXES.none { lowerIface.startsWith(it) }) {
            return false
        }
        return addr.isSiteLocalAddress || PREFERRED_PREFIXES.any { lowerIface.startsWith(it) }
    }

    private fun computeBroadcast(addr: Inet4Address, prefix: Int): Inet4Address? {
        return runCatching {
            val bytes = addr.address
            val ipInt = ((bytes[0].toInt() and 0xFF) shl 24) or
                ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or
                (bytes[3].toInt() and 0xFF)
            val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
            val bcastInt = ipInt or mask.inv()
            val out = byteArrayOf(
                ((bcastInt ushr 24) and 0xFF).toByte(),
                ((bcastInt ushr 16) and 0xFF).toByte(),
                ((bcastInt ushr 8) and 0xFF).toByte(),
                (bcastInt and 0xFF).toByte(),
            )
            InetAddress.getByAddress(out) as? Inet4Address
        }.getOrNull()
    }

    private fun friendlyName(lower: String, raw: String): String = when {
        lower == "wlan0" -> "Wi-Fi ($raw)"
        lower.startsWith("wlan") -> {
            val num = lower.removePrefix("wlan").toIntOrNull()?.plus(1)
            if (num != null) "Wi-Fi $num ($raw)" else "Wi-Fi ($raw)"
        }
        lower.startsWith("ap") || lower.startsWith("softap") -> "Hotspot ($raw)"
        lower.startsWith("eth") || lower.startsWith("en") -> "Ethernet ($raw)"
        lower.startsWith("rndis") || lower.startsWith("usb") -> "USB LAN ($raw)"
        else -> raw
    }

    private fun ifaceRank(lower: String): Int = when {
        lower == "wlan0" -> 0
        lower.startsWith("wlan") -> 1
        lower.startsWith("eth") || lower.startsWith("en") -> 2
        lower.startsWith("ap") || lower.startsWith("softap") -> 3
        lower.startsWith("rndis") || lower.startsWith("usb") -> 4
        else -> 5
    }
}
