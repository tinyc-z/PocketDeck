package com.pocketdeck.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class LocalPrefsStoreTest {

    private fun ByteArrayOutputStream.writeVarint(value: Long) {
        var v = value
        while (true) {
            if ((v and 0x7FL.inv()) == 0L) {
                write(v.toInt())
                return
            }
            write(((v.toInt() and 0x7F) or 0x80))
            v = v ushr 7
        }
    }

    private fun ByteArrayOutputStream.writeTag(fieldNum: Int, wireType: Int) {
        writeVarint(((fieldNum shl 3) or wireType).toLong())
    }

    private fun ByteArrayOutputStream.writeLengthDelimited(fieldNum: Int, payload: ByteArray) {
        writeTag(fieldNum, 2)
        writeVarint(payload.size.toLong())
        write(payload)
    }

    private fun encodePbEntry(key: String, valueBytes: ByteArray): ByteArray =
        ByteArrayOutputStream().apply {
            writeLengthDelimited(1, key.toByteArray(StandardCharsets.UTF_8))
            writeLengthDelimited(2, valueBytes)
        }.toByteArray()

    private fun encodePbBool(v: Boolean): ByteArray =
        ByteArrayOutputStream().apply {
            writeTag(1, 0)
            writeVarint(if (v) 1L else 0L)
        }.toByteArray()

    private fun encodePbString(v: String): ByteArray =
        ByteArrayOutputStream().apply {
            writeLengthDelimited(5, v.toByteArray(StandardCharsets.UTF_8))
        }.toByteArray()

    private fun encodePbStringSet(values: Collection<String>): ByteArray {
        val setMsg = ByteArrayOutputStream().apply {
            for (s in values) {
                writeLengthDelimited(1, s.toByteArray(StandardCharsets.UTF_8))
            }
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            writeLengthDelimited(6, setMsg)
        }.toByteArray()
    }

    @Test
    fun `legacy protobuf decoder migrates booleans strings and string sets losslessly`() {
        val pb = ByteArrayOutputStream().apply {
            writeLengthDelimited(1, encodePbEntry("show_metadata", encodePbBool(true)))
            writeLengthDelimited(1, encodePbEntry("clock_12h", encodePbBool(false)))
            writeLengthDelimited(1, encodePbEntry("theme", encodePbString("Dark")))
            writeLengthDelimited(1, encodePbEntry("empty_str", encodePbString("")))
            writeLengthDelimited(1, encodePbEntry("device_name", encodePbString("掌机PocketDeck")))
            writeLengthDelimited(
                1,
                encodePbEntry(
                    "renames",
                    encodePbStringSet(listOf("/roms/nes/mario.nes\u0000超级玛丽", "/roms/gba/zelda.gba\u0000塞尔达")),
                ),
            )
            writeLengthDelimited(1, encodePbEntry("empty_set", encodePbStringSet(emptyList())))
            // Unknown Value field types (fixed32 float=2, varint int=3, fixed64 double=7) should be skipped cleanly
            val skippedValue = ByteArrayOutputStream().apply {
                writeTag(2, 5)
                write(byteArrayOf(0, 0, 0, 0))
                writeTag(7, 1)
                write(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0))
            }.toByteArray()
            writeLengthDelimited(1, encodePbEntry("ignored_numeric", skippedValue))
            writeLengthDelimited(1, encodePbEntry("after_ignored", encodePbBool(true)))
        }.toByteArray()

        val decoded = LegacyDataStorePbReader.decode(pb)
        assertEquals(true, decoded["show_metadata"])
        assertEquals(false, decoded["clock_12h"])
        assertEquals("Dark", decoded["theme"])
        assertEquals("", decoded["empty_str"])
        assertEquals("掌机PocketDeck", decoded["device_name"])
        assertEquals(
            setOf("/roms/nes/mario.nes\u0000超级玛丽", "/roms/gba/zelda.gba\u0000塞尔达"),
            decoded["renames"],
        )
        assertEquals(emptySet<String>(), decoded["empty_set"])
        assertEquals(true, decoded["after_ignored"])
    }

    @Test
    fun `legacy protobuf decoder tolerates truncated input`() {
        val truncated = byteArrayOf(0x0A, 0x50, 0x0A, 0x03, 'a'.code.toByte())
        val decoded = LegacyDataStorePbReader.decode(truncated)
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `json codec round-trips booleans strings maps and sets with schema version`() {
        val original: Map<String, Any> = linkedMapOf(
            "show_metadata" to true,
            "reduce_motion" to false,
            "theme" to "Dark",
            "empty" to "",
            "rom_dirs" to "1\t/storage/emulated/0/ROMs\n0\t/storage/sdcard/Games",
            "renames" to linkedMapOf("/roms/gba/a.gba" to "口袋妖怪", "/roms/nes/b.nes" to "魂斗罗"),
            "favorites" to setOf("/roms/gba/a.gba", "/roms/nes/b.nes"),
        )
        val json = LocalPrefsStore.encodeJson(original)
        assertTrue(json.contains("\"v\":1"))
        val restored = LocalPrefsStore.decodeJson(json)
        assertEquals(original, restored)
    }
}
