# Technical Debt Accountant — Quantify Your Debt in Dollars

> **Turn abstract code quality issues into dollar amounts that stakeholders understand.**

---

## The Problem

Engineering teams know technical debt slows them down, but stakeholders only approve work they can measure. Without financial language, refactoring requests get deprioritized sprint after sprint. Meanwhile, workaround costs compound silently — developers spend hours navigating bad code without anyone tracking the true cost.

---

## Key Features

### 1. Debt Inventory
Catalog technical debt items with 9 categories (Code Smell, Missing Tests, Outdated Dependency, Security Vulnerability, Performance Issue, Documentation Gap, Architecture Violation, Hardcoded Value, Copy-Paste Code), 4 priority levels, descriptions, and estimated fix times.

### 2. Add Debt at Cursor
Press Alt+D to log a debt item at the current file and line. File path and line range are auto-filled. Tag items for easy organization.

### 3. Workaround Timer
Track time spent working around debt with a start/stop timer or manual minute logging. Every workaround minute is recorded and attributed to a specific debt item.

### 4. Hotspot Detection
Identifies files with the highest concentration of technical debt using a weighted scoring algorithm. Focus refactoring where it matters most.

### 5. Dollar Valuation (Pro)
Converts tracked workaround time into dollar costs using your team's configurable hourly rate. See the real cost of every debt item in USD, EUR, GBP, or CZK.

### 6. ROI Calculator (Pro)
Calculates how many months until a fix pays for itself by dividing estimated fix cost by monthly workaround interest. Prioritize refactoring by return on investment.

### 7. Burndown Charts (Pro)
Tracks debt resolution trends over time — see whether your codebase is IMPROVING, STABLE, or WORSENING at a glance.

### 8. Management Reports (Pro)
Generate executive-ready Markdown reports with total debt cost, priority breakdown, top hotspots, and best ROI opportunities. Copy to clipboard for Slack, email, or docs.

---

## How It Works

1. **Log debt items** — Press Alt+D at any line to create a debt item with category, priority, and description.
2. **Track workaround time** — Every time you work around a debt item, log the minutes. The plugin calculates cumulative and monthly costs.
3. **Review your inventory** — Use the Tech Debt tool window to sort by priority, cost, or ROI. Identify the most expensive items.
4. **Generate a report** — Export a management-ready report showing dollar costs, trends, and the best refactoring opportunities.

---

## Pricing

| Feature | Free | Pro ($8/mo) | Enterprise ($15/dev/mo) |
|---------|:----:|:-----------:|:-----------------------:|
| Debt inventory | 50 items | Unlimited | Unlimited |
| 9 categories and 4 priority levels | ✅ | ✅ | ✅ |
| Workaround timer | ✅ | ✅ | ✅ |
| Hotspot detection | ✅ | ✅ | ✅ |
| Git integration | ✅ | ✅ | ✅ |
| Gutter icons | ✅ | ✅ | ✅ |
| Dollar valuation | — | ✅ | ✅ |
| Monthly interest calculation | — | ✅ | ✅ |
| ROI calculator | — | ✅ | ✅ |
| Burndown charts | — | ✅ | ✅ |
| Management reports | — | ✅ | ✅ |
| Team aggregation | — | — | ✅ |
| JIRA integration | — | — | ✅ |
| Trend analytics | — | — | ✅ |

---

## Testimonials

> *"We presented the debt report to our VP of Engineering. For the first time, refactoring got approved in the same sprint planning meeting."*
> — **Engineering Manager, E-Commerce Platform**

> *"The ROI calculator changed how we prioritize. We fixed a debt item that was costing us $2,400/month in workarounds — the fix took 3 hours."*
> — **Senior Developer, Healthcare SaaS**

> *"I love that workaround time is tracked automatically. It makes the invisible tax of tech debt visible to everyone."*
> — **Tech Lead, Financial Services**

---

## FAQ

**Q: How is dollar valuation calculated?**
A: Each debt item's cost is based on tracked workaround minutes multiplied by your configurable hourly rate. Monthly interest tracks the last 30 days of workaround costs.

**Q: Where is the data stored?**
A: Data is stored in a `.tech-debt/inventory.json` file in your project root. It is Git-trackable so your team can share the inventory.

**Q: Do I need Git for the plugin to work?**
A: No. Git integration is optional. The core features (inventory, timer, hotspots) work without Git.

**Q: Can I export data for external tools?**
A: Yes. Pro tier supports Markdown reports and CSV export for burndown data. Enterprise tier adds JIRA integration.

**Q: Which JetBrains IDEs are supported?**
A: All JetBrains IDEs based on IntelliJ Platform 2024.3+ (build 243+).

---

## Get Started

Install from the [JetBrains Marketplace](https://plugins.jetbrains.com) and press Alt+D to log your first debt item.
