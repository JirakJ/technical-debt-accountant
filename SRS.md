# Technical Debt Accountant — Software Requirements Specification

**Verze dokumentu:** 1.0
**Plugin verze:** 2026.2.1
**Autor:** Jakub Jirák
**Datum:** 13. února 2026

---

## 1. Přehled

### 1.1 Účel
Technical Debt Accountant kvantifikuje technický dluh v peněžních jednotkách ($/Kč), sleduje „úroky" (čas strávený workaroundy) a počítá ROI refactoringu. Překlenuje komunikační propast mezi vývojáři a managementem tím, že převádí technické metriky do business jazyka.

### 1.2 Rozsah
- JetBrains IDE plugin (IntelliJ IDEA, WebStorm, PyCharm, GoLand, …)
- Freemium model (Free / Pro $8/měsíc / Enterprise $15/dev/měsíc)
- Package: `com.jakubjirak.debtaccountant`
- Plugin ID: `com.jakubjirak.debtaccountant`

### 1.3 Cílová platforma
| Parametr | Hodnota |
|----------|---------|
| IntelliJ Platform | 2024.3+ |
| sinceBuild | 243 |
| untilBuild | 252.* |
| JVM | 17 |
| Kotlin | 2.1.0 |
| Gradle Plugin | IntelliJ Platform 2.3.0 |

---

## 2. Funkční požadavky

### FR-1: Debt Inventory (Katalog tech dluhu)
**Priorita:** P0 — Core
**Tier:** Free

Plugin umožní vytvořit, upravit a smazat položky technického dluhu (TechDebtItem) přímo v IDE.

**Akceptační kritéria:**
- [ ] Uživatel přidá debt item přes Alt+D na aktuální řádek
- [ ] Každý item má: popis, kategorii, odhadovaný čas opravy, soubor, řádky
- [ ] Items jsou persistentně uloženy v `.tech-debt/inventory.json`
- [ ] Soubor je Git-tracked (sdílení s týmem)
- [ ] CRUD operace přes tool window

### FR-2: Debt Categories (Kategorizace)
**Priorita:** P0 — Core
**Tier:** Free

Každý debt item je zařazen do kategorie.

**Kategorie:**
| Enum | Popis |
|------|-------|
| `CODE_SMELL` | Špatná struktura kódu |
| `MISSING_TESTS` | Chybějící testy |
| `OUTDATED_DEPENDENCY` | Zastaralá závislost |
| `SECURITY_VULNERABILITY` | Bezpečnostní problém |
| `PERFORMANCE_ISSUE` | Výkonnostní problém |
| `DOCUMENTATION_GAP` | Chybějící dokumentace |
| `ARCHITECTURE_VIOLATION` | Porušení architektury |
| `HARDCODED_VALUE` | Hardcoded hodnota |
| `COPY_PASTE_CODE` | Duplicitní kód |

**Akceptační kritéria:**
- [ ] Dropdown výběr kategorie při vytváření
- [ ] Filtrování v tool window podle kategorie
- [ ] Ikona/barva per kategorie

### FR-3: Workaround Time Tracker
**Priorita:** P0 — Core
**Tier:** Free

Uživatel zaznamenává čas strávený workaroundy kvůli existujícímu dluhu.

**Akceptační kritéria:**
- [ ] Tlačítko „Log workaround time" u každého debt itemu
- [ ] Ruční zadání minut/hodin
- [ ] Timer režim (start/stop)
- [ ] Kumulativní součet za item, soubor, projekt
- [ ] Historie logů s timestampy

### FR-4: Hotspot Detection
**Priorita:** P1
**Tier:** Free

Identifikace souborů s nejvyšší koncentrací tech dluhu.

**Akceptační kritéria:**
- [ ] Seřazení souborů podle počtu debt items
- [ ] Seřazení podle kumulativního workaround time
- [ ] Gutter ikony v editoru pro soubory s debt items
- [ ] Barevné kódování severity v project tree

### FR-5: Dollar Valuation (Dolarová valuace)
**Priorita:** P0
**Tier:** Pro

Převod časových nákladů na peněžní hodnotu.

**Akceptační kritéria:**
- [ ] Nastavení hourly rate v Settings (výchozí $80/h)
- [ ] Nastavení měny (USD, EUR, CZK, GBP)
- [ ] Automatický přepočet: `workaroundTime × hourlyRate = debtCost`
- [ ] Zobrazení $ hodnoty u každého itemu, souboru, projektu
- [ ] Currency formatting dle locale

### FR-6: Interest Rate Calculator (Úroková sazba)
**Priorita:** P1
**Tier:** Pro

Sledování jak dluh „roste" v čase — recurring workaround costs.

**Akceptační kritéria:**
- [ ] Výpočet: `monthlyInterest = avgWorkaroundTimePerMonth × hourlyRate`
- [ ] Trend: roste/klesá/stabilní
- [ ] Projekce: „Za 6 měsíců tento dluh bude stát $X"
- [ ] Annualized cost zobrazení

### FR-7: ROI Calculator
**Priorita:** P0
**Tier:** Pro

Výpočet návratnosti refactoringu.

**Akceptační kritéria:**
- [ ] Vstup: odhadovaný čas refactoringu (fix time)
- [ ] Vstup: odhadovaná úspora workaround času po opravě
- [ ] Výpočet: `ROI = fixCost / monthlySavings` (v měsících)
- [ ] Doporučení: „Refactoring se zaplatí za X týdnů/měsíců"
- [ ] Prioritizace debt items podle ROI

### FR-8: Debt Burndown Chart
**Priorita:** P1
**Tier:** Pro

Vizualizace splácení dluhu v čase.

**Akceptační kritéria:**
- [ ] Timeline: nové debt items vs. vyřešené
- [ ] Kumulativní $ hodnota dluhu v čase
- [ ] Text-based chart v tool window
- [ ] Export data pro externí vizualizaci (CSV)

### FR-9: Management Export
**Priorita:** P1
**Tier:** Pro

Generování reportů v business jazyce.

**Akceptační kritéria:**
- [ ] Markdown report s executive summary
- [ ] Obsahuje: celkový dluh ($), top 5 hotspotů, ROI příležitosti, trend
- [ ] Copy-to-clipboard pro Confluence/Jira
- [ ] Konfigurovatelná šablona

### FR-10: Team Aggregation
**Priorita:** P2
**Tier:** Enterprise

Agregace dluhu přes celý tým.

**Akceptační kritéria:**
- [ ] Sdílený `.tech-debt/` directory přes Git
- [ ] Per-author statistiky (kdo vytváří/řeší dluh)
- [ ] Team-level dashboard v tool window

### FR-11: Git Integration
**Priorita:** P1
**Tier:** Free (basic) / Pro (full)

Propojení s Git historií.

**Akceptační kritéria:**
- [ ] Debt item linkován na commit kde vznikl (Free)
- [ ] Git blame integrace — kdo „vytvořil" dluh (Free)
- [ ] Age tracking — jak dlouho dluh existuje (Free)
- [ ] Debt per branch analýza (Pro)

---

## 3. Nefunkční požadavky

### NFR-1: Výkon
- Inventory load < 200ms pro 1000 items
- Gutter rendering nesmí blokovat EDT
- Background scanning pro hotspot detection

### NFR-2: Persitence
- JSON storage v `.tech-debt/inventory.json`
- Git-friendly formát (mergeable)
- Backward compatible schéma

### NFR-3: Bezpečnost
- Žádné citlivé údaje v debt items
- Hourly rate uložen lokálně (ne v Git)

### NFR-4: Kompatibilita
- Všechna JetBrains IDE (IntelliJ, WebStorm, PyCharm, GoLand, Rider, …)
- Light + Dark theme support

---

## 4. Datový model

```kotlin
data class TechDebtItem(
    val id: String,                          // UUID
    val filePath: String,                    // relativní cesta
    val lineStart: Int,
    val lineEnd: Int,
    val category: DebtCategory,
    val description: String,
    val estimatedFixTime: Long,              // minuty
    val workaroundLogs: List<WorkaroundLog>,
    val createdAt: Long,                     // epoch millis
    val resolvedAt: Long?,                   // null = active
    val author: String,
    val linkedCommit: String?,               // commit hash
    val tags: Set<String>,
    val priority: DebtPriority
)

data class WorkaroundLog(
    val id: String,
    val timestamp: Long,
    val durationMinutes: Int,
    val note: String
)

enum class DebtCategory {
    CODE_SMELL, MISSING_TESTS, OUTDATED_DEPENDENCY,
    SECURITY_VULNERABILITY, PERFORMANCE_ISSUE,
    DOCUMENTATION_GAP, ARCHITECTURE_VIOLATION,
    HARDCODED_VALUE, COPY_PASTE_CODE
}

enum class DebtPriority { CRITICAL, HIGH, MEDIUM, LOW }

data class DebtValuation(
    val item: TechDebtItem,
    val totalWorkaroundMinutes: Int,
    val totalCost: Double,                   // hourlyRate × hours
    val monthlyInterest: Double,             // recurring monthly cost
    val estimatedFixCost: Double,            // fixTime × hourlyRate
    val roiMonths: Double                    // fixCost / monthlyInterest
)

data class DebtReport(
    val projectName: String,
    val generatedAt: Long,
    val totalItems: Int,
    val activeItems: Int,
    val totalDebtCost: Double,
    val monthlyInterest: Double,
    val topHotspots: List<FileHotspot>,
    val topRoiOpportunities: List<DebtValuation>,
    val trend: DebtTrend
)

enum class DebtTrend { IMPROVING, STABLE, WORSENING }
```

---

## 5. Freemium Model

| Feature | Free | Pro ($8) | Enterprise ($15/dev) |
|---------|:----:|:--------:|:-------------------:|
| Debt Inventory | ✅ (max 50 items) | ✅ Unlimited | ✅ Unlimited |
| Categories | ✅ | ✅ | ✅ |
| Workaround Timer | ✅ | ✅ | ✅ |
| Hotspot Detection | ✅ | ✅ | ✅ |
| Git Integration (basic) | ✅ | ✅ | ✅ |
| Dollar Valuation | ❌ | ✅ | ✅ |
| Interest Rate | ❌ | ✅ | ✅ |
| ROI Calculator | ❌ | ✅ | ✅ |
| Burndown Chart | ❌ | ✅ | ✅ |
| Management Export | ❌ | ✅ | ✅ |
| Team Aggregation | ❌ | ❌ | ✅ |
| Sprint Debt Budget | ❌ | ❌ | ✅ |
| Jira/Linear Integration | ❌ | ❌ | ✅ |
| Trend Analysis | ❌ | ❌ | ✅ |

---

## 6. Závislosti

| Knihovna | Verze | Účel |
|---------|-------|------|
| Kotlin | 2.1.0 | Jazyk |
| IntelliJ Platform | 2024.3 | Cílová platforma |
| Gson | 2.11.0 | JSON persistence |
| Git4Idea | optional | Git integrace |
| JUnit 5 | 5.11.4 | Testy |
| Detekt | 1.23.7 | Static analysis |
