package com.moviecatalogue.catalogue.domain

import java.text.Normalizer

/**
 * Character-class screening applied to every stored text field, before the
 * length check (V2.2-06): NFC-normalize, then reject NUL, control characters,
 * bidi overrides, invisible characters, and — unless the field allows it —
 * emoji.
 *
 * Duplicated verbatim in the people service rather than shared, matching the
 * existing deliberate duplication of `SearchPattern`/`PersonSearch` and
 * `UuidV7` that keeps each service's domain package self-contained
 * (ADR-1). The two copies are kept identical by a shared test vector list,
 * not by shared code.
 */
object TextRules {
    private val BIDI_OVERRIDES = (0x202A..0x202E) + (0x2066..0x2069)
    private val ZERO_WIDTH = (0x200B..0x200F) + listOf(0xFEFF)
    private const val ZWJ = 0x200D
    private const val VARIATION_SELECTOR_16 = 0xFE0F
    private val SKIN_TONE_MODIFIERS = 0x1F3FB..0x1F3FF
    private val REGIONAL_INDICATORS = 0x1F1E6..0x1F1FF

    /**
     * @param allowNewlines long-text fields (synopsis, biography, comment text)
     *   allow `\n`/`\r`/`\t`; single-line fields reject them as paste damage.
     * @param allowEmoji comment fields only. A zero-width joiner is then also
     *   allowed, but only strictly between two emoji code points — the glue in
     *   a multi-person emoji sequence, never a free-floating character.
     */
    fun screen(raw: String, field: String, allowNewlines: Boolean, allowEmoji: Boolean): String {
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFC)
        val codePoints = normalized.codePoints().toArray()
        for (i in codePoints.indices) {
            val cp = codePoints[i]
            when {
                cp == 0 -> throw ValidationException(
                    "$field contains a null character, which can't be stored.",
                    field = field,
                )
                cp == ZWJ && allowEmoji && isEmojiAdjacent(codePoints, i) -> Unit
                cp in ZERO_WIDTH || cp == ZWJ -> throw ValidationException(
                    "$field contains an invisible character, which isn't allowed.",
                    field = field,
                )
                cp in BIDI_OVERRIDES -> throw ValidationException(
                    "$field contains a text-direction override character, which isn't allowed.",
                    field = field,
                )
                isControl(cp) -> {
                    val isAllowedWhitespace = allowNewlines && (cp == '\n'.code || cp == '\r'.code || cp == '\t'.code)
                    if (!isAllowedWhitespace) {
                        throw ValidationException(
                            "$field contains a control character at position $i.",
                            field = field,
                        )
                    }
                }
                !allowEmoji && isEmojiCodePoint(cp) -> throw ValidationException(
                    "$field can't contain emoji.",
                    field = field,
                )
            }
        }
        return normalized
    }

    private fun isControl(cp: Int): Boolean = cp in 0x00..0x1F || cp in 0x7F..0x9F

    private fun isEmojiCodePoint(cp: Int): Boolean =
        Character.isExtendedPictographic(cp) ||
            cp in SKIN_TONE_MODIFIERS ||
            cp in REGIONAL_INDICATORS ||
            cp == VARIATION_SELECTOR_16

    private fun isEmojiAdjacent(codePoints: IntArray, index: Int): Boolean {
        val prev = codePoints.getOrNull(index - 1)
        val next = codePoints.getOrNull(index + 1)
        return prev != null && next != null && isEmojiCodePoint(prev) && isEmojiCodePoint(next)
    }
}
