package com.rrimal.notetaker.data.orgmode

/**
 * Writer for org-mode files
 */
class OrgWriter {
    /**
     * Write a complete org file to string
     */
    fun writeFile(orgFile: OrgFile): String {
        val builder = StringBuilder()

        // Write preamble
        if (orgFile.preamble.isNotBlank()) {
            builder.append(orgFile.preamble)
            builder.append("\n\n")
        }

        // Write headlines
        orgFile.headlines.forEachIndexed { index, headline ->
            builder.append(writeHeadline(headline))
            if (index < orgFile.headlines.size - 1) {
                builder.append("\n")
            }
        }

        return builder.toString()
    }

    /**
     * Write a headline and its children to string
     */
    fun writeHeadline(headline: OrgNode.Headline, indent: Int = 0): String {
        val builder = StringBuilder()

        // Write headline line
        val stars = "*".repeat(headline.level)
        val todo = headline.todoState?.let { "$it " } ?: ""
        val priority = headline.priority?.let { "[#$it] " } ?: ""
        val tags = if (headline.tags.isNotEmpty()) {
            " :${headline.tags.joinToString(":")}:"
        } else {
            ""
        }
        builder.append("$stars $todo$priority${headline.title}$tags\n")

        // Write planning lines (CLOSED, SCHEDULED, DEADLINE)
        if (headline.closed != null) {
            builder.append("CLOSED: ${headline.closed}\n")
        }
        if (headline.scheduled != null) {
            builder.append("SCHEDULED: ${headline.scheduled}\n")
        }
        if (headline.deadline != null) {
            builder.append("DEADLINE: ${headline.deadline}\n")
        }

        // Write properties drawer
        if (headline.properties.isNotEmpty()) {
            builder.append(":PROPERTIES:\n")
            headline.properties.forEach { (key, value) ->
                builder.append(":$key: $value\n")
            }
            builder.append(":END:\n")
        }

        // Write body
        if (headline.body.isNotBlank()) {
            builder.append("${headline.body}\n")
        }

        // Write children
        if (headline.children.isNotEmpty()) {
            headline.children.forEach { child ->
                builder.append(writeHeadline(child, indent + 1))
            }
        }

        return builder.toString()
    }

    /**
     * Append a headline entry to existing org file content
     * If targetSection is specified, the entry will be appended under that section
     * Otherwise, it will be appended at the end of the file
     */
    fun appendEntry(
        existingContent: String,
        newHeadline: OrgNode.Headline,
        targetSection: String? = null
    ): String {
        // If no existing content, just write the new headline
        if (existingContent.isBlank()) {
            return writeHeadline(newHeadline)
        }

        // If no target section, append at the end
        if (targetSection == null) {
            return "$existingContent\n${writeHeadline(newHeadline)}"
        }

        // Parse existing content
        val parser = OrgParser()
        val orgFile = parser.parse(existingContent)

        // Find target section
        val targetHeadline = orgFile.findHeadline(targetSection)
        if (targetHeadline == null) {
            // Target section not found, append at the end
            return "$existingContent\n${writeHeadline(newHeadline)}"
        }

        // Insert under target section by reconstructing the file
        val updatedHeadlines = insertUnderHeadline(orgFile.headlines, targetSection, newHeadline)
        val updatedFile = orgFile.copy(headlines = updatedHeadlines)
        return writeFile(updatedFile)
    }

    /**
     * Helper function to insert a headline under a target headline
     */
    private fun insertUnderHeadline(
        headlines: List<OrgNode.Headline>,
        targetTitle: String,
        newHeadline: OrgNode.Headline
    ): List<OrgNode.Headline> {
        return headlines.map { headline ->
            if (headline.title == targetTitle) {
                // Found target, add new headline as child
                headline.copy(
                    children = headline.children + newHeadline.copy(level = headline.level + 1)
                )
            } else if (headline.children.isNotEmpty()) {
                // Recursively search in children
                headline.copy(
                    children = insertUnderHeadline(headline.children, targetTitle, newHeadline)
                )
            } else {
                headline
            }
        }
    }

    /**
     * Prepend a headline at the beginning of the file
     */
    fun prependEntry(existingContent: String, newHeadline: OrgNode.Headline): String {
        if (existingContent.isBlank()) {
            return writeHeadline(newHeadline)
        }

        return "${writeHeadline(newHeadline)}\n$existingContent"
    }

    /**
     * Create a simple headline from text (auto-extracts title and body)
     */
    fun createHeadline(
        text: String,
        level: Int = 1,
        todoState: String? = null,
        tags: List<String> = emptyList(),
        priority: String? = null,
        properties: Map<String, String> = emptyMap()
    ): OrgNode.Headline {
        val lines = text.lines()
        val title = lines.firstOrNull()?.take(100) ?: "Untitled"  // Limit title length
        val body = if (lines.size > 1) {
            lines.drop(1).joinToString("\n").trim()
        } else {
            ""
        }

        return OrgNode.Headline(
            level = level,
            todoState = todoState,
            priority = priority,
            title = title,
            tags = tags,
            properties = properties,
            body = body
        )
    }
}
