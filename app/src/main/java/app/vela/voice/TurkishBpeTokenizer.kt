package app.vela.voice

import java.io.File
import java.util.Locale

/**
 * Open-Vocabulary KWS (Keyword Spotter) icin Turkce SentencePiece BPE tokenlestirici.
 *
 * ONEMLI MIMARI NOT:
 * Bu bilesen ozel egitilmis bir KWS modeli degildir. Mihuai Turkce 66M Zipformer Transducer
 * ASR modelini kisitli anahtar kelime aramasi (constrained keyword spotting) amaciyla kullanir.
 *
 * Mihuai modeli SentencePiece BPE (model_type = 2) algoritmasi ile egitilmistir.
 * Modelin bpe.model ve tokens.txt dosyasindaki parca oncelik siralamasi (BPE rank order)
 * birebir aynidir.
 *
 * Bu sinif greedy longest-match YERINE gercek SentencePiece BPE ikili birlestirme (pair merge)
 * algoritmasini uygular. SentencePiece C++ motoruyla ve Sherpa-ONNX text2token araciyla
 * %100 ozdes token ciktisi uretir.
 *
 * Eger kullanicinin girdigi ifadede modelin sozlügünde bulunmayan bir karakter/parca olusursa
 * sessizce yanlis token uretmez; TokenizationException firlatir / null doner.
 */
class TurkishBpeTokenizer(tokensFile: File) {

    /**
     * Token -> Rank eslesmesi.
     * SentencePiece BPE algoritmasinda daha kucuk indeks (rank) daha yuksek birlestirme onceligine sahiptir.
     */
    private val ranks: Map<String, Int>

    init {
        val map = mutableMapOf<String, Int>()
        runCatching {
            var index = 0
            tokensFile.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    val piece = trimmed.split("\\s+".toRegex()).firstOrNull()
                    if (piece != null) {
                        map[piece] = index
                    }
                    index++
                }
            }
        }
        ranks = map
    }

    val isReady: Boolean
        get() = ranks.isNotEmpty()

    /**
     * Verilen Turkce metni gercek SentencePiece BPE parcalarina ayirir.
     *
     * @param text Kullanici ifadesi (orn: "Hey Kar", "Kadıköy Şifa Hastanesi")
     * @return Model sozlügündeki parcalarin listesi (orn: ["▁h", "ey", "▁kar"])
     * @throws TokenizationException Eger kelimede model sozlügünde olmayan gecersiz karakter varsa
     */
    fun tokenize(text: String): List<String> {
        if (!isReady || text.isBlank()) return emptyList()

        val trLocale = Locale.forLanguageTag("tr-TR")
        val clean = text.trim().lowercase(trLocale)
        val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }

        val result = mutableListOf<String>()
        for (word in words) {
            val pieces = bpeMerge(BPE_SPACE_PREFIX + word)
            for (p in pieces) {
                if (p !in ranks) {
                    throw TokenizationException("Token sozlukte bulunamadi: '$p' (Metin: '$text')")
                }
            }
            result.addAll(pieces)
        }
        return result
    }

    /**
     * SentencePiece BPE Merge Algoritmasi:
     * Metni once karakterlere ayirir, ardindan en yuksek oncelige (en kucuk rank degerine)
     * sahip komsu parca ciftlerini tek parca haline getirerek adim adim birlestirir.
     */
    private fun bpeMerge(word: String): List<String> {
        var symbols = ArrayList<String>(word.length)
        for (i in 0 until word.length) {
            symbols.add(word[i].toString())
        }

        while (symbols.size > 1) {
            var bestRank = Int.MAX_VALUE
            var bestPair: String? = null
            var bestIdx = -1

            for (i in 0 until symbols.size - 1) {
                val pair = symbols[i] + symbols[i + 1]
                val rank = ranks[pair]
                if (rank != null && rank < bestRank) {
                    bestRank = rank
                    bestPair = pair
                    bestIdx = i
                }
            }

            if (bestIdx == -1 || bestPair == null) {
                break
            }

            val newSymbols = ArrayList<String>(symbols.size - 1)
            var i = 0
            while (i < symbols.size) {
                if (i == bestIdx) {
                    newSymbols.add(bestPair)
                    i += 2
                } else {
                    newSymbols.add(symbols[i])
                    i++
                }
            }
            symbols = newSymbols
        }

        return symbols
    }

    /**
     * Anahtar kelime satirini KWS formatinda dondurur:
     * Orn: "▁h ey ▁kar : 1.6 # 0.45"
     */
    fun formatKeywordLine(phrase: String, boost: Float, threshold: Float): String? {
        val tokens = runCatching { tokenize(phrase) }.getOrNull() ?: return null
        if (tokens.isEmpty()) return null
        val tokenString = tokens.joinToString(" ")
        return String.format(Locale.US, "%s : %.1f # %.2f", tokenString, boost, threshold)
    }

    class TokenizationException(message: String) : RuntimeException(message)

    companion object {
        /** SentencePiece BPE kelime basi bosluk isareti (U+2581). */
        const val BPE_SPACE_PREFIX = "\u2581"
    }
}
