# Technical Debt Accountant — Implementation Plan

**Plugin:** Technical Debt Accountant
**Verze:** 2026.2.1
**Package:** `com.jakubjirak.debtaccountant`

---

## Fáze 0: Plugin Ikona & Branding
- [ ] Vytvořit `pluginIcon.svg` (light theme) — motiv: kalkulačka/účetní kniha s kódem
- [ ] Vytvořit `pluginIcon_dark.svg` (dark theme)
- [ ] Připravit marketplace assets (banner, screenshots mockupy)

## Fáze 1: Scaffolding
- [ ] `gradle.properties` — pluginGroup, pluginName, pluginVersion=2026.2.1
- [ ] `settings.gradle.kts` — pluginManagement, rootProject.name
- [ ] `build.gradle.kts` — IntelliJ Platform Gradle Plugin 2.3.0, detekt, dependencies
- [ ] `gradle/libs.versions.toml` — version catalog (kotlin, intellij-platform, gson, junit, detekt)
- [ ] Gradle wrapper (gradlew, gradle-wrapper.jar, gradle-wrapper.properties)
- [ ] `.gitignore`, `.editorconfig`, `detekt.yml`, `LICENSE` (MIT)
- [ ] `.github/workflows/build.yml` — CI pipeline
- [ ] `src/main/resources/META-INF/plugin.xml` — extension registrations
- [ ] `src/main/resources/messages/DebtAccountantBundle.properties`

## Fáze 2: Model
- [ ] `model/DebtCategory.kt` — enum (9 kategorií)
- [ ] `model/DebtPriority.kt` — enum (CRITICAL, HIGH, MEDIUM, LOW)
- [ ] `model/WorkaroundLog.kt` — data class (id, timestamp, durationMinutes, note)
- [ ] `model/TechDebtItem.kt` — data class (id, filePath, lineStart/End, category, description, estimatedFixTime, workaroundLogs, timestamps, author, commit, tags, priority)
- [ ] `model/DebtValuation.kt` — data class (totalCost, monthlyInterest, roiMonths)
- [ ] `model/DebtTrend.kt` — enum (IMPROVING, STABLE, WORSENING)
- [ ] `model/FileHotspot.kt` — data class (filePath, debtCount, totalWorkaroundMinutes)
- [ ] `model/DebtReport.kt` — data class (projectName, stats, hotspots, ROI opportunities, trend)

## Fáze 3: Storage (DebtInventoryStore)
- [ ] `store/DebtInventoryStore.kt` — project service
  - [ ] Load/save `.tech-debt/inventory.json` s Gson
  - [ ] CRUD operace (add, update, delete, resolve)
  - [ ] Filtering (by category, file, priority, status)
  - [ ] VFS listener pro auto-reload
  - [ ] Thread-safe operace

## Fáze 4: Workaround Time Tracker
- [ ] `tracker/WorkaroundTracker.kt`
  - [ ] Log workaround time (manual entry)
  - [ ] Timer mode (start/stop)
  - [ ] Kumulativní statistiky per item, file, project
  - [ ] History s timestampy

## Fáze 5: Dollar Valuation Engine
- [ ] `valuation/DollarValuator.kt`
  - [ ] Přepočet: workaroundMinutes × (hourlyRate / 60) = cost
  - [ ] Per-item, per-file, per-project valuace
  - [ ] Currency formatting (USD, EUR, CZK, GBP)
- [ ] `valuation/InterestCalculator.kt`
  - [ ] Monthly interest: avgMonthlyWorkaround × hourlyRate
  - [ ] Trend detection (posledních 3 měsíců)
  - [ ] Projekce: 6/12 měsíců dopředu
- [ ] `valuation/RoiCalculator.kt`
  - [ ] ROI = fixCost / monthlySavings (v měsících)
  - [ ] Prioritizace items podle ROI
  - [ ] Break-even point

## Fáze 6: Hotspot Detection
- [ ] `hotspot/HotspotDetector.kt`
  - [ ] Agregace debt items per soubor
  - [ ] Scoring: count × severity + workaround time
  - [ ] Top N hotspotů
  - [ ] Integration s project tree

## Fáze 7: Burndown Calculator
- [ ] `burndown/BurndownCalculator.kt`
  - [ ] Timeline: nové items vs. resolved items per den/týden
  - [ ] Kumulativní $ hodnota dluhu v čase
  - [ ] Data export pro vizualizaci (CSV)
  - [ ] Trend line

## Fáze 8: Report Generator
- [ ] `report/ReportGenerator.kt`
  - [ ] Markdown report template
  - [ ] Executive summary (total debt $, trend, top hotspots)
  - [ ] ROI opportunities section
  - [ ] Copy-to-clipboard functionality
  - [ ] Konfigurovatelná šablona

## Fáze 9: Git Integration
- [ ] `git/GitDebtLinker.kt`
  - [ ] Link debt item k commit hash
  - [ ] Git blame — kdo „vytvořil" dluh
  - [ ] Age tracking — jak dlouho existuje
  - [ ] Optional Git4Idea dependency

## Fáze 10: UI — Tool Window
- [ ] `ui/DebtToolWindowFactory.kt`
  - [ ] Tabulka debt items (sortable, filterable)
  - [ ] Kategorie filter tabs
  - [ ] Search bar
  - [ ] Quick actions (add, resolve, log time)
  - [ ] Summary panel ($, count, trend)
- [ ] `ui/AddDebtDialog.kt`
  - [ ] Formulář: popis, kategorie, priorita, estimated fix time
  - [ ] Auto-fill soubor + řádek z kontextu
  - [ ] Tags input

## Fáze 11: UI — Gutter & Actions
- [ ] `ui/DebtGutterIconRenderer.kt` — ikony v gutteru
- [ ] `ui/AddDebtAction.kt` — Alt+D rychlé přidání
- [ ] `ui/LogWorkaroundAction.kt` — quick log time
- [ ] `ui/GenerateReportAction.kt` — generate management report

## Fáze 12: Settings
- [ ] `settings/DebtAccountantSettings.kt` — PersistentStateComponent
  - [ ] hourlyRate (default 80)
  - [ ] currency (default USD)
  - [ ] inventoryPath (default .tech-debt/)
  - [ ] autoDetect enabled
  - [ ] maxFreeItems (50)
- [ ] `settings/DebtAccountantConfigurable.kt` — Settings UI page

## Fáze 13: Freemium & Licensing
- [ ] `licensing/FreemiumGate.kt`
  - [ ] LicenseStatus: FREE, TRIAL, PRO, ENTERPRISE
  - [ ] Feature gating per tier
  - [ ] Max 50 items v Free tier
  - [ ] JetBrains Marketplace Licensing API připraveno

## Fáze 14: Production-Grade Quality
- [ ] Error handling v každé komponentě
- [ ] Detekt clean (0 issues)
- [ ] .editorconfig dodržován
- [ ] GitHub Actions CI green
- [ ] Plugin verification pass

## Fáze 15: Testy
- [ ] `DebtInventoryStoreTest` — CRUD, filtering, persistence
- [ ] `DollarValuatorTest` — valuace, currency formatting
- [ ] `InterestCalculatorTest` — monthly interest, trend
- [ ] `RoiCalculatorTest` — ROI výpočet, prioritizace
- [ ] `HotspotDetectorTest` — scoring, ranking
- [ ] `BurndownCalculatorTest` — timeline data
- [ ] `ReportGeneratorTest` — markdown output
- [ ] ≥ 80% code coverage cíl

## Fáze 16: Release
- [ ] README.md s feature listem a screenshoty
- [ ] CHANGELOG.md
- [ ] Marketplace listing text
- [ ] Final `./gradlew build` green
- [ ] Git tag v2026.2.1

---

## Budoucí fáze (post-MVP)
- Sprint Debt Budget
- Jira/Linear Integration
- Team Aggregation Dashboard
- Compliance Reports (ISO, SOC 2)
- AI-powered debt detection suggestions
- VS Code port
