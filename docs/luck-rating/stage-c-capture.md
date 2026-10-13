# Luck Rating Stage C: live capture

Live capture for future runs, from dungeon completion to Stage B scoring input. Pure Java under `dev.vy.drt.luck.capture`; the client hooks in `src/client/java` only feed screens and chat into it.

## Pipeline

1. **Completion.** `LuckTrackingStore.addRun` stores the floor, grade and numeric score from the completion chat, the party from the tab list, a copy of the modifier profile active at that moment, and the newest meter reading for the floor taken after the floor's previous captured run (`BEFORE_RUN`).
2. **In-dungeon reward chests.** A reward chest preview seen in the instance of the run DRT just completed is stored as that run's offer (source `DUNGEON_CHEST`), once its contents read the same on two scans after the screen settled. A chest opened there is marked purchased with the same source. A run view seen there is stored under that run without a Croesus entry. "The instance" means the same world the completion happened in, outside the Dungeon Hub, within 10 minutes; the warp to the Dungeon Hub replaces the world, so a Croesus view is never linked this way. Nothing is clicked for the player.
3. **Croesus menu.** `CroesusRunMatcher` links menu entries to stored runs by floor, roster, completion-age window and, for chests DRT saw opened in the dungeon, the entry's opened chests. Under the default strict policy a group of look-alike entries is linked only when the evidence leaves a single pairing (`UNIQUE`, or `ELIMINATION` for larger groups); otherwise it stays `AMBIGUOUS`. The menu's list order is never used to pair entries until it is verified in game (`Policy.LIST_ORDER`, off). Entries that match no run go to `unmatchedCroesus`. The link records the listed modifiers (Kismet Feather, Dungeon Chest Key) and which chests were opened.
4. **Croesus run view.** The view is linked through the entry clicked just before it. The entry is read from the page at the moment of the click, so a click made before the page counted as stable still carries its entry and the same strict match; a click on anything but a run entry of that page arms nothing. The run view is also read at any click inside it, so a chest clicked before the view counted as stable still stores the offers and links the opened chest. Master Mode views are titled `Master Catacombs - Floor V`. A view with no readable chest buttons yet (still loading, or a chest preview sharing the title) keeps the clicked entry for its next read. Without a clicked entry the view is kept as `NO_CLICKED_ENTRY`, never given to the most recent run. Every chest offer is stored with raw lore and resolved content lines. Identical contents are not stored twice, including the same chest seen first in the dungeon and later in Croesus when every line resolved to the same keys. Different contents for a chest already seen are appended and flagged, and only count as a Kismet reroll when marked as one.
5. **RNG Meter.** A reading is attached as `AFTER_RUN` to the floor's latest run before it, if that run has none. An unchanged reading with no run in between only moves its last-seen time.
6. **Coverage.** `LuckCoverage.evaluate` (version 3) is recomputed on every change and on every load, so a stale stored verdict is never trusted.
7. **Conversion.** `LuckScoringInput.history` turns stored runs into `ObservedRun`s, re-evaluating coverage itself. Runs it cannot score come back as rejected with their blockers, runs with a Kismet reroll as excluded with their evidence, and gaps the engine can widen over as caveats.
8. **Evaluation.** `LuckHistoryEvaluation.evaluate` applies the missing-data policy (below), builds configurations for the rated floors with `HistoryConfigurations` (per-run modifiers, meter scenarios from `MeterScenarioPlanner`), runs `LuckEvaluator` and bounds the runs left out. The result keeps everything the Luck Rating screen needs; see below.

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

A run whose only blockers are Kismet ones, or whose coverage shows a reroll with its original offer, is excluded rather than rejected: `KISMET_REROLL_CONFIRMED` for a recorded reroll, `KISMET_REROLL_SUSPECTED` for contents that changed without a recorded reroll or a reroll whose original was not seen. Each carries per-chest evidence and stays in the store unchanged, so it can be scored once the real reroll behaviour is verified and a matching simulation policy exists. Until then evaluation uses the never-reroll policy and no excluded run is simulated. Excluded runs break meter continuity like rejected ones. Players usually reroll poor offers, so leaving rerolled runs out can make the rest look luckier. An excluded run on a rated floor therefore withholds the headline (see the missing-data policy).

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

## Missing-data policy

Coverage is a gate, not proof. Leaving runs out is only harmless if which runs are left out has nothing to do with their loot, and DRT cannot know that: a player may skip viewing a run in Croesus after seeing its chests in the dungeon, reroll poor offers, or meet an item DRT cannot resolve. In `MissingRunSelectionBiasTest`, histories with no luck at all keep an honest median percentile when 10% of runs are lost at random, but fall to a mean percentile below 0.2 when the lost 10% are the luckiest runs. The coverage is the same in both cases.

1. **Per-floor gate.** A floor is rated only when at least 90% of its recorded runs are scorable (`FloorCoverage.meetsGate`, integer arithmetic). Floors below the gate are left out of the headline as a whole, with their coverage and every run kept for display. A well-covered floor never carries a poorly covered one. Runs without a readable floor cannot be counted against any floor and withhold the headline.
2. **Kismet.** Any confirmed or suspected reroll on a rated floor withholds the headline until rerolls are simulated. Reroll exclusion only covers rated floors.
3. **Unrecorded runs.** Meter progress that the recorded runs do not explain (an `UNEXPLAINED_GAIN` window or note) means DRT missed runs whose number and loot are unknown, so the headline is withheld.
4. **Rejected runs on rated floors.** `LuckEvaluator.missingRunBounds` asks whether their possible outcomes could change the verdict. A run whose every eligible chest was seen and that was only blocked by a missing numeric score counts exactly. Otherwise the run's seen items are a lower limit, and its unknown outcome is bounded by the worst selection a capture pattern could make: on each floor, the missing runs being the luckiest (or unluckiest) of all that floor's runs, at the 95th percentile of what that selection gives with no luck. The whole history, scored plus missing, is placed against a null that simulates the missing runs too. If either limit, with simulation error, reaches a band the verdict does not already allow, the headline is withheld. A missing run with no grade or score, or with an unexpected chest, is simulated as S+ and its seen items set no lower limit.

The bounds use the baseline configuration only and simulate missing runs without meter boost. The meter gaps those runs cause are already covered by meter scenarios.

This policy is deliberately strict. With 300 null samples, single-floor histories with no luck were withheld this often by the missing-run check alone:

| Runs on floor | 1 unseen | 2 unseen | 5 unseen | 5% unseen | 10% unseen |
|---|---|---|---|---|---|
| 60 (F7 S+) | 19 of 30 | 25 of 30 | 30 of 30 | 30 of 30 | 30 of 30 |
| 150 (F7 S+) | 13 of 30 | 20 of 30 | 30 of 30 | 30 of 30 | 30 of 30 |
| 400 (F7 S+) | 13 of 30 | 14 of 30 | 26 of 30 | 30 of 30 | 30 of 30 |
| 400 (M7 S+) | 8 of 30 | 15 of 30 | 28 of 30 | 30 of 30 | 30 of 30 |

The bands are narrow (Lucky and Unlucky span 20 percentile points), the bound must stay inside the verdict's band including two standard errors of simulation, and one exceptional run can move a percentile several points. In practice a rated floor needs close to every run captured, and the 90% gate mostly matters for which floors can be rated at all. More null samples narrow the error margin but not the selection bound.

The strict bound stays as it is. It is not loosened to produce more ratings.

**Estimate for a withheld history.** `LuckHistoryEvaluation.Estimate` is a separate, labelled range, never a headline. It comes from the same worst-selection bound, so it assumes nothing about why runs are missing:

- Withheld only because rejected runs could change the verdict: the range spans every band the bound and the plausible configurations reach.
- Withheld only because no floor passes the gate: every floor with scorable runs is scored, gate or not, with the same pre-simulation checks, and its rejected runs are bounded the same way.
- Any other cause gives no estimate, with the reason kept in `estimateUnavailable`. Kismet rerolls are not ordinary rolls, unrecorded runs have an unknown count, runs without a floor cannot be placed, and contradictions or too many configurations leave nothing to bound.
- Too few expected rare drops on the estimate's floors also gives no estimate.

A range from Very unlucky to Very lucky is reported as uninformative (`informative()` is false) rather than as an estimate. The estimate is only computed for withheld histories, so a scored history never carries one.

**Looser random-loss bound (option B).** This bound is acceptable only where the capture mechanism proves that whether a run was captured is independent of its loot. No current mechanism does. "No chest opened" is not enough, because players see chest previews in the dungeon and Croesus. The one case that is loot-independent, a run blocked only by an unread numeric score with every chest seen, already counts exactly. So no run currently qualifies for a looser bound.

## Evaluation result

`LuckHistoryEvaluation` keeps:

- the counts of recorded, scorable, rejected and excluded runs, and coverage per floor with whether it is rated;
- every scored run with its caveats, every rejected run with all its blockers, and every excluded run with its Kismet evidence, on rated and unrated floors alike;
- tallies of rejection reasons and run caveats, and history caveats, including `FLOOR_NOT_RATED` and the possible bias from runs left out;
- the expected rare drops of the rated floors and whether that is statistically sufficient, reported even when the headline is withheld;
- the number of distinct modifier snapshots, the baseline meter windows and notes, the alternatives dropped for contradicting readings;
- every `WithholdCause` with a readable reason;
- the engine's evaluation when it ran, and the missing-run bounds when they were checked. A withheld result can keep its evaluation for diagnostics; `headlineBand()` is only set when the status is `PROVISIONAL`;
- for a withheld history, the labelled estimate or the reason there is none;
- progress helpers: `FloorCoverage.capturedRunsToGate()`, the fully captured runs in a row a floor still needs, and `runsUntilSufficient()`, roughly how many more rated runs reach one expected rare drop.

A run is scorable when its own record is complete. A history is statistically sufficient only when the rated floors' scorable runs expect at least one rare drop. Status is one of:

- `NO_RECORDED_RUNS`, `NO_SCORABLE_RUNS`;
- `WITHHELD`, with causes `NO_ELIGIBLE_FLOOR`, `UNATTRIBUTED_RUNS`, `KISMET_REROLLS`, `UNRECORDED_RUNS`, `METER_CONTRADICTION`, `TOO_MANY_CONFIGURATIONS`, `MISSING_RUNS_COULD_CHANGE_VERDICT` or `INCOMPLETE_RECORDS`;
- `INSUFFICIENT_DATA`: rated, but too few rare drops expected;
- `PROVISIONAL`: one band under every configuration and every plausible outcome of the runs left out;
- `UNRESOLVED`: configurations disagree, and the runs left out stay within the bands they span.

Checks that need no simulation (coverage, Kismet, meter consistency, unrecorded runs) run first. A withheld history is never scored.

## RNG Meter setup

A player opens the Catacombs RNG Meters overview once. That single screen gives the exact stored score and selected drop for F1 to F7 and M1 to M7, and each reading becomes the before-run state of that floor's next recorded run, however much later. From then on the meter is replayed from the recorded runs. Requirements the overview abbreviates come from the loot database. A floor's own RNG Meter screen, which the player must open to change the selected drop, is read whenever it is opened, so selection changes made while DRT runs are caught. Readings are never required after setup. Without them, rejected runs open `GAP` windows, and runs played without DRT cannot be detected.

## Collecting offers at scale

Players visit Croesus every 20 to 60 runs. What DRT can see without automating anything:

| Source | What it shows | Use |
|---|---|---|
| Croesus menu (paged list) | Floor, party, completion age, expiry, opened chests, Kismet and Chest Key availability. No contents. | Linking entries to runs |
| Croesus run view (one click per run) | Every chest's full contents and cost on one screen | Complete offers for that run |
| In-dungeon reward chest screens | One chest's contents per screen, for every chest the player looks at | Offers for the run just completed; opened chests are evidence for Croesus matching |
| `RARE REWARD!` chat | Rare items from opened chests | Purchases only, not appearance |

Croesus counts chest expiry down from 72 hours after the run, so runs left longer than that cannot be captured at all. The menu alone never shows contents, so DRT cannot fill in offers it did not see. Clicking through Croesus automatically would be unsafe gameplay automation and is ruled out.

Feasible, all player-driven:

1. **Optional guided Croesus pass.** Never a required workflow; DRT stays passive. While the Croesus menu is open, mark each entry DRT has linked: captured, needs viewing, or not linked. Show a count such as "14 runs still need their chests viewed" and keep it updated as the player opens and closes run views. One click and one back per run, with capture as soon as the view is stable, makes 40 runs a minute or two of clicking.
2. **Match across pages.** The matcher treats each page alone and refuses blocks that could continue onto another page (`PAGE_BOUNDARY`). Combining the pages seen in one visit would link those entries too.
3. **Content fingerprints.** Chests opened at the end of the dungeon already narrow Croesus candidates by tier (an entry must list them as opened). Comparing their contents with the run view would separate runs that opened the same tiers.
4. **Expiry reminder.** A passive notice when recorded runs approach their 72-hour expiry unviewed.
5. **Later, chest-level scoring.** Scoring only chests the player chose to open is biased if opening a second chest depends on the first one's contents, so it would need its own policy and is not part of Stage D.

## Loot history links and run times

Loot history (`/drt loot`) and the luck store share runs through the DRT run session id. A chest record is tied to a completed run only on proof, decided when it is opened and stored on the record (`runLinkSource`, `linkedRunId`):

| Link | When |
|---|---|
| `LIVE_INSTANCE` | Opened in the instance of the run DRT completed there, within 10 minutes |
| `CROESUS_UNIQUE` | Opened from a Croesus run view whose entry the evidence linked to one run |
| `UNLINKED` | Anything else |

A `CROESUS_UNIQUE` chest is also marked purchased in the luck store (source `CROESUS_CHEST`), but only after its "CHEST REWARDS" chat arrived and the record was saved; viewing an offer never marks it. A purchase already proven by the Croesus page or in the instance is not overwritten.

Opened chest contents come from a scan of the opened chest and from the indented lines under the "CHEST REWARDS" header, merged by item. The opened chest is scanned on its first frame when it follows that tier's preview, because it can close, or its chat can arrive, within the 500 ms settle delay used for other reward screens. Chat lines only count while they follow the header without a blank or unindented line in between, so other chat is never read as loot.

Older records carry no link. `RunHistoryLinks` judges them at display time without changing them: a chest stamped with its run's own completion time, floor and session, in a group of at most two chests of different tiers, counts as opened in the dungeon. Croesus opens that older versions filed under the latest run fail these checks and stay unlinked. Any run with more than two linked chests, or the same tier twice, has all of them unlinked. Loot, cost and profit are never changed by any of this, and totals still count only purchased chests.

Completion records store where their time came from (`runTimeSource`): the boss time from chat (`OFFICIAL_CHAT`), DRT's own clock (`MEASURED`) or a manual entry. Older records are judged by their fingerprint, which ends in the chat time or 0. A linked chest shows its run's time; anything else shows "Time unavailable".

The loot screen shows one tab per chest the run offered when there is more than one: purchased chests with their logged loot and a green mark, chests seen but not bought with their observed contents marked "Not Opened", and chests never seen as "Not recorded". Every observed chest shows Opening Cost, Loot Value and Profit; for an unopened chest these are display only and never enter history totals, filters or statistics. Up and Down move through the chest list, Left and Right through the tabs.

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

Regression tests: `LuckScoringInputTest`, `LuckCoverageTest`, `LuckTrackingStoreTest` (runs between readings, older schema), `LiveCaptureF5Test`, `MeterScenarioPlannerTest`, `HistoryConfigurationsTest`, `LuckMissingDataPolicyTest` (coverage gate and thresholds, per-floor coverage, Kismet, unrecorded runs, rejected runs that could change the band), `MissingRunSelectionBiasTest` (loot-dependent against random loss, bound coverage) and `LuckCaptureCoordinatorTest` (one setup overview, click-time entries, in-instance run views, delayed views, consecutive runs). `RewardChatBlockTest` covers the chat reward lines.

## Remaining limitations

- **Kismet.** No real reroll has been captured. Confirmed and suspected rerolls are excluded from the score until the real behaviour is verified and simulated. A reroll used before DRT first saw the chest looks like a normal offer and cannot be detected.
- **Opened-chest formats.** How an opened chest looks in the Croesus run view has not been observed. If its contents are not shown, the run is blocked as `OFFERS_PARTIAL`. In-dungeon previews are now captured, but their item wording has only been checked against logged loot, not against a live preview; a line that resolves to no key blocks the run as `OFFER_CONTENT_UNRESOLVED` rather than being guessed.
- **Master Mode.** The run view title has only been seen for Normal floors. A title without a Master Mode marker leaves the mode unknown and relies on the matched menu entry. No Master Mode run has been captured end to end.
- **Mechanic variants.** S-run meter gain is still provisional (round first or one combined multiplier) until an S score of 285 or 299 is read. M7 chest eligibility, guarantee placement, meter reset trigger, overflow, Kismet and meter, and the rounding variants are still unconfirmed and stay in sensitivity analysis.
- **Data gaps.** Dyes and other known gaps are not modelled. Runs played without DRT running are only noticed when a later reading shows unexplained progress, which withholds the headline; without a later reading they go unnoticed, and a derived start assumes no such run happened inside its window.
- **Meter assumptions.** Selections are assumed unchanged between readings; a change is only noted when the next reading shows a different selection. Fills between the 0, 50 and 100% candidates are not tried. Resets under the on-purchase rule rely on recorded purchases, which can be unknown.
- **Runs left out.** Rejected runs are bounded, not simulated as observed. The bound assumes the worst selection, not the worst possible loot, so a capture pattern more extreme than "the missing runs were the luckiest ones" is not covered. A gap's meter uses the fill grid even when the missing run's score is known; carrying its exact gain with a reset or no reset would be tighter.
- **Strictness.** The missing-run check withholds most histories with more than a few unseen runs on a rated floor (see the table above). This is kept on purpose. Incomplete histories get a labelled estimate where the bound allows one, and no capture mechanism yet qualifies for a looser bound.

## Luck Rating screen (Stage D)

`/drt luck` opens `DrtLuckRatingScreen`, which is read-only. `LuckRatingService` evaluates deep copies of the stored runs on a low-priority background pool, with 1000 null samples per configuration, and configurations are scored in parallel. The result is cached against `LuckTrackingStore.revision()`, so it is only recalculated after something new is captured or the modifiers change.

- **Verdict block.** The status is labelled distinctly: Provisional, Unresolved, Withheld, Not enough data, Not rated or No runs. Provisional shows its band and percentile. Unresolved shows the band range. Withheld shows its first cause and the labelled estimate, marked "Estimate, incomplete history" and "Not verified", or why there is none. Not enough data shows a progress bar towards one expected rare drop and roughly how many more captured runs it needs.
- **Setup strip.** It shows whether the RNG Meter overview has been read and how many modifiers are unknown, with an Edit button for the preset. Nothing else is asked of the player.
- **Floors tab.** For each floor: captured out of recorded, whether it is rated, and how many fully captured runs it still needs.
- **Runs tab.** Every recorded run, newest first, as Scored, Captured (floor not rated), Not captured with its reasons, Held out with Kismet evidence, or New since the last calculation.
- **Details tab.** Every withheld cause with its explanation and raw reason, the estimate's floors, counts and percentile range, data counts, caveats and model notes.

Visual verification happens in Prism. Optional Croesus guidance comes after the screen and stays optional.

## What remains

1. Capture more live runs: an S score of 285 or 299, a real Kismet reroll, an opened chest in the run view, and a Master Mode run.
2. Optional Croesus guidance and cross-page matching, since a defensible rating needs close to every run on a floor captured.
