package com.rrimal.notetaker.data.orgmode

/**
 * Parser for org-mode files with full org-mode support
 */
class OrgParser {
    private val headlineRegex = Regex("""^(\*+)\s+(TODO|DONE|WAITING|CANCELLED|IN-PROGRESS)?\s*(?:\[#([ABC])\])?\s*(.*?)(?:\s+(:[^\s:]+(?::[^\s:]+)*:))?\s*$""")
    private val propertyDrawerStartRegex = Regex("""^\s*:PROPERTIES:\s*$""")
    private val propertyDrawerEndRegex = Regex("""^\s*:END:\s*$""")
    private val propertyLineRegex = Regex("""^\s*:([^:]+):\s*(.*)$""")
    private val scheduledRegex = Regex("""^\s*SCHEDULED:\s*(.+)$""")
    private val deadlineRegex = Regex("""^\s*DEADLINE:\s*(.+)$""")
    private val closedRegex = Regex("""^\s*CLOSED:\s*(.+)$""")

    /**
     * Parse an org-mode file content into an OrgFile structure
     */
    fun parse(content: String): OrgFile {
        if (content.isBlank()) {
            return OrgFile()
        }

        val lines = content.lines()
        var lineIndex = 0
        val preambleLines = mutableListOf<String>()
        val topLevelHeadlines = mutableListOf<OrgNode.Headline>()

        // Collect preamble (content before first headline)
        while (lineIndex < lines.size && !isHeadline(lines[lineIndex])) {
            preambleLines.add(lines[lineIndex])
            lineIndex++
        }

        // Parse headlines
        while (lineIndex < lines.size) {
            val (headline, nextIndex) = parseHeadline(lines, lineIndex)
            if (headline != null) {
                topLevelHeadlines.add(headline)
                lineIndex = nextIndex
            } else {
                lineIndex++
            }
        }

        return OrgFile(
            preamble = preambleLines.joinToString("\n").trim(),
            headlines = topLevelHeadlines
        )
    }

    /**
     * Check if a line is a headline
     */
    private fun isHeadline(line: String): Boolean {
        return line.trim().startsWith("*") && headlineRegex.matches(line)
    }

    /**
     * Parse a headline and its content (including nested headlines)
     * Returns the parsed headline and the next line index to process
     */
    private fun parseHeadline(lines: List<String>, startIndex: Int): Pair<OrgNode.Headline?, Int> {
        if (startIndex >= lines.size || !isHeadline(lines[startIndex])) {
            return null to startIndex + 1
        }

        val headlineLine = lines[startIndex]
        val match = headlineRegex.matchEntire(headlineLine) ?: return null to startIndex + 1

        val level = match.groupValues[1].length
        val todoState = match.groupValues[2].takeIf { it.isNotBlank() }
        val priority = match.groupValues[3].takeIf { it.isNotBlank() }
        val title = match.groupValues[4].trim()
        val tags = match.groupValues[5].takeIf { it.isNotBlank() }
            ?.trim(':')
            ?.split(':')
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        var lineIndex = startIndex + 1
        var scheduled: String? = null
        var deadline: String? = null
        var closed: String? = null
        val properties = mutableMapOf<String, String>()
        val bodyLines = mutableListOf<String>()
        val children = mutableListOf<OrgNode.Headline>()

        // Parse planning line (SCHEDULED, DEADLINE, CLOSED)
        while (lineIndex < lines.size && lines[lineIndex].isNotBlank()) {
            val line = lines[lineIndex]

            // Check for SCHEDULED
            val scheduledMatch = scheduledRegex.matchEntire(line)
            if (scheduledMatch != null) {
                scheduled = scheduledMatch.groupValues[1].trim()
                lineIndex++
                continue
            }

            // Check for DEADLINE
            val deadlineMatch = deadlineRegex.matchEntire(line)
            if (deadlineMatch != null) {
                deadline = deadlineMatch.groupValues[1].trim()
                lineIndex++
                continue
            }

            // Check for CLOSED
            val closedMatch = closedRegex.matchEntire(line)
            if (closedMatch != null) {
                closed = closedMatch.groupValues[1].trim()
                lineIndex++
                continue
            }

            // Check for property drawer
            if (propertyDrawerStartRegex.matches(line)) {
                lineIndex++
                while (lineIndex < lines.size && !propertyDrawerEndRegex.matches(lines[lineIndex])) {
                    val propMatch = propertyLineRegex.matchEntire(lines[lineIndex])
                    if (propMatch != null) {
                        properties[propMatch.groupValues[1].trim()] = propMatch.groupValues[2].trim()
                    }
                    lineIndex++
                }
                if (lineIndex < lines.size && propertyDrawerEndRegex.matches(lines[lineIndex])) {
                    lineIndex++
                }
                continue
            }

            // If it's not a planning line or property drawer, break
            break
        }

        // Parse body and nested headlines
        while (lineIndex < lines.size) {
            val line = lines[lineIndex]

            // Check if this is a headline at same or higher level (sibling or parent's sibling)
            if (isHeadline(line)) {
                val childLevel = line.takeWhile { it == '*' }.length
                if (childLevel <= level) {
                    // This is a sibling or parent's sibling, stop here
                    break
                } else if (childLevel == level + 1) {
                    // This is a direct child
                    val (childHeadline, nextIndex) = parseHeadline(lines, lineIndex)
                    if (childHeadline != null) {
                        children.add(childHeadline)
                        lineIndex = nextIndex
                        continue
                    }
                }
            }

            // Add to body if not a nested headline
            if (!isHeadline(line) || line.takeWhile { it == '*' }.length > level + 1) {
                bodyLines.add(line)
            }
            lineIndex++
        }

        return OrgNode.Headline(
            level = level,
            todoState = todoState,
            priority = priority,
            title = title,
            tags = tags,
            scheduled = scheduled,
            deadline = deadline,
            closed = closed,
            properties = properties,
            body = bodyLines.joinToString("\n").trim(),
            children = children
        ) to lineIndex
    }
}
