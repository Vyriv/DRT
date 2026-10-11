# Luck Rating Stage C: live capture

Live capture for future runs, from dungeon completion to Stage B scoring input. Pure Java under `dev.vy.drt.luck.capture`; the client hooks in `src/client/java` only feed screens and chat into it.

## Pipeline

1. **Completion.** `LuckTrackingStore.addRun` stores the floor, grade and numeric score from the completion chat, the party from the tab list, a copy of the modifier profile active at that moment, and the newest meter reading for the floor taken after the floor's previous captured run (`BEFORE_RUN`).
2. **Croesus menu.** `CroesusRunMatcher` links menu entries to stored runs by floor, roster and completion-age window. Ambiguous entries are not linked; entries that match no run go to `unmatchedCroesus`. The link records the listed modifiers (Kismet Feather, Dungeon Chest Key) and which chests were opened.
3. **Croesus run view.** Every chest offer is stored with raw lore and resolved content lines. Identical contents are not stored twice. Different contents for a chest already seen are appended and flagged, and only count as a Kismet reroll when marked as one.
4. **RNG Meter.** A reading is attached as `AFTER_RUN` to the floor's latest run before it, if that run has none. An unchanged reading with no run in between only moves its last-seen time.
5. **Coverage.** `LuckCoverage.evaluate` (version 3) is recomputed on every change and on every load, so a stale stored verdict is never trusted.
6. **Conversion.** `LuckScoringInput.history` turns stored runs into `ObservedRun`s for `LuckEvaluator`, re-evaluating coverage itself. Runs it cannot score come back as rejected with their blockers; gaps the engine can widen over come back as caveats.

## Coverage

Blockers stop a run from being scored. Caveats are passed on.

| Blocker | Meaning |
|---|---|
| `FLOOR_UNKNOWN`, `GRADE_UNKNOWN` | Completion could not be read |
| `NUMERIC_SCORE_MISSING` | Grade only: eligibility is known, but meter gain needs the exact score |
| `OFFERS_NOT_OBSERVED`, `OFFERS_PARTIAL` | Not every eligible chest's offer was seen |
| `UNEXPECTED_CHEST` | A chest the grade does not allow, or an unnamed chest, was offered or opened: the grade, score or eligibility rule is wrong |
| `OFFER_CONTENT_UNRESOLVED` | An offer with no lines, or a line without an item key; it could be a rare drop |
| `OFFER_CHANGED_UNVERIFIED` | Contents changed without being marked as a Kismet reroll |
| `KISMET_ORIGINAL_OFFER_MISSING` | Only the rerolled offer was seen |

| Caveat | Meaning |
|---|---|
| `KISMET_PROVISIONAL` | Every run with offers: Kismet handling is unverified |
| `MODIFIERS_PARTIALLY_UNKNOWN`, `MODIFIERS_UNKNOWN` | Unknown fields are evaluated at their extremes |
| `METER_UNKNOWN`, `METER_DERIVED` | No exact before-run reading |
| `METER_SELECTION_UNRESOLVED` | A selected drop DRT could not name; the reading is not treated as exact |

Conversion adds `CROESUS_MODIFIERS_UNKNOWN`, `PURCHASES_UNKNOWN`, `METER_SELECTION_NOT_SELECTABLE` and `KISMET_REROLLED:<tier>` per run. At history level it adds `METER_START_UNKNOWN:<floor>` when a floor's first scored run has no reading, `METER_GAP:<run>` when a rejected run on the same floor sits before a run without a reading, `MODIFIER_SNAPSHOTS_DIFFER:<fields>` when the runs' snapshots disagree (those fields become unknown), and `KISMET_REROLLED:<run>`.

## Meter attachment

With two runs between readings X and Y on a floor, the first run gets X as its before-run state, the second gets none, Y becomes the second run's after-run state and the first run gets no after-run state. Neither run yields a meter gain observation, so readings that span several runs never feed the rule evidence. Runs on other floors do not affect the attachment.

## Persistence

`drt-luck.json`, schema 2. Older and unversioned files are migrated on load, keeping runs, offers, meter snapshots and each run's own modifier snapshot. A newer schema makes the store read-only and leaves the file untouched. A malformed file is preserved under a timestamped name before a fresh one is started. The coverage version can change without a schema change, because coverage is recomputed on load.

## Audit (before Stage D)

The live F5 S 298 capture goes from the stored file through `LuckScoringInput` into `LuckEvaluator` with no manual reconstruction. Its floor, score, Kismet and Chest Key availability, all seven rolled items, the empty purchase set, the before-run meter (3,556, nothing selected) and the fully known Pity 10 snapshot are taken from stored fields. Alone it is withheld as `INSUFFICIENT_DATA`; 150 copies pass the rare-outcome gate and get a band under all 13 configurations. The S+ run is rejected with `OFFERS_NOT_OBSERVED`.

Gaps found and fixed:

- Nothing converted stored runs into engine input. `LuckScoringInput` now does.
- Grade-only runs qualified for scoring even though meter gain needs the exact score.
- A chest the grade does not allow was silently ignored by the rarity tables, and an unnamed chest's contents were skipped.
- Offers with no lines or with unresolved lines qualified, which would bias a run towards unlucky.
- A meter reading with an unresolved selected drop counted as an exact state.
- Essence totals would have been reported to the engine as unmodelled items. Conversion leaves them out, since they sum repeated picks and filler.

Regression tests: `LuckScoringInputTest`, `LuckCoverageTest`, `LuckTrackingStoreTest` (runs between readings, older schema) and `LiveCaptureF5Test`.

## Remaining limitations

- **Kismet.** No real reroll has been captured. A reroll is only recognised when the offer is marked as one; any other change blocks the run. A reroll used before DRT first saw the chest looks like a normal offer and cannot be detected. A captured reroll counts both offers as appeared, as the simulator does, but the default configurations use a never-reroll policy, so those runs carry `KISMET_REROLLED` until Stage D evaluates them with a matching policy.
- **Opened-chest formats.** How an opened chest looks in the Croesus run view has not been observed. If its contents are not shown, the run is blocked as `OFFERS_PARTIAL`. Chests opened at the end of the dungeon are only known through Croesus "Opened Chest:" lines; their contents are not captured from the in-dungeon chest screen.
- **Master Mode.** The run view title has only been seen for Normal floors. A title without a Master Mode marker leaves the mode unknown and relies on the matched menu entry. No Master Mode run has been captured end to end.
- **Mechanic variants.** S-run meter gain is still provisional (round first or one combined multiplier) until an S score of 285 or 299 is read. M7 chest eligibility, guarantee placement, meter reset trigger, overflow, Kismet and meter, and the rounding variants are still unconfirmed and stay in sensitivity analysis.
- **Data gaps.** Dyes and other known gaps are not modelled. Runs played without DRT running, or rejected runs, break meter continuity; this is named as `METER_GAP` or shows up as conflicting meter evidence.
- **Engine inputs.** `LuckEvaluator` takes one modifier preset per history, so differing snapshots are merged to unknown rather than applied per run. `HistorySimulator` starts a floor with no reading at an empty meter, which is why `METER_START_UNKNOWN` must become meter scenarios. Selection changes between readings are not seen.

## What remains before Stage D

1. Build meter scenarios from `METER_START_UNKNOWN` and `METER_GAP` caveats (for example with `MeterAssumptions`), instead of letting the simulator default to an empty meter.
2. Pass the converted history's caveats and rejected runs through to the evaluation result, so the screen can say which runs were left out and why.
3. Decide how to evaluate runs with `KISMET_REROLLED`: a Kismet policy that rerolls the recorded chest, or leaving those runs out.
4. Capture more live runs: an S score of 285 or 299, a real Kismet reroll, an opened chest in the run view, and a Master Mode run.
