package app.vela.voice

import java.io.File
import java.util.Locale

/**
 * Open-Vocabulary KWS (Keyword Spotter) icin Turkce BPE tokenlestirici.
 *
 * Sherpa-ONNX Zipformer Transducer modeli tokens.txt icindeki BPE parcalarini bekler.
 * Kullanicinin girdigi serbest Turkce kelimeleri (orn: "Hey Kar", "Hey Mola", "Mola Kardes")
 * BPE parcalarina ayirarak keywords.txt formatina donusturur.
 */
class TurkishBpeTokenizer(tokensFile: File) {

    private val tokenSet: Set<String> = runCatching {
        tokensFile.readLines()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) null
                else trimmed.split("\\s+".toRegex()).firstOrNull()
            }
            .toSet()
    }.getOrDefault(emptySet())

    val isReady: Boolean
        get() = tokenSet.isNotEmpty()

    /**
     * Verilen Turkce metni BPE parcalarina ayirir.
     * Orn: "Hey Kar" -> listOf("▁h", "ey", "▁kar")
     */
    fun tokenize(text: String): List<String> {
        if (!isReady || text.isBlank()) return emptyList()

        val trLocale = Locale.forLanguageTag("tr-TR")
        val clean = text.trim().lowercase(trLocale)
        val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }

        val result = mutableListOf<String>()
        for (word in words) {
            val wordWithPrefix = BPE_SPACE_PREFIX + word
            var i = 0
            while (i < wordWithPrefix.length) {
                var matched: String? = null
                val maxLen = minOf(wordWithPrefix.length - i, 16)
                for (len in maxLen downTo 1) {
                    val sub = wordWithPrefix.substring(i, i + len)
                    if (sub in tokenSet) {
                        matched = sub
                        i += len
                        break
                    }
                }
                if (matched != null) {
                    result.add(matched)
                } else {
                    // Fallback: tek karakter al
                    val singleChar = wordWithPrefix[i].toString()
                    result.add(singleChar)
                    i++
                }
            }
        }
        return result
    }

    /**
     * Anahtar kelime satirini formatlar:
     * Orn: "▁h ey ▁kar : 1.6 # 0.45"
     */
    fun formatKeywordLine(phrase: String, boost: Float, threshold: Float): String? {
        val tokens = tokenize(phrase)
        if (tokens.isEmpty()) return null
        val tokenString = tokens.joinToString(" ")
        return String.format(Locale.US, "%s : %.1f # %.2f", tokenString, boost, threshold)
    }

    companion object {
        /** SentencePiece BPE kelime basi bosluk isareti (U+2581). */
        const val BPE_SPACE_PREFIX = "\u2581"
    }
}

