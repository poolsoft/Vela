package app.vela.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class TurkishBpeTokenizerTest {

    private lateinit var tokenizer: TurkishBpeTokenizer

    @Before
    fun setUp() {
        val sampleTokens = listOf(
            "<blk> 0",
            "<sos/eos> 1",
            "<unk> 2",
            " 3",
            "h 4",
            "ey 5",
            "kar 6",
            "m 7",
            "ol 8",
            "a 9",
            "k 10",
            "ar 11",
            "de 12",
            "ş 13",
            "mer 14",
            "ha 15",
            "ba 16",
            "as 17",
            "is 18",
            "tan 19"
        )
        val tempFile = File.createTempFile("sample_tokens", ".txt").apply {
            writeText(sampleTokens.joinToString("\n"))
            deleteOnExit()
        }

        tokenizer = TurkishBpeTokenizer(tempFile)
    }

    @Test
    fun testIsReady() {
        assertTrue(tokenizer.isReady)
    }

    @Test
    fun testTokenizeHeyKar() {
        val tokens = tokenizer.tokenize("Hey Kar")
        assertTrue(tokens.isNotEmpty())
        val formatted = tokenizer.formatKeywordLine("Hey Kar", boost = 1.6f, threshold = 0.45f)
        assertNotNull(formatted)
        assertTrue(formatted!!.contains(": 1.6 # 0.45"))
    }

    @Test
    fun testEmptyInput() {
        val tokens = tokenizer.tokenize("   ")
        assertTrue(tokens.isEmpty())
        val formatted = tokenizer.formatKeywordLine("   ", 1.6f, 0.45f)
        assertNull(formatted)
    }
}
