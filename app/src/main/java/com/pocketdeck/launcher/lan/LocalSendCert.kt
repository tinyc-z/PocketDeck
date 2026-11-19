package com.pocketdeck.launcher.lan

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManager
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager

/**
 * LocalSend v2 自签名 TLS 证书与 SSLContext 管理：
 * - 纯 JDK/Android API 生成 X.509 v3 RSA-2048 自签名证书（零第三方加密库依赖）
 * - 持久化到应用私有目录以保持稳定的 SHA-256 指纹
 * - 同时提供服务端 SSLContext 与客户端 Trust-All SSLContext（LocalSend 通过指纹而非 CA 链校验）
 */
object LocalSendCert {

    data class Identity(
        val fingerprint: String,
        val serverSslContext: SSLContext,
        val clientSslContext: SSLContext,
    )

    @Volatile
    private var cached: Identity? = null

    fun getOrCreate(context: Context): Identity {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val created = loadOrGenerate(context.applicationContext)
            cached = created
            return created
        }
    }

    private fun loadOrGenerate(context: Context): Identity {
        val keyFile = File(context.filesDir, "localsend_rsa.key")
        val certFile = File(context.filesDir, "localsend_rsa.der")

        val loaded = runCatching {
            if (keyFile.isFile && certFile.isFile) {
                val keyBytes = Base64.getDecoder().decode(keyFile.readText().trim())
                val certBytes = certFile.readBytes()
                val priv = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
                val cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
                priv to cert
            } else {
                null
            }
        }.getOrNull()

        val (privateKey, certificate, certDer) = if (loaded != null) {
            Triple(loaded.first, loaded.second, loaded.second.encoded)
        } else {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048, SecureRandom())
            val kp = kpg.generateKeyPair()
            val der = buildSelfSignedX509Der(kp.public.encoded, kp.private, "PocketDeck")
            val cert = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
            runCatching {
                keyFile.writeText(Base64.getEncoder().encodeToString(kp.private.encoded))
                certFile.writeBytes(der)
            }
            Triple(kp.private, cert, der)
        }

        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(certDer)
            .joinToString("") { "%02x".format(it) }

        val pass = "pocketdeck".toCharArray()
        val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setKeyEntry("localsend", privateKey, pass, arrayOf(certificate))
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(ks, pass)
        }
        val serverSsl = SSLContext.getInstance("TLS").apply {
            init(kmf.keyManagers, null, SecureRandom())
        }

        val baseKm = kmf.keyManagers.filterIsInstance<X509KeyManager>().first()
        val clientKm: KeyManager = object : X509ExtendedKeyManager() {
            override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? =
                baseKm.getClientAliases(keyType, null)

            override fun chooseClientAlias(
                keyType: Array<out String>?,
                issuers: Array<out Principal>?,
                socket: Socket?,
            ): String? = if (keyType?.any { it.equals("RSA", ignoreCase = true) } != false) "localsend" else null

            override fun chooseEngineClientAlias(
                keyType: Array<out String>?,
                issuers: Array<out Principal>?,
                engine: SSLEngine?,
            ): String? = if (keyType?.any { it.equals("RSA", ignoreCase = true) } != false) "localsend" else null

            override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? =
                baseKm.getServerAliases(keyType, issuers)

            override fun chooseServerAlias(
                keyType: String?,
                issuers: Array<out Principal>?,
                socket: Socket?,
            ): String? = baseKm.chooseServerAlias(keyType, issuers, socket)

            override fun chooseEngineServerAlias(
                keyType: String?,
                issuers: Array<out Principal>?,
                engine: SSLEngine?,
            ): String? = (baseKm as? X509ExtendedKeyManager)?.chooseEngineServerAlias(keyType, issuers, engine)

            override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
                baseKm.getCertificateChain(alias)

            override fun getPrivateKey(alias: String?): PrivateKey? =
                baseKm.getPrivateKey(alias)
        }

        val trustAll = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            },
        )
        val clientSsl = SSLContext.getInstance("TLS").apply {
            init(arrayOf(clientKm), trustAll, SecureRandom())
        }

        return Identity(fingerprint, serverSsl, clientSsl)
    }

    // ───────────────────────── Minimal ASN.1 DER X.509 v3 Builder ─────────────────────────

    private fun buildSelfSignedX509Der(
        spkiDer: ByteArray,
        privateKey: PrivateKey,
        commonName: String,
    ): ByteArray {
        val serialBytes = ByteArray(8).also { SecureRandom().nextBytes(it) }
        serialBytes[0] = (serialBytes[0].toInt() and 0x7F).toByte()
        if (serialBytes[0] == 0.toByte()) serialBytes[0] = 0x01

        // sha256WithRSAEncryption: 1.2.840.113549.1.1.11 + NULL
        val sigAlg = derSeq(
            byteArrayOf(
                0x06, 0x09,
                0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B,
            ) + byteArrayOf(0x05, 0x00),
        )

        // Name: SEQUENCE { SET { SEQUENCE { OID(2.5.4.3 = CN), UTF8String(commonName) } } }
        val cnBytes = commonName.toByteArray(Charsets.UTF_8)
        val attrTypeAndValue = derSeq(
            byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03) + derTag(0x0C, cnBytes),
        )
        val name = derSeq(derTag(0x31, attrTypeAndValue))

        // Validity: UTCTime 2024-01-01 to 2048-01-01
        val validity = derSeq(
            derTag(0x17, "240101000000Z".toByteArray(Charsets.US_ASCII)) +
                derTag(0x17, "480101000000Z".toByteArray(Charsets.US_ASCII)),
        )

        val version = byteArrayOf(0xA0.toByte(), 0x03, 0x02, 0x01, 0x02) // v3
        val serial = derTag(0x02, BigInteger(1, serialBytes).toByteArray())

        val tbs = derSeq(version + serial + sigAlg + name + validity + name + spkiDer)

        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey)
            update(tbs)
        }.sign()

        val sigBitString = derTag(0x03, byteArrayOf(0x00) + sig)
        return derSeq(tbs + sigAlg + sigBitString)
    }

    private fun derSeq(content: ByteArray): ByteArray = derTag(0x30, content)

    private fun derTag(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(content.size + 6)
        out.write(tag)
        val len = content.size
        when {
            len < 128 -> out.write(len)
            len <= 0xFF -> {
                out.write(0x81)
                out.write(len)
            }
            len <= 0xFFFF -> {
                out.write(0x82)
                out.write((len ushr 8) and 0xFF)
                out.write(len and 0xFF)
            }
            else -> {
                out.write(0x83)
                out.write((len ushr 16) and 0xFF)
                out.write((len ushr 8) and 0xFF)
                out.write(len and 0xFF)
            }
        }
        out.write(content)
        return out.toByteArray()
    }
}
