package app.vela.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class TurkishBpeTokenizerTest {

    private lateinit var tokenizer: TurkishBpeTokenizer

    @Before
    fun setUp() {
        val rootDir = File(".").canonicalFile
        var tokensFile = File(rootDir, "temp_kws_turkish/tokens.txt")
        if (!tokensFile.exists()) {
            tokensFile = File(rootDir, "../temp_kws_turkish/tokens.txt")
        }

        if (tokensFile.exists()) {
            tokenizer = TurkishBpeTokenizer(tokensFile)
        } else {
            // Test fallback: gercek mihuai bpe rank sirasini yansitan ornek tokenlar
            val sampleTokens = listOf(
                "<blk> 0",
                "<sos/eos> 1",
                "<unk> 2",
                "ar 3",
                " 4",
                "er 5",
                "an 6",
                "in 7",
                "de 8",
                "ir 9",
                "ın 10",
                " k 11",
                "il 12",
                "lar 13",
                " s 14",
                " y 15",
                "en 16",
                "da 17",
                "ma 18",
                " g 19",
                "or 20",
                "ek 21",
                "et 22",
                "la 23",
                " v 24"
            )
            val tempFile = File.createTempFile("sample_tokens", ".txt").apply {
                writeText(sampleTokens.joinToString("\n"))
                deleteOnExit()
            }
            tokenizer = TurkishBpeTokenizer(tempFile)
        }
    }

    @Test
    fun testIsReady() {
        assertTrue(tokenizer.isReady)
    }

    @Test
    fun testSixExactPhrasesMatchSentencePiece() {
        val tokensFile = File("temp_kws_turkish/tokens.txt")
        if (!tokensFile.exists()) return // Dosya yoksa pas gec

        val realTokenizer = TurkishBpeTokenizer(tokensFile)

        // 1. Hey Kar -> [' h', 'ey', ' kar']
        assertEquals(listOf(" h", "ey", " kar"), realTokenizer.tokenize("Hey Kar"))

        // 2. Hey Mola -> [' h', 'ey', ' m', 'o', 'la']
        assertEquals(listOf(" h", "ey", " m", "o", "la"), realTokenizer.tokenize("Hey Mola"))

        // 3. Mola Kardeş -> [' m', 'o', 'la', ' kar', 'de', 'ş']
        assertEquals(listOf(" m", "o", "la", " kar", "de", "ş"), realTokenizer.tokenize("Mola Kardeş"))

        // 4. Merhaba Asistan -> [' m', 'er', 'h', 'ab', 'a', ' a', 'si', 'st', 'an']
        assertEquals(listOf(" m", "er", "h", "ab", "a", " a", "si", "st", "an"), realTokenizer.tokenize("Merhaba Asistan"))

        // 5. Günaydın -> [' gün', 'ay', 'd', 'ın']
        assertEquals(listOf(" gün", "ay", "d", "ın"), realTokenizer.tokenize("Günaydın"))

        // 6. Şoför -> [' ş', 'o', 'f', 'ör']
        assertEquals(listOf(" ş", "o", "f", "ör"), realTokenizer.tokenize("Şoför"))
    }

    @Test
    fun testFormatKeywordLine() {
        val tokensFile = File("temp_kws_turkish/tokens.txt")
        if (!tokensFile.exists()) return

        val realTokenizer = TurkishBpeTokenizer(tokensFile)
        val formatted = realTokenizer.formatKeywordLine("Hey Kar", boost = 1.6f, threshold = 0.45f)
        assertNotNull(formatted)
        assertEquals(" h ey  kar : 1.6 # 0.45", formatted)
    }

    @Test
    fun testEmptyInput() {
        val tokens = tokenizer.tokenize("   ")
        assertTrue(tokens.isEmpty())
        val formatted = tokenizer.formatKeywordLine("   ", 1.6f, 0.45f)
        assertNull(formatted)
    }

    @Test
    fun testInvalidCharacterThrowsException() {
        val tokensFile = File("temp_kws_turkish/tokens.txt")
        if (!tokensFile.exists()) return

        val realTokenizer = TurkishBpeTokenizer(tokensFile)
        // Rusça veya Çince bir karakter tokens.txt içinde yoktur; hata fırlatmalıdır
        assertThrows(TurkishBpeTokenizer.TokenizationException::class.java) {
            realTokenizer.tokenize("Привет")
        }
    }
}
