package com.rrimal.notetaker.ui.orgview.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

object OrgHighlighter {
    private val boldRegex = Regex("""\*([^*]+)\*""")
    private val italicRegex = Regex("""/([^/]+)/""")
    private val underlineRegex = Regex("""_([^_]+)_""")
    private val codeRegex = Regex("""~([^~]+)~""")
    private val verbatimRegex = Regex("""=([^=]+)=""")
    private val strikeRegex = Regex("""\+([^+]+)\+""")
    private val linkRegex = Regex("""\[\[([^\]]+)\](?:\[([^\]]+)\])?\]""")
    private val commentRegex = Regex("""^\s*#.*$""", RegexOption.MULTILINE)

    fun highlight(text: String): AnnotatedString {
        return buildAnnotatedString {
            val lines = text.lines()
            lines.forEachIndexed { index, line ->
                if (commentRegex.matches(line)) {
                    withStyle(SpanStyle(color = OrgTheme.SyntaxColors.comment, fontStyle = FontStyle.Italic)) {
                        append(line)
                    }
                } else {
                    highlightLine(line)
                }
                if (index < lines.size - 1) {
                    append("\n")
                }
            }
        }
    }

    private fun AnnotatedString.Builder.highlightLine(line: String) {
        var currentIndex = 0
        
        // This is a simplified sequential highlighter. 
        // For production, a proper AST or a more robust regex-based parser would be better.
        // Here we just look for patterns and apply them if they don't overlap.
        
        val matches = mutableListOf<Triple<IntRange, SpanStyle, String>>()
        
        boldRegex.findAll(line).forEach { matches.add(Triple(it.range, SpanStyle(fontWeight = FontWeight.Bold, color = OrgTheme.SyntaxColors.bold), it.groupValues[1])) }
        italicRegex.findAll(line).forEach { matches.add(Triple(it.range, SpanStyle(fontStyle = FontStyle.Italic, color = OrgTheme.SyntaxColors.italic), it.groupValues[1])) }
        underlineRegex.findAll(line).forEach { matches.add(Triple(it.range, SpanStyle(textDecoration = TextDecoration.Underline), it.groupValues[1])) }
        codeRegex.findAll(line).forEach { matches.add(Triple(it.range, SpanStyle(fontFamily = FontFamily.Monospace, color = OrgTheme.SyntaxColors.code, background = OrgTheme.SyntaxColors.code.copy(alpha = 0.1f)), it.groupValues[1])) }
        verbatimRegex.findAll(line).forEach { matches.add(Triple(it.range, SpanStyle(fontFamily = FontFamily.Monospace, color = OrgTheme.SyntaxColors.verbatim), it.groupValues[1])) }
        strikeRegex.findAll(line).forEach { matches.add(Triple(it.range, SpanStyle(textDecoration = TextDecoration.LineThrough), it.groupValues[1])) }
        linkRegex.findAll(line).forEach { 
            val label = it.groupValues[2].takeIf { l -> l.isNotBlank() } ?: it.groupValues[1]
            matches.add(Triple(it.range, SpanStyle(color = OrgTheme.SyntaxColors.link, textDecoration = TextDecoration.Underline), label)) 
        }

        // Sort by start index and filter out overlaps
        val sortedMatches = matches.sortedBy { it.first.first }
        var lastEnd = 0
        
        for (match in sortedMatches) {
            val range = match.first
            val style = match.second
            val content = match.third
            
            if (range.first >= lastEnd) {
                append(line.substring(lastEnd, range.first))
                withStyle(style) {
                    append(content)
                }
                lastEnd = range.last + 1
            }
        }
        
        if (lastEnd < line.length) {
            append(line.substring(lastEnd))
        }
    }
}
