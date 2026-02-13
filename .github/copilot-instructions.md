# Technical Debt Accountant — Copilot Instructions

## Přehled projektu
JetBrains IDE plugin pro kvantifikaci technického dluhu v peněžních jednotkách. Sleduje workaround costs, počítá ROI refactoringu, generuje management reporty.

## Tech Stack
- **Jazyk:** Kotlin 2.1.0
- **Build:** Gradle 8.12, Kotlin DSL, IntelliJ Platform Gradle Plugin 2.3.0
- **Platforma:** IntelliJ Platform 2024.3+ (sinceBuild=243)
- **JVM:** 17
- **JSON:** Gson 2.11.0
- **Git:** Git4Idea (optional dependency)
- **Testy:** JUnit 5
- **Linting:** Detekt

## Architektura
```
com.jakubjirak.debtaccountant/
├── model/          # TechDebtItem, DebtCategory, WorkaroundLog, DebtPriority
├── store/          # DebtInventoryStore (JSON persistence, CRUD)
├── tracker/        # WorkaroundTracker (time logging)
├── valuation/      # DollarValuator, InterestCalculator, RoiCalculator
├── hotspot/        # HotspotDetector (file-level aggregation)
├── report/         # ReportGenerator (Markdown export)
├── burndown/       # BurndownCalculator (timeline data)
├── git/            # GitDebtLinker (commit linking, blame)
├── ui/             # ToolWindowFactory, AddDebtDialog, DebtGutterRenderer
├── settings/       # DebtAccountantSettings, Configurable
└── licensing/      # FreemiumGate
```

## Konvence
- Package: `com.jakubjirak.debtaccountant`
- Logger: vždy v `companion object` (ne jako private property)
- Freemium: všechny paid features za `FreemiumGate.isPaidFeatureAvailable()`
- Storage: `.tech-debt/inventory.json` v project root (Git-tracked)
- Settings (hourly rate, currency): lokální PersistentStateComponent (ne v Gitu)
- Ikona: pluginIcon.svg + pluginIcon_dark.svg v META-INF

## Build & Test
```bash
./gradlew build        # compile + detekt + test
./gradlew test         # pouze testy
./gradlew detekt       # static analysis
./gradlew verifyPlugin # plugin structure check
```

## Freemium Boundaries
- **Free:** max 50 debt items, categories, timer, hotspots, basic git
- **Pro:** unlimited items, $ valuation, interest rate, ROI, burndown, export
- **Enterprise:** team aggregation, sprint budget, Jira integration, trends
