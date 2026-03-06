# Note Taker — Documentation Index

Welcome to the Note Taker project documentation. This index provides a map of all documentation files, categorized by their purpose.

## 🗺️ Master Guides
- **[Requirements](REQUIREMENTS.md)**: Functional and non-functional requirements (FR1-FR16).
- **[Roadmap](ROADMAP.md)**: Current status of features and future development phases.
- **[Architecture Overview](architecture/OVERVIEW.md)**: High-level design, tech stack, and layering.
- **[Data & System Flows](FLOWS.md)**: Visualizing how data moves through the app.
- **[System Quirks](QUIRKS.md)**: Documentation of non-obvious behaviors (quick.org, layering, audio focus).

## 🏗️ Architecture & Decisions
Detailed technical design and Architecture Decision Records (ADR).
- **[ADR Index](architecture/OVERVIEW.md#adrs)**: Direct links to all design decisions.
- **[001: Authentication Strategy](architecture/001-pat-over-oauth.md)**: Deciding between PAT and OAuth.
- **[002: Nepali Language Support](architecture/002-nepali-language-support.md)**: Phased implementation of Nepali speech-to-text.
- **[003: Agenda View Architecture](architecture/003-agenda-view-with-orgzly-architecture.md)**: The database-centric design for fast agenda loading.
- **[004: High-Fidelity Org Viewer](architecture/004-high-fidelity-org-viewer.md)**: Using an AST-driven approach for the viewer.
- **[005: Agenda Architecture Alternatives](architecture/005-agenda-architecture-alternatives.md)**: Comparing single-file vs database approaches.
- **[Auth Flows](architecture/AUTH-FLOWS.md)**: Detailed explanation of GitHub OAuth and PAT authentication.

## 🔄 Sync & Data Logic
- **[JSON Sync Summary](JSON_SYNC_SUMMARY.md)**: Overview of the v0.9.0 sync simplification.
- **[JSON Sync Implementation Plan](JSON_SYNC_IMPLEMENTATION_PLAN.md)**: Technical spec for JSON-based status updates.
- **[JSON Sync Diagrams](JSON_SYNC_DIAGRAMS.md)**: Visual flow of state changes.
- **[Pending Sync Layering](research/PENDING_SYNC_LAYERING.md)**: Solving the "Stale File Reversion" flaw.

## 📱 Features & Workflows
- **[Quick Task Workflow](QUICK_TASK_WORKFLOW.md)**: How the FAB-triggered quick task system works.
- **[Phone Time Tracking](PHONE-TIME-TRACKING.md)**: Logic for `PHONE_STARTED` and `PHONE_ENDED` properties.
- **[Toggl Integration](TOGGL-INTEGRATION.md)**: Detailed guide on time tracking setup.
- **[WIREFRAMES](WIREFRAMES.md)**: UI design mockups and layout plans.

## 📖 User Guides
- **[App Trigger Guide](guides/APP-TRIGGER.md)**: Setting up the app for lock screen/assistant launch.
- **[PAT Setup Guide](guides/PAT-SETUP.md)**: How to generate and use a GitHub Personal Access Token.

## 🛠️ Developer & Testing
- **[TDD Workflow](developer/TDD_WORKFLOW.md)**: Our Test-Driven Development process.
- **[Testing Strategy](testing/TEST_STRATEGY.md)**: Overall test plan and coverage goals.
- **[Testing Guide](developer/TESTING_GUIDE.md)**: Practical guide on running and writing tests.
- **[Build Enforcement](developer/BUILD_ENFORCEMENT.md)**: Gradle and CI/CD rules.
- **[Deployment Guide](developer/DEPLOYMENT.md)**: CI/CD pipeline and release process (v0.1.0 - v0.8.0).

## 🧪 Research & History
- **[Agenda Debugging](AGENDA-DEBUGGING.md)**: Troubleshooting items not showing in agenda.
- **[Conflicts & Issues](research/CONFLICTS_AND_ISSUES_HISTORICAL.md)**: Historical report of major code conflicts (v0.8.0).
- **[Orgzly Architecture Analysis](research/orgzly-architecture-analysis.md)**: Deep dive into Orgzly's DB design.
- **[Research Index](RESEARCH.md)**: Summary of various technical explorations.

---
*Last Updated: 2026-03-04*
