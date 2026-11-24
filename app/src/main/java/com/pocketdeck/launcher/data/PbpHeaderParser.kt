package com.pocketdeck.launcher.data

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PbpHeaderInfo(
    val title: String?,
    val discId: String?,
    val category: String?,
    val isPs1Classics: Boolean,
)

/**
 * 解析 Sony `.pbp` 封装头（PARAM.SFO 与 DATA.PSAR 魔数）：
 * - 提取 `TITLE` / `DISC_ID` / `CATEGORY`（避免所有 `EBOOT.PBP` 仅显示为 "EBOOT"）；
 * - 识别 PSX2PSP / PSOne Classics（`CATEGORY = ME` 或 `PSISOIMG` / `PSTITLEIMG` 偏移魔数）。
 */
object PbpHeaderParser {

    private const val PBP_HEADER_SIZE = 40
    private const val MIN_SFO_SIZE = 20
    private const val MAX_SFO_SIZE = 64 * 1024

    fun parse(file: File): PbpHeaderInfo? {
        if (!file.isFile || !file.canRead()) return null
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val fileLen = raf.length()
                if (fileLen < PBP_HEADER_SIZE.toLong()) return@use null
                val hdr = ByteArray(PBP_HEADER_SIZE)
                raf.readFully(hdr)
                if (hdr[0] != 0.toByte() ||
                    hdr[1] != 'P'.code.toByte() ||
                    hdr[2] != 'B'.code.toByte() ||
                    hdr[3] != 'P'.code.toByte()
                ) {
                    return@use null
                }

                val hdrBuf = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN)
                val sfoOffset = hdrBuf.getInt(8).toLong() and 0xFFFFFFFFL
                val icon0Offset = hdrBuf.getInt(12).toLong() and 0xFFFFFFFFL
                val psarOffset = hdrBuf.getInt(36).toLong() and 0xFFFFFFFFL

                var title: String? = null
                var discId: String? = null
                var category: String? = null

                val sfoSize = icon0Offset - sfoOffset
                if (sfoOffset >= PBP_HEADER_SIZE.toLong() &&
                    sfoSize in MIN_SFO_SIZE.toLong()..MAX_SFO_SIZE.toLong() &&
                    icon0Offset <= fileLen
                ) {
                    val sfoBytes = ByteArray(sfoSize.toInt())
                    raf.seek(sfoOffset)
                    raf.readFully(sfoBytes)
                    if (sfoBytes[0] == 0.toByte() &&
                        sfoBytes[1] == 'P'.code.toByte() &&
                        sfoBytes[2] == 'S'.code.toByte() &&
                        sfoBytes[3] == 'F'.code.toByte()
                    ) {
                        val sfo = ByteBuffer.wrap(sfoBytes).order(ByteOrder.LITTLE_ENDIAN)
                        val keyTableOffset = sfo.getInt(8)
                        val dataTableOffset = sfo.getInt(12)
                        val entryCount = sfo.getInt(16).coerceIn(0, 256)
                        if (keyTableOffset in MIN_SFO_SIZE..sfoBytes.size &&
                            dataTableOffset in keyTableOffset..sfoBytes.size
                        ) {
                            for (i in 0 until entryCount) {
                                val pos = MIN_SFO_SIZE + i * 16
                                if (pos + 16 > sfoBytes.size) break
                                val keyRel = sfo.getShort(pos).toInt() and 0xFFFF
                                val fmt = sfo.getShort(pos + 2).toInt() and 0xFFFF
                                val dataLen = sfo.getInt(pos + 4)
                                val dataRel = sfo.getInt(pos + 12)

                                val keyStart = keyTableOffset + keyRel
                                if (keyStart !in keyTableOffset until dataTableOffset) continue
                                var keyEnd = keyStart
                                while (keyEnd < dataTableOffset && sfoBytes[keyEnd] != 0.toByte()) {
                                    keyEnd++
                                }
                                val key = String(sfoBytes, keyStart, keyEnd - keyStart, Charsets.UTF_8)

                                if ((fmt == 0x0204 || fmt == 0x0004) && dataLen > 0) {
                                    val valStart = dataTableOffset + dataRel
                                    if (valStart >= dataTableOffset &&
                                        valStart.toLong() + dataLen.toLong() <= sfoBytes.size.toLong()
                                    ) {
                                        val raw = String(sfoBytes, valStart, dataLen, Charsets.UTF_8)
                                        val clean = raw.trimEnd('\u0000', '\r', '\n', ' ').trim()
                                        if (clean.isNotEmpty()) {
                                            when (key) {
                                                "TITLE" -> if (title == null) title = clean
                                                "DISC_ID" -> if (discId == null) discId = clean
                                                "CATEGORY" -> if (category == null) category = clean
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                var hasPs1PsarMagic = false
                if (psarOffset >= PBP_HEADER_SIZE.toLong() && psarOffset + 12L <= fileLen) {
                    val psarMagic = ByteArray(12)
                    raf.seek(psarOffset)
                    raf.readFully(psarMagic)
                    val prefix = String(psarMagic, Charsets.US_ASCII)
                    if (prefix.startsWith("PSISOIMG") || prefix.startsWith("PSTITLEIMG")) {
                        hasPs1PsarMagic = true
                    }
                }

                val isPs1 = category.equals("ME", ignoreCase = true) || hasPs1PsarMagic
                PbpHeaderInfo(
                    title = title,
                    discId = discId,
                    category = category,
                    isPs1Classics = isPs1,
                )
            }
        }.getOrNull()
    }
}
