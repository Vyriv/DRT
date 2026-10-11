# Dungeon Luck Rating: Stage A audit

Status: research and audit only. No simulator or UI code has been written yet.
Date: 2026-10-11. Applies to DRT 1.0.11 (Minecraft 26.1.2 and 26.2).

## 1. What DRT records today

| Area | Status | Notes |
|---|---|---|
| Run completion | Supported | `DungeonRunCompletionRecord` per completed run: floor, grade letter, completion time, run time. Requires the score line plus boss time or EXTRA STATS. |
| Floor | Supported | `DungeonFloor` F1-F7, M1-M7 (plus Kuudra K1-K5). |
| Numeric score | Not recorded | `SCORE_GRADE_PATTERN` keeps only the letter (S+, S, A...). The number (for example 301) is discarded. |
| Failed or abandoned runs | Not recorded | `RunSession.abandon()` exists in memory only. Deaths, fails and early leaves are never saved. |
| Opened chests | Supported | One `DungeonRunRecord` per opened chest: tier title, loot (`rawName`, `itemId`, `quantity`), base cost, modifier costs. |
| Chest offers (contents of chests seen but not opened) | Not persisted | Parsed into `cachedChestOffersByTitle` for the overlay, held in memory, keyed by tier title, cleared on every new run. |
| Kismet Feather | Partial | `usedKismetFeather` flag and cost. Pre-reroll contents are overwritten and lost. |
| Dungeon Chest Key | Supported | `usedDungeonChestKey` flag and cost. |
| RNG Meter | Not recorded | The only reference filters "RNG METER" chat lines out of loot parsing. |
| Boss Luck, Treasure accessory, Catacombs Box, Pity, Echo, Magic Find | Not present | No code or config. |
| Persistence | Supported, unversioned | Everything is in `config/drt.json` (Gson of `DrtConfig`), atomic write with `.bak`. No schema version; migrations are ad-hoc normalisation at load. `runHistory` is uncapped. |
| Tests | Supported | JUnit 5 per Stonecutter version (`.\gradlew.bat :26.2:test`). `dev.vy.drt.price`, `dev.vy.drt.tracking` and most of `dev.vy.drt.config` are pure Java. |
| Existing loot tables | Names only | `ExpectedLootTables` (guaranteed essence for F1-F7 only, no Master Mode values), `ManualLootSuggestions` (drop name lists). No weights or probabilities. |

### Identity and duplicate protection

- `DungeonRunRecord` has `recordId` (random UUID), `runSessionId`, `chestSessionId`, `commitFingerprint` (name-based UUID over the record content), `runNumber`, `chestNumber`, `timestampEpochMillis`.
- `recordId` and `commitFingerprint` are stable once saved, so a backfill can key imports on them and be safely re-run.
- Session ids are `live-<8 hex>-run-N`, regenerated on every launch. They are unique enough within one install, not globally.
- `timestampEpochMillis` is the loot-window start, not the chest open time. Every chest opened from one Croesus visit shares it.
- No player or profile identity is stored.

### Measured on the local install (Prism 26.1.2 instance, read-only)

- 141 completions (M5 S+ 131, M5 S 4, F6 S+ 5, UNKNOWN 1) and 144 chest records, 2026-08-18 to 2026-10-11.
- M5 chests opened: Wood 45, Bedrock 36, Obsidian 32, Emerald 8, Gold 4, Diamond 4. Kismet used once, Chest Key 3 times.
- Only 4 of 130 M5 chest records share a `runSessionId` with a completion. Linking by timestamp is ambiguous: one Croesus visit put up to 13 chests within 60 seconds of a single completion.
- 19 records are tagged K3/K4 but titled with Catacombs chest tiers, which shows floor attribution errors exist in the history.

### What cannot be recovered for past data

- Numeric score, so past RNG Meter score gains are unknown.
- RNG Meter selection, stored score and resets at any point.
- Contents of chests that were seen but not opened, including pre-Kismet contents.
- Failed and abandoned runs.
- A reliable chest-to-run link. Past data can only be grouped by floor, not by individual run.
- The modifier setup (Boss Luck, accessory, attributes) in effect at the time.

## 2. Research findings

Authority order used: official patch notes or staff posts > in-game data copied by players > community wiki > community calculators.

### Loot data source

Only one complete, machine-readable source exists for all 14 floors:
Pigicial `sb-calculators` (Apache-2.0), `assets/dungeon_loot/{f1..f7,m1..m7}/{chest}.json`, repo HEAD `6938191` (2026-09-11), loot data last changed 2026-08-07.
Each file has base quality, base cost and every entry's quality cost, weight and added chest cost, plus guaranteed essence.

The community wiki loot pages are generated from the same data, so they are not an independent source. FluxCapacitor2's calculator is a 2024 scrape of old wiki percentages with no weights or quality values.

The only independent check is the in-game RNG Meter screen, copied by players to Fandom (`RNG Meter/UI/Catacombs`) and NotEnoughUpdates-REPO (`constants/rngscore.json`). Required score is `300 x totalWeight / itemWeight` of the highest chest, so it confirms the top chest's total weight and item weights.

| Floor | Top-chest weight check (JSON / Fandom / NEU) | Verdict |
|---|---|---|
| F1 | 106 / 106 / 106 | Single source, top chest confirmed |
| F2 | 63 / 63 / 63 | Single source, Emerald base cost conflict (200k vs 250k) |
| F3 | 87 / 87 / 87 | Single source, top chest confirmed |
| F4 | 83 / 83 / 83 | Single source, top chest confirmed |
| F5 | 381 / 381 / 381 | Single source, top chest confirmed |
| F6 | 2667 / 2667 / 2667 | Single source, top chest confirmed |
| F7 | 14056 / 14056 / 13706 | Single source, top chest confirmed. NEU predates the Wither Shard (350). F7 Obsidian Wither Shard listed as guaranteed in JSON, absent on wiki: unresolved |
| M1 | 86 / 86 / 86 | Single source, top chest confirmed |
| M2 | 62 / 62 / 62 | Single source, Emerald base cost conflict |
| M3 | 79 / 79 / 79 | Single source, top chest confirmed |
| M4 | 293 / 292 / 296 | Partial. Spirit Mask weight likely 4, JSON has 1 |
| M5 | 325 / 326 / 326 | Single source, one weight unit missing from JSON |
| M6 | 2708 / 2708 / 2708 | Single source, top chest confirmed |
| M7 | 13893 / ~13893 / ~13893 | Single source, top chest confirmed. Gold chest essence looks copied from F7. Necron Dye handling disputed |

No floor has a second source for quality costs, lower-chest weights or essence amounts.

### Mechanics

| Rule | Value | Sources | Status |
|---|---|---|---|
| Chest availability | Wood/Gold/Diamond on boss kill, Emerald 230+, Obsidian 270+, Bedrock 300+ on F5-F7 and M5-M7 | archived official wiki, community wiki | Verified. Normal F7 unlock wording conflicts |
| Base quality per floor and chest | Table in community wiki and archived official wiki | 2 sources | Verified |
| Base chest cost | Wood free; Gold 25k/50k/100k; Diamond 50k/100k/250k; Emerald 100k/250k/500k; Obsidian 250k/500k/1M; Bedrock 2M | 2 sources | Verified except F2/M2 Emerald |
| Roll order | Weighted picks among entries with quality <= remaining budget, without replacement except repeatable essence; leftover budget becomes filler essence; then guaranteed entries | archived official wiki, community wiki, forum, Pigicial code | Verified. Server tie-breaking unknown |
| S+ quality | `floor(base x 1.05)`; S gives no bonus | community wiki, Pigicial; official table headers round half up | Conflict on rounding |
| Treasure accessory | x1.01 / x1.02 / x1.03, highest tier only | 2+ sources | Verified |
| Boss Luck | +1 / +3 / +5 / +10 (levels I-IV, no V) | NEU, wikis, Pigicial | Verified |
| Catacombs Box | Hunting attribute, +1 quality per level, 1-10 | NEU, wiki, 0.23 patch notes | Verified |
| Echo of Boxes / Echo of Echoes | +2% per level to Box (max +20%); +5% per level to Echo attributes (max +50%). Max Box 10 / 12 / 13 | wiki, forum, NEU | Verified maximums. Rounding of fractional values unknown |
| Final quality | `q1 = round(initial x T + B + C)`, `q = round(q1 x T + B + C)` (multiplier and flat bonuses applied twice, a known Hypixel bug) | archived official wiki, community wiki, Pigicial | Verified. Half-rounding mode unknown |
| Magic Find | No source says it affects reward chests | absence only | Unverified. Not modelled |
| RNG Meter | 14 separate meters. S+ adds 100% of score, S adds 70%. Score accumulates with no selection and carries over on target change. Weight multiplier `1 + min(2 x stored / required, 2)`. Full meter guarantees the item next eligible run. Selected item appearing resets the meter (fixed in 0.18.4). Dyes fixed at 1,000,000 | 0.14 patch notes, archived official wiki, NEU | Verified |
| RNG guarantee placement | Lowest chest tier containing the item | Pigicial only | Single source |
| Reset on appearance vs purchase | Appearance | forum plus Pigicial | Single source |
| Overflow on guaranteed drop | Required score subtracted, else 0 | community wiki | Single source |
| S-run meter rounding | 70% of score, truncation in Pigicial | Pigicial | Unknown server behaviour |
| Pity | Attribute, meter fills +1% to +10% faster | community wiki | Single source |
| Kismet Feather | Rerolls one unopened chest per run with the same chest table and score | archived official wiki, Fandom | Verified. Whether the RNG Meter applies to the reroll: conflicting |
| Dungeon Chest Key | One extra chest per run, chest cost still paid | archived official wiki, Fandom | Verified |
| Multiple priced items in one chest | Sum or max of added cost | none | Unknown |
| Music discs | 1.6% of essence vs 2% in Obsidian/Bedrock | conflicting | Conflict |
| Mayor effects | Paul Marauder (-20% chest cost), EZPZ (+10 score); Aura Work Better (doubled meter, Nov 2025-Jan 2026) | community wiki | Not relevant to the local history window, must be flagged if present |

## 3. Statistical problems the design must handle

1. **Selection bias.** Chest contents are visible before purchase, both at the end of a run and in Croesus. The opened chest is chosen because of what it holds, so its contents cannot be compared with an unconditional simulation of that chest. The comparison unit must be the run: simulate every chest offered in the run, then apply an explicit purchase policy (for example "buy the chest holding the rarest item, a second one if a Chest Key was used").
2. **Purchased is not offered.** Past data only shows what was bought. A rare item that appeared and was skipped is invisible. For high-value rare items the purchase policy assumption is reasonable; it must be stated, not hidden.
3. **No run linkage in past data.** Past records can only support per-floor totals: number of eligible completions per floor and grade, and rare items obtained on that floor.
4. **Unknown past meter state.** Historical luck must either exclude meter-targetable items, or report results under explicit meter scenarios (none selected, selected item X) and show the range.
5. **Unknown modifiers in the past.** The configured preset becomes an explicit assumption on backfilled data.

## 4. Proposed architecture

All probability code lives in a new pure-Java package `dev.vy.drt.luck` under `src/main/java`, with no Minecraft imports, so it is testable with plain JUnit.

- `luck.data`
  - Bundled resource `assets/drt/luck/catacombs-loot-v1.json`: a converted snapshot of the Pigicial data at a pinned commit, with attribution (Apache-2.0 NOTICE).
  - A separate overrides file for corrections (for example M4 Spirit Mask), each with source URL, authority and date.
  - Per floor: `status` = VERIFIED / SINGLE_SOURCE / PARTIAL / INCOMPLETE, and a ruleset version string. Floors marked INCOMPLETE produce no score.
- `luck.model`: `FloorId`, `ChestTier`, `LootEntry` (item, quality, weight, added cost, kind: weighted / repeatable / filler / guaranteed), `ChestTable`, `FloorTable`, `ModifierPreset` (every field nullable for "unknown"), `RunContext` (floor, grade, score if known, Kismet, Chest Key).
- `luck.engine`
  - `QualityCalculator`: S+ multiplier, double-applied accessory and flat bonuses, with the rounding mode as an explicit parameter.
  - `ChestRoller`: the quality-budget roll with an injected `SplittableRandom`, so tests use fixed seeds.
  - `RunSimulator`: rolls every eligible chest for a run, applies Kismet and the purchase policy.
  - `RngMeter`: stateful per-floor meter (selected item, stored score, required score, Pity, reset and overflow rules). Updates from simulated outcomes, not fixed percentages.
  - `Assumptions`: every unresolved rule (guarantee placement, Kismet and meter, rounding modes, disc rate) as a named switch with a default. Scores are re-run under the alternatives, and the UI shows a warning when the score moves by more than a set tolerance.
- `luck.stats`
  - Rare outcome set per floor, chosen from the loot table (per-run appearance probability below a threshold), never from prices.
  - Statistic: total surprisal of rare items obtained, `sum of -ln(p_item)`, where `p_item` is the per-run probability of obtaining it under the purchase policy.
  - Null distribution: Monte Carlo of the player's exact history (same runs per floor and grade, same Kismet and key usage, meter evolving inside each simulated history). Deterministic seeds; replication count chosen by a convergence check on the percentile.
  - Score: 100 x mid-p percentile of the observed statistic. Under the null the score is uniform, so the average is 50 and more runs do not raise it.
  - Small samples: if the expected number of rare outcomes is below 1, show counts only and "not enough data", no score. Otherwise show the score with a confidence band from the discreteness of the distribution.
  - Floors are combined by summing the per-floor statistic and simulating the combined null, so each floor keeps its own distribution.
- `luck.history`
  - `LuckBackfill`: reads `DrtConfig` records without modifying them. Writes to a new file `config/drt-luck.json` with a schema version. Imports are keyed on `recordId` and `commitFingerprint`, so it is safe to run repeatedly.
  - Summary: processed, eligible, partial and excluded records, with the exclusion reason.
- Client side (Stages C and E): a Luck Rating screen and a modifier settings submenu opened from the existing settings screen, following DESIGN.md. New live capture for future runs: numeric score, all chest offers seen per run (including pre-Kismet contents), and RNG Meter state from the `/rngmeter` screen and meter chat. A modifier snapshot is stored with each new run.

## 5. Decisions needed before Stage B

1. Accept the Pigicial data as the base table (single source, top chest confirmed for most floors), with corrections in a documented overrides file? The alternative is to wait for more data, which leaves every floor unscored.
2. Accept run-level comparison with an explicit purchase policy as the way to handle selection bias?
3. For backfilled history, exclude meter-targetable items or show meter scenarios as a range?
4. Default choices for the unresolved rules (S+ rounding, guarantee placement, Kismet and meter), with sensitivity warnings in the UI?
