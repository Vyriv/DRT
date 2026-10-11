# Luck Rating Stage C: live capture

Live capture for future runs, from dungeon completion to Stage B scoring input. Pure Java under `dev.vy.drt.luck.capture`; the client hooks in `src/client/java` only feed screens and chat into it.

## Pipeline

1. **Completion.** `LuckTrackingStore.addRun` stores the floor, grade and numeric score from the completion chat, the party from the tab list, a copy of the modifier profile active at that moment, and the newest meter reading for the floor taken after the floor's previous captured run (`BEFORE_RUN`).
2. **Croesus menu.** `CroesusRunMatcher` links menu entries to stored runs by floor, roster and completion-age window. Ambiguous entries are not linked; entries that match no run go to `unmatchedCroesus`. The link records the listed modifiers (Kismet Feather, Dungeon Chest Key) and which chests were opened.
3. **Croesus run view.** Every chest offer is stored with raw lore and resolved content lines. Identical contents are not stored twice. Different contents for a chest already seen are appended and flagged, and only count as a Kismet reroll when marked as one.
4. **RNG Meter.** A reading is attached as `AFTER_RUN` to the floor's latest run before it, if that run has none. An unchanged reading with no run in between only moves its last-seen time.
5. **Coverage.** `LuckCoverage.evaluate` (version 3) is recomputed on every change and on every load, so a stale stored verdict is never trusted.
6. **Conversion.** `LuckScoringInput.history` turns stored runs into `ObservedRun`s, re-evaluating coverage itself. Runs it cannot score come back as rejected with their blockers, runs with a Kismet reroll as excluded with their evidence, and gaps the engine can widen over as caveats.
7. **Evaluation.** `LuckHistoryEvaluation.evaluate` builds configurations with `HistoryConfigurations` (per-run modifiers, meter scenarios from `MeterScenarioPlanner`) and runs `LuckEvaluator`. The result keeps everything the Luck Rating screen needs; see below.

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

Conversion adds `CROESUS_MODIFIERS_UNKNOWN`, `PURCHASES_UNKNOWN` and `METER_SELECTION_NOT_SELECTABLE` per run. At history level it adds `METER_START_UNKNOWN:<floor>` when a floor's first scored run has no reading and `METER_GAP:<run>` when a rejected or excluded run on the same floor sits before a run without a reading.

## Kismet exclusion

A run whose only blockers are Kismet ones, or whose coverage shows a reroll with its original offer, is excluded rather than rejected: `KISMET_REROLL_CONFIRMED` for a recorded reroll, `KISMET_REROLL_SUSPECTED` for contents that changed without a recorded reroll or a reroll whose original was not seen. Each carries per-chest evidence and stays in the store unchanged, so it can be scored once the real reroll behaviour is verified and a matching simulation policy exists. Until then evaluation uses the never-reroll policy and no excluded run is simulated. Excluded runs break meter continuity like rejected ones. Players usually reroll poor offers, so leaving rerolled runs out can make the rest look luckier; the result says so whenever a run is excluded.

## Modifier snapshots

Every scored run keeps the snapshot recorded with it. `LuckEvaluator.Configuration` carries per-run modifiers and the simulator, statistic, null distribution and rare-outcome gate use one simulator per distinct snapshot, while the meter carries across runs. Unknown fields widen only the runs recorded with them: one alternative per extreme of each snapshot, plus one with every snapshot at its highest unknowns when several are uncertain. Known values are never replaced by unknowns. The no-Pity rule leaves the alternatives only when every snapshot records a nonzero Pity level.

## Meter scenarios

`MeterScenarioPlanner` replays each floor's meter exactly between readings, using each run's score and modifiers for the gain and the observed appearances or purchases for resets. Selections are assumed unchanged between readings. Where the chain breaks, it opens a window:

| Window | When | Candidates |
|---|---|---|
| `UNKNOWN_START` | The floor's first scored run has no reading before it | Derived exactly from the next reading when the selected drop did not reset in between. Otherwise every fill (0, 50, 100%) of the next reading's selection; with no later reading at all, nothing selected plus every selectable drop at each fill |
| `GAP` | A rejected or excluded run sits before a run without a reading | As above, but with the selection known from before the gap when no later reading says otherwise |
| `UNEXPLAINED_GAIN` | A reading shows more progress than the captured runs since the last reading explain, which points to uncaptured runs | The missing progress after the runs (baseline) or before them |

A reading after a floor's last run counts as an anchor too. Every candidate must reproduce each later reading exactly and must not imply a full-meter guarantee whose drop never appeared. The baseline scenario takes each window's weakest candidate. Alternatives raise every window's fill together, and try each other selection of an unknown-selection window at its weakest and strongest fill. Each configuration is planned with its own rules and modifiers. An alternative that contradicts the readings is dropped with its reason. If the baseline contradicts them (a reading below what the captured runs gave, an unseen guarantee, or no start reproducing a reading), the score is withheld with the contradiction. It is also withheld when more than 64 configurations would be needed, naming the floors whose RNG Meter should be read. The simulator's empty-meter default is never relied on: every floor's first run is set by a reading or an assumption in every configuration.

## Evaluation result

`LuckHistoryEvaluation` keeps:

- the counts of recorded, scorable, rejected and excluded runs;
- every scored run with its caveats, every rejected run with all its blockers, and every excluded run with its Kismet evidence;
- tallies of rejection reasons and run caveats, and history caveats, including the possible bias from runs left out;
- the number of distinct modifier snapshots, the baseline meter windows and notes, the alternatives dropped for contradicting readings, and why a score was withheld;
- the engine's evaluation, when it ran.

A run is scorable when its own record is complete. A history is statistically sufficient only when its scorable runs expect at least one rare drop. Status is `NO_RECORDED_RUNS`, `NO_SCORABLE_RUNS`, `WITHHELD` (the meter data contradicts the runs or leaves too many scenarios), `INSUFFICIENT_DATA`, `PROVISIONAL` or `UNRESOLVED`.

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

Regression tests: `LuckScoringInputTest`, `LuckCoverageTest`, `LuckTrackingStoreTest` (runs between readings, older schema), `LiveCaptureF5Test`, `MeterScenarioPlannerTest` and `HistoryConfigurationsTest`.

## Remaining limitations

- **Kismet.** No real reroll has been captured. Confirmed and suspected rerolls are excluded from the score until the real behaviour is verified and simulated. A reroll used before DRT first saw the chest looks like a normal offer and cannot be detected.
- **Opened-chest formats.** How an opened chest looks in the Croesus run view has not been observed. If its contents are not shown, the run is blocked as `OFFERS_PARTIAL`. Chests opened at the end of the dungeon are only known through Croesus "Opened Chest:" lines; their contents are not captured from the in-dungeon chest screen.
- **Master Mode.** The run view title has only been seen for Normal floors. A title without a Master Mode marker leaves the mode unknown and relies on the matched menu entry. No Master Mode run has been captured end to end.
- **Mechanic variants.** S-run meter gain is still provisional (round first or one combined multiplier) until an S score of 285 or 299 is read. M7 chest eligibility, guarantee placement, meter reset trigger, overflow, Kismet and meter, and the rounding variants are still unconfirmed and stay in sensitivity analysis.
- **Data gaps.** Dyes and other known gaps are not modelled. Runs played without DRT running are only noticed when a later reading shows unexplained progress; a derived start assumes no such run happened inside its window.
- **Meter assumptions.** Selections are assumed unchanged between readings; a change is only noted when the next reading shows a different selection. Fills between the 0, 50 and 100% candidates are not tried. Resets under the on-purchase rule rely on recorded purchases, which can be unknown.
- **Runs left out.** Rejected and excluded runs are not simulated. If whether a run was captured or rerolled depends on its loot, the remaining history is biased; this is reported, not corrected.

## What remains before Stage D

1. Decide whether runs left out (rejected or Kismet-excluded) should only be reported, as now, or should withhold the score above some share of the history.
2. Capture more live runs: an S score of 285 or 299, a real Kismet reroll, an opened chest in the run view, and a Master Mode run.
