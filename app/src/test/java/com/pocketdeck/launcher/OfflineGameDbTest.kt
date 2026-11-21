package com.pocketdeck.launcher

import com.pocketdeck.launcher.data.OfflineGameDb
import com.pocketdeck.launcher.data.TitleNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OfflineGameDbTest {

    @Test
    fun parseMetaTsv_extractsHashesAndMetadata() {
        val lines = sequenceOf(
            "H\t63f9407d\tTetris (World) (Rev 1)",
            "H\t3EA860C2\tPokemon - Emerald Version (USA, Europe)",
            "M\tTetris (World) (Rev 1)\tBullet-Proof Software\tNintendo\tPuzzle\t1989-06",
            "M\tPokemon - Emerald Version (USA, Europe)\tGame Freak\tNintendo\tRPG\t2005-05",
        )
        val table = OfflineGameDb.parseMetaTsv(lines)

        assertEquals("Tetris (World) (Rev 1)", table.hashToName["63F9407D"])
        assertEquals("Pokemon - Emerald Version (USA, Europe)", table.hashToName["3EA860C2"])

        val tetrisMeta = table.metaByExactName[TitleNormalizer.exactKey("Tetris (World) (Rev 1)")]
        assertNotNull(tetrisMeta)
        assertEquals("Bullet-Proof Software", tetrisMeta?.developer)
        assertEquals("Nintendo", tetrisMeta?.publisher)
        assertEquals("Puzzle", tetrisMeta?.genre)
        assertEquals("1989-06", tetrisMeta?.releaseDate)

        val emeraldNorm = table.metaByNormTitle[TitleNormalizer.normalizedTitle("Pokemon - Emerald Version (USA, Europe)")]
        assertNotNull(emeraldNorm)
        assertEquals("Game Freak", emeraldNorm?.developer)
    }

    @Test
    fun mergeBoxartNames_unionsWithoutDuplicatesAndPreservesBundledOrder() {
        val bundled = listOf("A.png", "B.png")
        val remote = listOf("B.png", "C.png", "D.png")
        val merged = OfflineGameDb.mergeBoxartNames(bundled, remote)
        assertEquals(listOf("A.png", "B.png", "C.png", "D.png"), merged)
    }

    @Test
    fun sanitizeSystemName_matchesAssetNaming() {
        assertEquals(
            "Nintendo_-_Game_Boy_Advance",
            OfflineGameDb.sanitizeSystemName("Nintendo - Game Boy Advance"),
        )
    }
}
