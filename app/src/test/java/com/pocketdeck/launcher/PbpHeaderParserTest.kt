package com.pocketdeck.launcher

import com.pocketdeck.launcher.data.PbpHeaderParser
import com.pocketdeck.launcher.data.RomScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PbpHeaderParserTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun parseExtractsPs1AndPspSfoMetadata() {
        val ps1File = File(tmp.root, "ps1.pbp").apply {
            writeBytes(
                buildSyntheticPbp(
                    entries = listOf(
                        "CATEGORY" to "ME",
                        "DISC_ID" to "SCUS94640",
                        "TITLE" to "Syphon Filter 3",
                    ),
                    psarMagic = "PSISOIMG0000",
                ),
            )
        }
        val ps1Info = PbpHeaderParser.parse(ps1File)
        assertNotNull(ps1Info)
        assertEquals("Syphon Filter 3", ps1Info?.title)
        assertEquals("SCUS94640", ps1Info?.discId)
        assertEquals("ME", ps1Info?.category)
        assertTrue(ps1Info?.isPs1Classics == true)

        val pspFile = File(tmp.root, "psp.pbp").apply {
            writeBytes(
                buildSyntheticPbp(
                    entries = listOf(
                        "CATEGORY" to "MG",
                        "DISC_ID" to "HOMEBREW1",
                        "TITLE" to "Cave Story",
                    ),
                    psarMagic = null,
                ),
            )
        }
        val pspInfo = PbpHeaderParser.parse(pspFile)
        assertNotNull(pspInfo)
        assertEquals("Cave Story", pspInfo?.title)
        assertEquals("MG", pspInfo?.category)
        assertFalse(pspInfo?.isPs1Classics == true)

        val invalidFile = File(tmp.root, "not_pbp.pbp").apply { writeText("plain text") }
        assertNull(PbpHeaderParser.parse(invalidFile))
    }

    @Test
    fun romScannerReclassifiesPs1PbpFromPspFolderAndPreservesAcrossIncrementalScan() {
        val romsRoot = tmp.newFolder("roms")
        val pspDir = File(romsRoot, "psp").apply { mkdirs() }
        val ps1Pbp = File(pspDir, "EBOOT.PBP").apply {
            writeBytes(
                buildSyntheticPbp(
                    entries = listOf(
                        "CATEGORY" to "ME",
                        "DISC_ID" to "SCUS94640",
                        "TITLE" to "Syphon Filter 3",
                    ),
                ),
            )
        }

        val firstLib = RomScanner.scan(romsRoot)
        val psxGames = firstLib.games["psx"].orEmpty()
        assertEquals(1, psxGames.size)
        assertEquals(ps1Pbp.path, psxGames[0].id)
        assertEquals("psx", psxGames[0].platformId)
        assertEquals("Syphon Filter 3", psxGames[0].title)
        assertTrue(firstLib.games["psp"].orEmpty().isEmpty())

        // 增量扫描命中相同目录快照时，仍保留跨平台识别出的 psx 分类与真实标题
        val incrementalLib = RomScanner.scanAll(listOf(romsRoot), previousLibrary = firstLib)
        val incPsx = incrementalLib.games["psx"].orEmpty()
        assertEquals(1, incPsx.size)
        assertEquals("Syphon Filter 3", incPsx[0].title)
        assertEquals("psx", incPsx[0].platformId)
    }

    @Test
    fun romScannerKeepsNativePspPbpInPspAndUsesSfoTitle() {
        val romsRoot = tmp.newFolder("roms_psp")
        val pspDir = File(romsRoot, "psp").apply { mkdirs() }
        File(pspDir, "EBOOT.PBP").apply {
            writeBytes(
                buildSyntheticPbp(
                    entries = listOf(
                        "CATEGORY" to "MG",
                        "TITLE" to "Cave Story",
                    ),
                ),
            )
        }

        val lib = RomScanner.scan(romsRoot)
        val pspGames = lib.games["psp"].orEmpty()
        assertEquals(1, pspGames.size)
        assertEquals("psp", pspGames[0].platformId)
        assertEquals("Cave Story", pspGames[0].title)
        assertTrue(lib.games["psx"].orEmpty().isEmpty())
    }

    private fun buildSyntheticPbp(
        entries: List<Pair<String, String>>,
        psarMagic: String? = null,
    ): ByteArray {
        val sorted = entries.sortedBy { it.first }
        val keyTable = ByteArrayOutputStream()
        val dataTable = ByteArrayOutputStream()
        val indexRows = ArrayList<ByteArray>(sorted.size)

        for ((key, value) in sorted) {
            val keyOff = keyTable.size()
            keyTable.write(key.toByteArray(Charsets.UTF_8))
            keyTable.write(0)

            val rawVal = value.toByteArray(Charsets.UTF_8) + byteArrayOf(0)
            val maxLen = ((rawVal.size + 3) / 4) * 4
            val dataOff = dataTable.size()
            dataTable.write(rawVal)
            repeat(maxLen - rawVal.size) { dataTable.write(0) }

            val row = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                .putShort(keyOff.toShort())
                .putShort(0x0204.toShort())
                .putInt(rawVal.size)
                .putInt(maxLen)
                .putInt(dataOff)
                .array()
            indexRows += row
        }

        val keyBytes = keyTable.toByteArray()
        val paddedKeyBytes = ByteArray(((keyBytes.size + 3) / 4) * 4).also {
            System.arraycopy(keyBytes, 0, it, 0, keyBytes.size)
        }
        val dataBytes = dataTable.toByteArray()

        val keyTableOffset = 20 + sorted.size * 16
        val dataTableOffset = keyTableOffset + paddedKeyBytes.size
        val sfoOut = ByteArrayOutputStream()
        sfoOut.write(
            ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
                .put(byteArrayOf(0x00, 'P'.code.toByte(), 'S'.code.toByte(), 'F'.code.toByte()))
                .putInt(0x00000101)
                .putInt(keyTableOffset)
                .putInt(dataTableOffset)
                .putInt(sorted.size)
                .array(),
        )
        indexRows.forEach { sfoOut.write(it) }
        sfoOut.write(paddedKeyBytes)
        sfoOut.write(dataBytes)
        val sfoBytes = sfoOut.toByteArray()

        val sfoOffset = 40
        val icon0Offset = sfoOffset + sfoBytes.size
        val psarBytes = psarMagic?.toByteArray(Charsets.US_ASCII) ?: ByteArray(0)
        val psarOffset = icon0Offset

        val pbpHeader = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN)
            .put(byteArrayOf(0x00, 'P'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte()))
            .putInt(0x00010000)
            .putInt(sfoOffset)
            .putInt(icon0Offset)
            .putInt(icon0Offset)
            .putInt(icon0Offset)
            .putInt(icon0Offset)
            .putInt(icon0Offset)
            .putInt(icon0Offset)
            .putInt(psarOffset)
            .array()

        return pbpHeader + sfoBytes + psarBytes
    }
}
