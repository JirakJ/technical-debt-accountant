# Technical Debt Accountant — Business Requirements Specification

**Verze dokumentu:** 1.0
**Autor:** Jakub Jirák
**Datum:** 13. února 2026

---

## 1. Executive Summary

Technical Debt Accountant překlenuje komunikační propast mezi vývojáři a managementem tím, že kvantifikuje technický dluh v peněžních jednotkách. Plugin sleduje workaround časy, počítá ROI refactoringu, a generuje reporty ve srozumitelném business jazyce.

---

## 2. Business Objectives

### 2.1 Primární cíle
1. **Kvantifikovat tech debt v $** — poskytnout společný jazyk pro vývojáře a management
2. **Trackovat workaround costs** — ukázat skutečné náklady ignorovaného dluhu
3. **Prioritizovat refactoring** — ROI-based rozhodování
4. **Generovat management reporty** — data pro business decisions

### 2.2 KPIs
| Metrika | Target (12 měsíců) |
|---------|---------------------|
| Downloads | 15,000+ |
| Free → Pro konverze | 5% |
| Pro → Enterprise konverze | 10% of Pro |
| DAU (Free) | 2,000+ |
| Pro subscribers | 750+ |
| Enterprise teams | 30+ (150 devs) |
| MRR | $8,250+ |
| ARR | $99,000+ |
| Churn rate (Pro) | < 5% monthly |

---

## 3. Cílový trh

### 3.1 Primární segment
| Segment | Popis |
|---------|-------|
| **Tech Leads** | Potřebují kvantifikovat tech debt pro management buy-in |
| **Senior Developers** | Chtějí prioritizovat refactoring na základě dat |
| **Engineering Managers** | Potřebují reporty pro sprint planning a budget allocation |

### 3.2 Sekundární segment
| Segment | Popis |
|---------|-------|
| **CTOs/VPE** | High-level tech debt dashboards |
| **Freelancers** | Kvantifikace tech debt pro klienty |
| **Consulting firmy** | Audity kódových bází |

### 3.3 Persona: Tech Lead Martin
- 8 let zkušeností, vede tým 6 lidí
- Ví o tech debt, ale nemůže přesvědčit management k refactoring spritu
- Chce data: „Tento workaround nás stojí $2,400/měsíc, refactoring se zaplatí za 3 týdny"
- Používá IntelliJ IDEA + Jira

---

## 4. Konkurenční analýza

| Nástroj | Přístup | Naše výhoda |
|---------|---------|-------------|
| **SonarQube** | Automatická statická analýza | My = developer-recorded, skutečný kontext |
| **CodeClimate** | Technické metriky | My = business metriky ($) |
| **Manual spreadsheets** | Ad-hoc tracking | My = IDE-integrované, real-time |
| **Stepsize** (sunset 2024) | VS Code tech debt tracker | My = JetBrains, aktivní vývoj |
| **CodeScene** | SaaS behavioral analysis | My = IDE-native, okamžitá zpětná vazba |

### Competitive Moat
1. **Žádný JetBrains plugin nedělá $ valuaci tech dluhu**
2. **Developer-recorded context** — ne automatické metriky, ale reálné workaround costs
3. **ROI Calculator** — business-ready argumenty pro refactoring
4. **IDE-native** — žádné context switching

---

## 5. Revenue Model

### 5.1 Pricing
| Tier | Cena | Billing |
|------|------|---------|
| Free | $0 | — |
| Pro | $8/měsíc | Monthly/Annual (2 měsíce zdarma) |
| Enterprise | $15/dev/měsíc | Annual |

### 5.2 Revenue Projection (konzervativní)
| Měsíc | Downloads | Pro | Enterprise devs | MRR |
|-------|-----------|-----|-----------------|-----|
| 3 | 3,000 | 100 | 0 | $800 |
| 6 | 7,000 | 300 | 50 | $3,150 |
| 12 | 15,000 | 750 | 150 | $8,250 |
| 18 | 25,000 | 1,200 | 300 | $14,100 |
| 24 | 40,000 | 2,000 | 600 | $25,000 |

### 5.3 CAC a LTV
- **CAC (Free→Pro):** ~$5 (content marketing, organic)
- **Pro LTV:** $8 × 18 měsíců avg retention = **$144**
- **Enterprise LTV:** $15 × 5 devs × 24 měsíců = **$1,800/team**
- **LTV/CAC ratio:** >28x ✅

---

## 6. Go-to-Market Strategy

### 6.1 Launch (Měsíc 1-3)
1. **JetBrains Marketplace** listing s compelling screenshots
2. **Blog post**: „How I Convinced My CTO to Approve Refactoring Week"
3. **Dev Twitter/LinkedIn**: Screenshots debt dashboards, ROI calculations
4. **Reddit**: r/programming, r/java, r/kotlin posts

### 6.2 Growth (Měsíc 4-12)
1. **Conference talks**: „Speaking CFO Language as a Developer"
2. **Free web tool**: „Tech Debt Cost Calculator" → funnel to plugin
3. **Case studies**: reální příběhy úspory po refactoringu
4. **YouTube**: Demo videa, tutorial series
5. **Newsletter**: Tech Debt Weekly — tipy, best practices

### 6.3 Enterprise (Měsíc 6+)
1. **Sales outreach**: engineering managers na LinkedIn
2. **Pilot program**: 30 dní zdarma pro týmy
3. **Partner program**: consulting firmy jako resellers
4. **Compliance angle**: ISO 27001, SOC 2 reporting

---

## 7. Rizika a mitigace

| Riziko | Pravděpodobnost | Dopad | Mitigace |
|--------|:-:|:-:|---------|
| Nízká adopce — devs nechtějí manuálně logovat | Střední | Vysoký | Quick-add UX (Alt+D), timer, minimální friction |
| Management ignoruje reporty | Nízká | Střední | Compelling templates, $ framing, ROI focus |
| Konkurent (SonarQube/CodeScene) přidá $ valuaci | Nízká | Vysoký | First-mover advantage, IDE-native moat |
| JetBrains absorbuje funkcionalitu | Velmi nízká | Vysoký | Budovat brand, enterprise features |

---

## 8. Milestones

| Milestone | Datum | Deliverable |
|-----------|-------|-------------|
| M1: MVP | 2026-02 | Free tier (inventory, timer, hotspots) |
| M2: Pro Launch | 2026-03 | $ valuation, ROI, burndown, export |
| M3: Enterprise Beta | 2026-04 | Team aggregation, Jira integration |
| M4: 1.0 Release | 2026-05 | Full feature set, polished UX |
| M5: Growth | 2026-Q3 | 10K+ downloads, marketing push |

---

## 9. Success Criteria

1. ✅ 15K+ downloads za 12 měsíců
2. ✅ 5%+ Free→Pro konverze
3. ✅ $8K+ MRR za 12 měsíců
4. ✅ 4.5+ hvězdiček na JetBrains Marketplace
5. ✅ Alespoň 3 case studies od reálných týmů
6. ✅ < 5% monthly churn
