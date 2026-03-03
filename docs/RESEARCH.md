# Research

Structured research on various topics.
All the documents of the research are to be kept inside research/ folder in the model defined below in this document.
## Overview

Research follows four phases:

0. **Requirements Gathering** (optional) - Clarify constraints before diving in
1. **Setup** - Create folder and files
2. **Research** - Search systematically, log findings incrementally
3. **Synthesis** - Distill into a final report

Each topic gets its own folder. The separation of raw research (background.md) from clean output (report.md) allows you to gather tons of information and log it carefully so it can be referenced later, while still providing a clean report.

## Structure
Keep every new research inside research/ folder following this structure.
```
{topic}/
├── requirements.md    # User constraints and context (optional)
├── background.md      # Raw research notes, citations, excerpts
└── report.md          # Final distilled writeup
```

For comparison research (evaluating multiple options):

```
{topic}/
├── research_overview.md   # Criteria, sources, methodology
├── {option_1}.md          # Notes on each option
├── {option_2}.md
└── report.md   # Final analysis
```

## Phase 0: Requirements Gathering (Optional)

If the research topic is ambiguous or has unstated constraints, clarify before diving in.

1. **Ask probing questions** - Short list, focused on what will shape the research
2. **Create requirements.md** - Capture the answers so they don't get lost
3. **Then start research** - Now you know what you're actually looking for

Examples of things to clarify:
- Budget or price constraints
- Must-have vs nice-to-have features
- Specific use case or contextts

Skip this phase if the request is already clear and specific.

## Phase 1: Setup

Before any research, create the folder and files.

### Checklist

1. Create a folder for the topic (lowercase with hyphens, e.g., `message-queue-comparison`)
2. Create `background.md` with:
   - Header with research topic and date
   - Empty `## Sources` section
   - Empty `## Key Findings` section
3. If requirements were gathered, ensure `requirements.md` captures user constraints

## Phase 2: Research

### Planning Searches

Plan 5-10 initial searches across these categories:
- Core concepts and definitions
- Current best practices
- Common challenges and solutions
- Recent developments and trends
- Authoritative sources (official docs, academic papers, expert opinions)

### Search Principles

- **Use open-ended queries** - Don't pre-specify expected answers
- **Prioritize quality sources** - Target reputable sites for the domain (journals, official docs, expert forums)
- **Note contradictions** - When sources disagree, capture both views
- **Match source age to topic velocity**:
  - Fast-moving fields (tech, pricing, current events) → prioritize recent sources
  - Stable domains (history, established science) → older authoritative sources still valuable
  - Mixed topics → recent for current state, older for foundational context

### After Each Search

After each search that yields useful results:

1. Append findings to `background.md` immediately—don't wait
2. Log the search query used
3. Record key information with source attribution
4. Note any follow-up questions or related topics discovered

If a search yields nothing useful, note this briefly and move on.

### Targeted Searches

After initial searches, you'll discover topics needing deeper exploration. Create a second round of searches based on what you've learned—use the follow-up questions you noted.

### Background File Format

- Organize by topic with `## Section` headers
- Bold key findings for scannability
- Use markdown reference-style links at end of each section
- Include publication/source in link text: `([Source: Title][1])`
- Include dates for time-sensitive information (pricing, versions, market data)
- Preserve nuance—don't flatten "debated" into "confirmed"
- Cross-reference important claims across multiple sources
- Be explicit about gaps—note when information is unavailable or uncertain

## Phase 3: Synthesis

### Executive Summary

Every report opens with a summary that answers the core question. The structure of the summary should be driven by the research topic itself—what matters depends on what you're researching.

- Lead with the conclusion or recommendation
- Include the key details that support it
- Keep it dense but readable—no filler, no throat-clearing

### Comparison Tables

Use tables when comparing options across objective factors. Good candidates:

- Pricing tiers
- Feature presence/absence
- Measurable specs
- Support for specific integrations

Avoid tables for subjective assessments (ease of use, quality, value). Those belong in prose where you can explain the reasoning.

### Tone

Write for a general audience while preserving rigor. Engaging without being overblown—no breathless superlatives but also no dry recitation.

- **Accessible** - Avoid jargon; when technical terms are necessary, provide context
- **Grounded** - Stick to what the evidence supports; flag uncertainty honestly
- **Restrained** - Trust the reader; no hype, no hedging everything into mush
