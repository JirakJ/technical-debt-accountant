# Technical Debt Accountant

[![Build](https://github.com/JirakJ/technical-debt-accountant/actions/workflows/build.yml/badge.svg)](https://github.com/JirakJ/technical-debt-accountant/actions/workflows/build.yml)
[![Version](https://img.shields.io/badge/version-2026.2.1-blue.svg)](https://github.com/JirakJ/technical-debt-accountant/releases)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

> Quantify your technical debt in dollars, track workaround costs, and prioritize refactoring by ROI — all inside your JetBrains IDE.

## ✨ Features

- **Debt inventory** — catalog tech debt items with category, priority, description, and estimated fix time
- **Add debt at cursor** — press **Alt+D** to log a debt item at the current file and line
- **9 debt categories** — Code Smell, Missing Tests, Outdated Dependency, Security Vulnerability, Performance Issue, Documentation Gap, Architecture Violation, Hardcoded Value, Copy-Paste Code
- **4 priority levels** — Critical (4×), High (3×), Medium (2×), Low (1×) with weight multipliers
- **Workaround timer** — start/stop timer or manually log workaround minutes with notes
- **Hotspot detection** — identifies files with the most debt by weighted score
- **Git integration** — links debt items to commits and tracks file authors
- **Gutter icons** — priority-colored markers in the editor with debt tooltips
- **Tech Debt tool window** — table view with active count, category, priority, and workaround minutes
- **Project-level persistence** — data stored in `.tech-debt/inventory.json` (Git-trackable)
- **Dollar valuation** — converts workaround time to cost using configurable hourly rate
- **Monthly interest calculation** — tracks recurring workaround costs over 30-day windows
- **ROI calculator** — calculates breakeven months (fix cost ÷ monthly interest) and prioritizes by ROI
- **Burndown chart** — tracks debt resolution trends (IMPROVING / STABLE / WORSENING)
- **Management export** — generates Markdown reports with executive summary, hotspots, and ROI opportunities
- **CSV export** — export burndown data for external analysis
- **Team aggregation** — aggregate debt metrics across team members and projects
- **JIRA integration** — link debt items to JIRA tickets
- **Trend analytics** — long-term debt trend analysis across the organization

## 🧮 Sprint Planning Recommender

Turn your debt backlog into a ready-to-copy sprint plan.

**Tools → Suggest Sprint Items…** opens a dialog where you enter:

- **Budget (hours)** — how much refactoring capacity the sprint has
- **Minimum priority** — filter out items below `LOW` / `MEDIUM` / `HIGH` / `CRITICAL`
- **Include tags** / **Exclude tags** — comma-separated tag filters
- **Allow overflow** — optionally accept one oversize item up to +20% of the budget

The recommender scores every eligible (unresolved, effort > 0) item by
`ROI = monthly interest saved ($) ÷ fix effort (hours)` using the configured `DollarValuator`, then
greedily fills the budget. Ties are broken deterministically by priority weight, then age (older
first), then id.

The result dialog shows a `JBTable` of selected items plus totals (selected hours, monthly savings,
item count, declined count). **Copy Markdown** and **Export .md** produce a Jira/GitHub-friendly
document with a summary table, selected items table, and a collapsed *Declined items* section
(each declined item carries a reason: `BUDGET`, `FILTERED`, or `NO_EFFORT`).

## 📦 Installation

### From JetBrains Marketplace
1. Open **Settings** → **Plugins** → **Marketplace**
2. Search for **Technical Debt Accountant**
3. Click **Install** and restart the IDE

### From ZIP
1. Download the latest release from [Releases](https://github.com/JirakJ/technical-debt-accountant/releases)
2. Open **Settings** → **Plugins** → ⚙️ → **Install Plugin from Disk…**
3. Select the downloaded ZIP file and restart the IDE

## 🚀 Quick Start

1. Open a project in your JetBrains IDE
2. Place your cursor on a line with technical debt
3. Press **Alt+D** (or **Tools** → **Add Debt Item**) to log the debt
4. Fill in the category, priority, description, and estimated fix time
5. Open the **Tech Debt** tool window (bottom panel) to view your inventory
6. Use **Tools** → **Log Workaround Time** to track time spent on workarounds
7. Use **Tools** → **Generate Debt Report** to create an executive summary

## ⚙️ Configuration

Open **Settings** → **Tools** → **Technical Debt Accountant** to configure:

| Setting | Default | Description |
|---------|---------|-------------|
| Hourly Rate | `80.0` | Developer hourly rate for dollar valuation |
| Currency | `USD` | Currency for cost display (USD, EUR, GBP, CZK) |
| Inventory Path | `.tech-debt` | Directory for debt inventory storage (relative to project root) |
| Show Gutter Icons | `true` | Display priority-colored icons in the editor gutter |

## 🎯 Usage

### Adding Debt Items

Press **Alt+D** anywhere in the editor to open the Add Debt dialog:

- **File Path** — auto-filled from the current editor file
- **Line Start / End** — auto-filled from cursor position
- **Category** — select from 9 categories (Code Smell, Missing Tests, Security Vulnerability, etc.)
- **Priority** — Critical, High, Medium, or Low
- **Description** — free-text description of the debt
- **Estimated Fix (minutes)** — how long you estimate the fix would take (default: 60)
- **Tags** — comma-separated labels for organization

### Tracking Workaround Time

Use **Tools** → **Log Workaround Time** to record time spent working around debt:

- Select an active debt item from the list
- Enter the number of minutes spent
- Optionally add a note describing the workaround

Alternatively, use the timer mode: `WorkaroundTracker.startTimer()` → `stopTimer()` to track time automatically.

### Generating Reports

Use **Tools** → **Generate Debt Report** to create a Markdown report containing:

- **Executive summary** — total items, active vs. resolved, total workaround cost
- **Priority breakdown** — items and costs grouped by CRITICAL / HIGH / MEDIUM / LOW
- **Category breakdown** — items grouped by debt category
- **Top 5 hotspots** — files with the highest weighted debt score
- **Top 5 ROI opportunities** — items with the fastest payback period

The report is copied to your clipboard for pasting into docs, Slack, or email.

### Hotspot Detection

Hotspots are files with the most concentrated debt. The score is calculated as:

```
score = (priority.weight × 10) + (workaroundMinutes × 0.5)
```

Only active (unresolved) items are counted. View the top 10 hotspots in the Tech Debt tool window.

### Dollar Valuation

Each debt item's cost is calculated from tracked workaround time:

| Metric | Formula |
|--------|---------|
| Workaround Cost | `totalWorkaroundMinutes × (hourlyRate ÷ 60)` |
| Estimated Fix Cost | `estimatedFixMinutes × (hourlyRate ÷ 60)` |
| Monthly Interest | `last30DaysWorkaroundMinutes × (hourlyRate ÷ 60)` |
| ROI (Breakeven) | `estimatedFixCost ÷ monthlyInterest` months |

## 🔌 Keyboard Shortcuts

| Shortcut | Action | Description |
|----------|--------|-------------|
| **Alt+D** | Add Debt Item | Log a new tech debt item at the current cursor position |

## 📋 Requirements

- IntelliJ IDEA 2024.3+ (or any JetBrains IDE build 243–252.*)
- Git4Idea plugin (optional — for Git integration features)
- Java 17+

## 🏗️ Building from Source

```bash
git clone https://github.com/JirakJ/technical-debt-accountant.git
cd technical-debt-accountant
./gradlew build
```

The built plugin ZIP will be in `build/distributions/`.

## 📝 License

MIT — [LICENSE](LICENSE)

## 👤 Author

**Jakub Jirák** — [@JirakJ](https://github.com/JirakJ)
