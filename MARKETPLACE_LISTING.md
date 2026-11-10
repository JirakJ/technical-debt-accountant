# Technical Debt Accountant — JetBrains Marketplace Listing

---

## 📝 Plugin Information

| Field | Value |
|-------|-------|
| **Name** | Technical Debt Accountant |
| **Plugin ID** | `com.jakubjirak.debtaccountant` |
| **Version** | 2026.7.0 |
| **Category** | Code Quality |
| **Tags** | `technical-debt`, `code-quality`, `refactoring`, `roi`, `debt-tracking`, `workaround`, `hotspot`, `maintenance`, `metrics` |

---

## 🎯 Short Description

> Quantify technical debt in dollars, track workaround costs with timers, calculate ROI for refactoring, and generate executive reports — all inside your JetBrains IDE.

---

## 📖 Full Description

### 💵 Turn "We Should Refactor This" Into a Business Case With Real Numbers

**The Problem:** Technical debt is invisible. Developers know it exists, but they can't quantify it. Management can't prioritize refactoring because there's no data showing the cost of inaction. Workaround time silently drains productivity.

**The Solution:** Technical Debt Accountant turns vague "we need to fix this" into concrete dollar amounts. Catalog debt items, track workaround time, calculate monthly interest costs, and generate executive-ready reports that make the business case for refactoring — all from within your IDE.

---

### ✨ Key Features

#### 📋 Debt Inventory
Catalog technical debt items with category, priority, description, estimated fix time, and tags. Press **Alt+D** to log debt at the current cursor position with file and line auto-filled.

#### 🏷️ 9 Debt Categories
Code Smell, Missing Tests, Outdated Dependency, Security Vulnerability, Performance Issue, Documentation Gap, Architecture Violation, Hardcoded Value, and Copy-Paste Code.

#### ⚖️ 4 Priority Levels
Critical (4× weight), High (3×), Medium (2×), Low (1×) — priority multipliers ensure the worst debt surfaces first.

#### ⏱️ Workaround Timer
Start/stop a timer or manually log workaround minutes with notes. Every minute spent working around debt is tracked and converted to cost.

#### 🔥 Hotspot Detection
Identifies files with the most concentrated debt by weighted score: `(priority.weight × 10) + (workaroundMinutes × 0.5)`. View the top 10 hotspots in the Tech Debt tool window.

#### 🔗 Git Integration
Links debt items to commits and tracks file authors. Data stored in `.tech-debt/inventory.json` — fully Git-trackable.

#### 🎯 Gutter Icons
Priority-colored markers in the editor gutter with debt tooltips for instant visual feedback.

#### 💰 Dollar Valuation
Converts workaround time to cost using a configurable hourly rate. See total workaround cost, estimated fix cost, and monthly interest for every debt item.

#### 📊 ROI Calculator
Calculates breakeven months (`fix cost ÷ monthly interest`) and prioritizes debt items by ROI — fix the items that pay for themselves fastest.

#### 📉 Burndown Chart
Tracks debt resolution trends over time with status indicators: IMPROVING, STABLE, or WORSENING.

#### 📄 Management Export
Generates Markdown reports with executive summary, priority/category breakdown, top 5 hotspots, and top 5 ROI opportunities. Copied to clipboard for Slack, email, or docs.

---

### 💰 Pricing Model

Free & open source (Apache-2.0) — every feature is available to everyone, no license needed.


---

### 🎯 Perfect For

- **Developers** who want to make technical debt visible and trackable
- **Tech Leads** building a data-driven case for refactoring sprints
- **Engineering Managers** who need to quantify the cost of deferred maintenance
- **Architects** tracking architecture violations and their impact
- **Teams** practicing continuous improvement with measurable outcomes

---

### ⚙️ Requirements

| Requirement | Value |
|-------------|-------|
| **IDE Version** | IntelliJ IDEA 2024.3+ (build 232+) |
| **Java** | 17+ |
| **Optional** | Git4Idea plugin (for Git integration features) |

---

### 🚀 Quick Start

1. **Install** — Settings → Plugins → Marketplace → Search "Technical Debt Accountant"
2. **Configure** — Settings → Tools → Technical Debt Accountant (set hourly rate, currency)
3. **Log debt** — Place cursor on problematic code → Press **Alt+D** → Fill in category, priority, description
4. **Track workarounds** — Tools → Log Workaround Time → Select debt item → Enter minutes spent
5. **View inventory** — Open the **Tech Debt** tool window (bottom panel)
6. **Generate report** — Tools → Generate Debt Report → Paste into Slack/email/docs

---

## 📸 Screenshots & Media

Available in [`screenshots/`](screenshots/) at 1280×800:

| # | Screenshot | Description |
|---|-----------|-------------|
| 1 | `debt-health-score.png` | Debt Health Score — A–F grade with gauge, monthly interest, budget status, and the status bar widget |
| 2 | `tech-debt-tool-window.png` | Tech Debt tool window with debt inventory table, health grade + dollar summary, and priority-colored gutter icons in the editor |
| 3 | `scan-debt-comments.png` | TODO/FIXME/HACK/XXX/OPTIMIZE comment scan preview with marker→category/priority mapping |
| 4 | `add-debt-dialog.png` | Add Debt Item dialog (Alt+D) with category, priority, description, and estimated fix time fields |
| 5 | `management-report.png` | Generated executive report with summary, hotspots, and ROI opportunities |
| 6 | `debt-forecast.png` | Debt trend forecast with burndown projection and 95% confidence band |
| 7 | `export-inventory.png` | CSV/JSON export with computed workaround and fix costs |

---

## 🏷️ Marketplace Categories

| Type | Category |
|------|----------|
| **Primary** | Code Quality |
| **Secondary** | Project Management, Developer Productivity |

---

## 🔍 Keywords (SEO)

`technical debt`, `code quality`, `refactoring`, `roi`, `debt tracking`, `workaround`, `hotspot`, `maintenance cost`, `code smell`, `burndown`, `tech debt`, `dollar valuation`, `interest`, `breakeven`, `management report`, `code metrics`, `developer productivity`, `engineering metrics`, `jira`, `team analytics`

---

## 📋 Compatibility

| IDE | Supported |
|-----|-----------|
| IntelliJ IDEA Community | ✅ |
| IntelliJ IDEA Ultimate | ✅ |
| WebStorm | ✅ |
| PyCharm | ✅ |
| GoLand | ✅ |
| Rider | ✅ |
| PhpStorm | ✅ |
| CLion | ✅ |
| RubyMine | ✅ |
| Android Studio | ✅ |

| Requirement | Value |
|-------------|-------|
| **Minimum IDE Build** | 232 (2023.2) |
| **Java Version** | 17+ |

---

## 📞 Vendor Information

| Field | Value |
|-------|-------|
| **Vendor** | Jakub Jirak |
| **Email** | jirak@jakubjirak.com |
| **Website** | https://jakubjirak.com |
| **Support** | support@jakubjirak.com |
| **Source Code** | https://github.com/JirakJ/technical-debt-accountant |

---

## ✅ Pre-Launch Checklist

- [ ] Plugin icon (40×40 and 80×80 SVG/PNG) included in repository
- [ ] Plugin description under 255 characters for short description field
- [ ] Full description formatted in HTML for Marketplace submission
- [ ] Screenshots captured at 1280×800 minimum resolution
- [ ] Plugin JAR/ZIP tested on IntelliJ IDEA 2024.3 (build 232)
- [ ] Plugin JAR/ZIP tested on latest IntelliJ IDEA version
- [ ] Change notes prepared for current version
- [ ] Vendor information verified and support email tested
- [ ] `.tech-debt/` directory documented in README for Git tracking
- [ ] Plugin ID (`com.jakubjirak.debtaccountant`) registered on Marketplace
