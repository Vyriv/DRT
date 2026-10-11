# Luck Rating Stage B: simulation engine

Pure Java under `dev.vy.drt.luck` (no Minecraft imports). Tests: `.\gradlew.bat :26.2:test --tests "dev.vy.drt.luck.*"`.

## Layout

| Package | Contents |
|---|---|
| `luck` | `FloorId`, `ChestTier`, `ScoreTier` |
| `luck.data` | `CatacombsLootDatabase` (hash-verified base data plus overrides), `ChestTable`, `FloorTable`, `LootEntry`, `DataDispute`, `KnownGap`, `FloorStatus` |
| `luck.rules` | `RuleVariants` (13 named rule choices), `ModifierPreset` (nullable = unknown), `ResolvedModifiers`, `QualityCalculator`, `SensitivityAnalysis` |
| `luck.engine` | `ChestRoller` (Monte Carlo), `ExactChestDistribution` (exact DP), `MeterBoost`, `ChestRoll` |
| `luck.meter` | `RngMeterModel`, `RngMeterState`, `MeterAssumptions` (plausible starts for unknown meters) |
| `luck.sim` | `RunSimulator`, `HistorySimulator` (one meter per floor), `SelectionPolicy`, `KismetPolicy`, `RunOutcome`, `RunRarity`, `AppearanceEstimator`, `ChestEligibility` |
| `luck.stats` | `SurprisalStatistic`, `NullDistribution`, `MidPPercentile`, `RareOutcomeGate`, `ObservedRun`, `LuckEvaluator` |

## Evaluation flow

`LuckEvaluator` takes observed runs and a set of plausible configurations (modifier extremes for unknown fields, every one-at-a-time rule variant, every meter scenario, the buying and Kismet policies).

1. Any run without complete chest offers: `INCOMPLETE_RECORDS`. Incomplete runs are never silently dropped, because which runs got recorded may itself depend on luck.
2. Fewer than 1 expected rare drop (per-run probability 1% or less): `INSUFFICIENT_DATA`.
3. Otherwise each configuration gets its own observed statistic and null distribution (common random numbers across configurations). If every percentile, widened by 2 Monte Carlo standard errors, lands in one band (very unlucky under 5%, unlucky under 25%, typical, lucky above 75%, very lucky above 95%), the verdict is `PROVISIONAL`; otherwise `UNRESOLVED`.

Reasons always list non-verified floors, known gaps on the floors played and items outside the loot tables.

## Buying policy and selection bias

The appearance statistic and its null do not depend on which chests are bought, unless Kismet is used (rerolls add opportunities) or the meter resets on purchase. Tests pin both: identical null samples for different buying policies without Kismet, and different nulls once either applies. Purchased-reward statistics always depend on the policy.

Data: `assets/drt/luck/catacombs-data-v1.json` (manifest, provenance, per-file SHA-256, floor status), `pigicial-6938191/` (vendored, unmodified), `catacombs-overrides-v1.json` (disputes with evidence and selectable options, plus known gaps).

## Data disputes

| Id | Default | Alternative | Affects rolls |
|---|---|---|---|
| `M4_SPIRIT_MASK_WEIGHT` | weight 4 (all 22 in-game meter scores fit) | weight 1 (base data) | Yes |
| `M5_M7_METER_TOTAL_WEIGHT` | meter total +1 (in-game copies) | base total | Meter only |
| `F7_OBSIDIAN_WITHER_SHARD` | guaranteed (base data) | absent (wiki) | No |
| `F2_M2_EMERALD_BASE_COST` | 200k (base data) | 250k (wiki tables) | Cost only |

Known gaps (not modelled, cannot be scored): Livid and Necron Dyes, music discs, M7 Gold essence amounts, M7 dragon shard chest list.

## Rule variants

Defaults first. One-at-a-time alternatives come from `RuleVariants.oneAtATimeAlternatives()`.

| Rule | Default | Alternative |
|---|---|---|
| S+ quality rounding | floor | half up (official table headers) |
| Quality rounding | half up | half even |
| Modifier application | twice (known bug) | once |
| Box bonus after Echoes | exact | floor |
| Guarantee placement | lowest tier containing | highest eligible tier |
| Meter reset trigger | on appearance | on purchase |
| Meter overflow | subtract required | reset to zero |
| S run gain (70%) | truncate | round |
| Pity | multiply gain, truncate | not applied |
| Kismet and meter | applies | ignored |
| Required score rounding | floor | half up |
| Selection carry-over | cap at required | uncapped |
| M7 eligibility | all chests on completion | score gated |

## Validation

- Exact DP matches every weighted row of the community wiki average-chance tables (all 76 chests, both qualities) within 2e-6. The tables come from the same generator, so this validates the implementation, not the data.
- Monte Carlo roller matches the exact DP for all 76 chests (20,000 rolls each, 5.5 sigma), with and without meter boost and forced guarantee.
- Quality calculator reproduces all 152 wiki table headers.
- Required scores match the NEU in-game meter copy within 1 point on every floor (F7 after excluding the Wither Shard, which the copy predates) and the Fandom copy within its printed precision.
- Run simulator: eligibility, Chest Key, Kismet as an extra opportunity (matches 1 - (1 - p)^2), purchases always a subset of appearances, highest-tier purchase rate equals top-chest appearance.
- Meter: gap between appearances never exceeds the fill time; meter carries between runs.
- Percentile: ties count half, Monte Carlo error reported, mid-p mean 0.5 under the null with the meter active, rare-outcome gate withholds scores with under 1 expected rare appearance.
- Evaluator: implausibly lucky and empty histories give very lucky and very unlucky under all 14 configurations; incomplete and short histories are refused; band stability accounts for simulation error.
- Mixed-floor histories keep an independent meter per floor.

## Sensitivity

No modifiers: no alternative rule changes the rarest selectable item's per-run odds on any floor.
Maxed modifiers (Boss Luck IV, Artifact, Box 13): modifier application (twice vs once) moves rarest-item odds by 44% on M6, 34% on M5 and 16 to 18% on F1, F2, M1 and M2. S+ rounding moves F7 by 45%. Scores for players with large quality bonuses must pass sensitivity before being shown as provisional.

## Findings during Stage B

- Bedrock "no bonus" wiki headers include S+ on F5-F7, M5 and M6, but not M7.
- M4 Obsidian weights are all scaled by 4, including essence.
- Neither in-game meter copy lists the F7 Wither Shard as a selectable item, though its weight is in the total.
