package com.rrimal.notetaker.data.orgmode

/**
 * Sealed class representing nodes in an org-mode document
 */
sealed class OrgNode {
    /**
     * Represents an org-mode headline with all its properties
     */
    data class Headline(
        val level: Int,                              // Number of stars (1 = *, 2 = **, etc.)
        val todoState: String? = null,               // TODO, DONE, WAITING, CANCELLED, etc.
        val priority: String? = null,                // A, B, or C
        val title: String,                           // Headline text
        val tags: List<String> = emptyList(),        // Tags like :work:urgent:
        val scheduled: String? = null,               // SCHEDULED: <2026-02-28 Fri>
        val deadline: String? = null,                // DEADLINE: <2026-02-28 Fri>
        val closed: String? = null,                  // CLOSED: [2026-02-28 Fri 14:30]
        val properties: Map<String, String> = emptyMap(), // :PROPERTIES: drawer content
        val body: String = "",                       // Text content under the headline
        val children: List<Headline> = emptyList()   // Nested headlines
    ) : OrgNode() {
        /**
         * Get the full headline line (without body)
         */
        fun getHeadlineLine(): String {
            val stars = "*".repeat(level)
            val todo = todoState?.let { "$it " } ?: ""
            val prio = priority?.let { "[$#$it] " } ?: ""
            val tagStr = if (tags.isNotEmpty()) " :${tags.joinToString(":")}:" else ""
            return "$stars $todo$prio$title$tagStr"
        }
    }

    /**
     * Represents a paragraph of text (not under a headline)
     */
    data class Paragraph(val text: String) : OrgNode()

    /**
     * Represents a timestamp
     */
    data class Timestamp(
        val raw: String,      // Original timestamp string
        val type: TimestampType = TimestampType.PLAIN
    ) : OrgNode()
}

/**
 * Types of timestamps in org-mode
 */
enum class TimestampType {
    PLAIN,          // <2026-02-28 Fri>
    INACTIVE,       // [2026-02-28 Fri]
    SCHEDULED,      // SCHEDULED: <2026-02-28 Fri>
    DEADLINE,       // DEADLINE: <2026-02-28 Fri>
    CLOSED          // CLOSED: [2026-02-28 Fri 14:30]
}

/**
 * Represents a complete org-mode file
 */
data class OrgFile(
    val preamble: String = "",                    // Content before first headline
    val headlines: List<OrgNode.Headline> = emptyList()  // Top-level headlines
) {
    /**
     * Get all headlines (including nested ones) as a flat list
     */
    fun getAllHeadlines(): List<OrgNode.Headline> {
        fun flattenHeadlines(headlines: List<OrgNode.Headline>): List<OrgNode.Headline> {
            return headlines.flatMap { headline ->
                listOf(headline) + flattenHeadlines(headline.children)
            }
        }
        return flattenHeadlines(headlines)
    }

    /**
     * Find a headline by title
     */
    fun findHeadline(title: String): OrgNode.Headline? {
        return getAllHeadlines().find { it.title == title }
    }
}
