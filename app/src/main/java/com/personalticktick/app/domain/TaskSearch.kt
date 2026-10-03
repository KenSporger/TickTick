package com.personalticktick.app.domain

import net.sourceforge.pinyin4j.PinyinHelper
import java.util.Locale

/** Title-only fuzzy matcher used by both local search and filtering server results. */
object TaskSearch {
    fun matches(title: String, query: String): Boolean {
        val needle = normalize(query)
        if (needle.isEmpty()) return true
        val plain = normalize(title)
        if (isOrderedSubsequence(needle, plain)) return true

        val syllables = title.map { character ->
            PinyinHelper.toHanyuPinyinStringArray(character)
                ?.firstOrNull()
                ?.replace(Regex("[1-5]$"), "")
                ?.lowercase(Locale.ROOT)
                ?: character.lowercaseChar().toString()
        }
        val fullPinyin = syllables.joinToString("").filterNot(Char::isWhitespace)
        val initials = syllables.filter { it.isNotBlank() }.joinToString("") { it.first().toString() }
        return isOrderedSubsequence(needle, fullPinyin) || isOrderedSubsequence(needle, initials)
    }

    fun filter(tasks: Iterable<Task>, query: String): List<Task> =
        tasks.filter { matches(it.title, query) }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).filterNot(Char::isWhitespace)

    private fun isOrderedSubsequence(needle: String, haystack: String): Boolean {
        var index = 0
        for (character in haystack) {
            if (index < needle.length && character == needle[index]) index++
        }
        return index == needle.length
    }
}
