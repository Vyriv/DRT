# DRT Full Catch-Up / Agent Handoff

Last updated: 2026-07-08, early morning, Europe/London.

This file is intentionally large. It is meant to let another agent continue the DRT work without needing the full chat transcript.

The user is Ryan. Project is DRT, a Minecraft/Hypixel SkyBlock dungeon run tracker mod. The working directory during this session was:

`C:\Users\Ryan\Desktop\IntelliJ\Mini-Mods\DRT`

Shell was PowerShell. The active workspace is a real local repo, not a disposable example. Do not revert user changes. This branch is dirty and contains multiple large implementation passes.

## Critical User Preferences

Ryan is actively testing in Prism Launcher. He wants direct, pragmatic work, not long theory unless he explicitly asks for talking-stage planning.

Important preferences and constraints from the chat:

- Do not make huge, bloated UIs.
- Avoid heavily rounded, padded, card-like UI unless already established.
- For the dungeon chest overlay, no large background panel.
- The chest overlay should sit close to the right side of the chest UI, similar density to another mod shown on the left side.
- Overlay text must render above/darker inventory background so it is readable.
- Chest overlay should eventually be clickable; some clickability has already been added.
- Icon support matters. Use item/chest/key/feather style icons where the loot log already does.
- Color coding should match the loot logger:
  - normal enchant books like Overload are blue
  - ultimate enchants are bold pink
  - essence is pink
  - quantities like `x41` should be grey
  - profit is green if positive, red if negative, regardless of number formatting
- Chest headers should be bold. Bold text had a blurry/broken rendering bug once, so do not "fix" it by removing bold. Fix the cause.
- For onboarding, Ryan explicitly said Codex has a history of making huge, ugly UIs. Keep it compact, aligned, and usable.
- Onboarding dropdown arrows should be real triangle icons, `▲` and `▼`, not `V` / `^`.
- Dropdown options should highlight selected rows. They should not use `[x]` or `[ ]`.
- If a dropdown opens, the whole onboarding menu should not move.
- Buttons should align on a clean right boundary.
- `/drt` with no args should say exactly: `[DRT] Invalid syntax, use /drt <config/loot/move/toggle>`
- When replacing a jar in Prism, kill the Minecraft instance first, wait 2 seconds, then copy. Copying over a loaded jar can break the instance.
- The Prism log Ryan cares about is the Prism profile log, not `.minecraft` generally.
- Ryan's active Prism profile is `Skyblock 26.1.2(1)`.
- If Ryan says "do not code" or "talking stage", do not implement.

## Prism / Jar Handling

Active Prism instance path used in this session:

`C:\Users\Ryan\AppData\Roaming\PrismLauncher\instances\Skyblock 26.1.2(1)\minecraft`

Runtime mod jar path:

`C:\Users\Ryan\AppData\Roaming\PrismLauncher\instances\Skyblock 26.1.2(1)\minecraft\mods\DungeonRunTracker-1.0.2+26.1.2.jar`

Built jar paths:

- `C:\Users\Ryan\Desktop\IntelliJ\Mini-Mods\DRT\versions\26.1.2\build\libs\DungeonRunTracker-1.0.2+26.1.2.jar`
- `C:\Users\Ryan\Desktop\IntelliJ\Mini-Mods\DRT\versions\1.21.11\build\libs\DungeonRunTracker-1.0.2+1.21.11.jar`

Dist jar paths:

- `C:\Users\Ryan\Desktop\IntelliJ\Mini-Mods\DRT\dist\DungeonRunTracker-1.0.2+26.1.2.jar`
- `C:\Users\Ryan\Desktop\IntelliJ\Mini-Mods\DRT\dist\DungeonRunTracker-1.0.2+1.21.11.jar`

Last known successful build:

```powershell
.\gradlew.bat build
```

Last known successful dist refresh after the kismet/global reroll fix:

- `dist\DungeonRunTracker-1.0.2+26.1.2.jar`
  - timestamp around `2026-07-08 02:17:09`
  - length around `274730`
- `dist\DungeonRunTracker-1.0.2+1.21.11.jar`
  - timestamp around `2026-07-08 02:17:10`
  - length around `280751`

The final build/dist refresh at that point did not copy into Prism. Ryan specifically asked not to copy into Prism while he was testing.

Use this pattern only when Ryan asks to replace the Prism jar:

```powershell
$instance = "Skyblock 26.1.2(1)"
$procs = Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^(java|javaw)\.exe$' -and $_.CommandLine -like "*$instance*"
}
foreach ($proc in $procs) {
    Stop-Process -Id $proc.ProcessId -Force
}
Start-Sleep -Seconds 2

$src = "C:\Users\Ryan\Desktop\IntelliJ\Mini-Mods\DRT\versions\26.1.2\build\libs\DungeonRunTracker-1.0.2+26.1.2.jar"
$dist = "C:\Users\Ryan\Desktop\IntelliJ\Mini-Mods\DRT\dist\DungeonRunTracker-1.0.2+26.1.2.jar"
$dst = "$env:APPDATA\PrismLauncher\instances\Skyblock 26.1.2(1)\minecraft\mods\DungeonRunTracker-1.0.2+26.1.2.jar"

Copy-Item -LiteralPath $src -Destination $dist -Force
Copy-Item -LiteralPath $src -Destination $dst -Force
Get-Item -LiteralPath $dst | Format-List FullName,LastWriteTime,Length
"KilledProcesses=$($procs.Count)"
```

Do not copy the jar while Minecraft is loaded.

## Original TBA / Todo List Status

This section maps the original TBA to what has been added, what has been changed, and what still needs work.

### 1. Loot Logger: Dungeon Chest Keys, Kismet Feathers, Wheel of Fate

Original item:

`Add support in loot logger for dungeon chest keys, kismet feathers and wheel of fate.`

Status: mostly implemented, still needs real in-game verification for kismet edge cases and Wheel of Fate.

Added:

- Dungeon chest key support.
- Kismet feather support.
- Wheel of Fate cost support.
- Chest cost breakdown model.
- Cost fields stored on loot log records.
- Loot log icons/cost lines for modifiers.
- Flush logging includes cost fields.

Changed from the original vague plan:

- Kismet detection was changed more than once. It now treats `You already rerolled a chest` as global reward-menu state and arms the next opened chest rather than marking every chest offer as kismeted.
- Dungeon chest key detection was moved toward concrete lore/button detection, because chat-message-only detection was unreliable and Ryan confirmed there is no dungeon chest key chat message.
- Cost overlay became more elaborate and interactive than the original text mockup.

Remaining:

- Test kismet reroll then do not open: should not create a loot-log entry.
- Test kismet reroll then open: should show Kismet in overlay and loot log.
- Test kismet plus dungeon chest key: should show both.
- Test Wheel of Fate in-game.
- If kismet still fails, inspect actual latest.log and actual chest/reroll lore. The current known signal is global lore `You already rerolled a chest`.

#### 1.1 Dungeon Chest Key Separate Tab / Key Icon

Original item:

`Dungeon chest key will add a separate new tab for another chest opened, with a little key icon next to the name so <ChestIcon> XXX Chest <KeyIcon>`

Status: implemented enough that Ryan later said "Key worked."

Important findings:

- Ryan initially said it did not acknowledge the dungeon chest key.
- He clarified there is no dungeon chest key chat message.
- Detection needed to come from concrete lore/button state.
- Ryan provided screenshot context:
  - if a chest needs a key to be opened, the open reward chest button lore/text says that.
- This concrete lore detection is preferred over the earlier pending-message-style detection.

Remaining:

- Keep testing key-only and key-plus-kismet paths.
- Make sure the loot log record clearly appears as a separate opened chest with key indicator.

#### 1.2 Kismet Feather Icon

Original item:

`Kismet feather reroll will add a kismet feather icon next to the chest name so <ChestIcon> XXX Chest <KismetFeather>`

Status: implemented, but the last kismet fix still needs fresh test.

Important findings:

- Ryan rerolled a chest and said the numbers made no sense.
- One case showed profit around `-5m` and a barrier icon next to the key.
- Ryan said it was a kismet plus key chest.
- In an M5 example, original open cost was around `5.6m` because the chest had a Livid Dagger, then reroll changed open cost.
- Kismet state is not necessarily attached per chest in the way the first parser assumed.
- The reward menu can show `You already rerolled a chest` on all chests after reroll.

Current intended behavior:

- Seeing global reroll state arms the next opened chest as kismeted.
- Rerolling without opening should not write a record.
- Opening after reroll should consume the kismet pending state and attach kismet cost/icon to that chest.

Remaining:

- Test this in-game.
- If still wrong, grab exact chat line and exact lore from the reroll feather/button plus the opened chest button.

#### 1.3 Kismet Rerolled Chest Not Opened

Original item:

`If the kismet rerolled chest is NOT opened then it will !! TBD !!`

Decision made:

- Implement it as "arm the next opened chest only."
- A reroll by itself should not log a chest.
- If the player rerolls and then leaves/does not open, no loot-log entry should be created.
- If the player rerolls and opens a chest, the opened chest gets Kismet cost/icon.

Status: implemented in latest patch, needs real test.

Why this behavior was chosen:

- Ryan asked what to do.
- We chose the simple/realistic behavior: only count Kismet if a chest is actually opened and logged.
- Avoid fake loot log records for unclaimed rerolled chests.

Current likely implementation points:

- `rewardMenuKismetRerollPending`
- `armNextOpenedKismetReroll()`
- `assignPendingOpenedChest`
- `nextOpenedChestUsesKismetFeather`
- `lastRewardModifierScanHadKismetMarker`

Remaining:

- Test reroll plus no open.
- Test reroll plus open.
- Test reroll plus key plus open.

#### 1.4 Chest Overlay Cost Breakdown

Original mockup:

```text
Chest type
XX Items Value
XX Items Value
XX Items Value
Value: xxx

Key: -x
Kismet: -x (iff applicable)
Chest key: -x (if applicable)
Wheel of fate: -x (if applicable)
Profit:
```

Status: implemented and heavily refined.

Final-ish requested layout:

```text
Chest

Item #1-4
Essence 1
Essence 2

Value

Open Cost modifiers (Kismet, chest key)

Profit
```

Important UI decisions:

- The overlay goes on the right side of the reward chest, not left.
- It should be close to the chest UI, not far out.
- It should have no big background.
- It should be dense/close together like the comparison mod on the left side.
- It must render above the chest/inventory background so it is not dark.
- Header should be bold.
- Profit green/red based only on sign.
- Opened chests should show `Opened` in pink if chest lore says already opened.
- Tooltips should ideally render above the overlay, but current state may still need polish.
- Chest key modifier line and key icon are clickable and run `/bz dungeon chest key`.
- Tooltip for that click target should say `Click to buy a dungeon chest key`.

Naming behavior:

- `Enchanted Book (Overload I)` should display as `Overload 1`.
- Similar book names should be simplified in overlay and loot log style.

Remaining:

- Verify tooltip layering over overlay.
- Verify exact color rendering and bold header in live game.
- Verify all modifier rows only appear when applicable.

### 2. Configuration Onboarding Screen

Original item:

`Add a configuration onboarding screen on first launch to configure basic settings and advanced settings for kuudra etc`

Status: implemented and iterated.

Added:

- First-launch onboarding screen.
- Command(s) to reopen it.
- Skip button bottom-left.
- Overlay position button.
- Kuudra pet toggle.
- Pet rarity selector.
- Pet level selector.
- Kuudra faction dropdown.
- Force salvage dropdown.
- Cool Forged toggle/level.
- Bazaar pricing mode dropdown.
- Compact layout pass.

Changed during iteration:

- Mythic rarity was removed because Kuudra pet does not have mythic.
- Pet rarity/level now only show when pet is enabled.
- Salvage and essence no longer have separate headings. Ryan said salvage is part of Kuudra, and essence is too.
- Faction became dropdown.
- Force salvage became dropdown.
- Dropdown arrow changed to `▲` / `▼`.
- Dropdown selected options are highlighted instead of `[x]` checkbox style.
- Dropdowns should not move the entire onboarding menu when opened.
- Panel width was reduced because the original had dead space.
- Buttons were aligned to a cleaner right boundary.

#### 2.1 Button to Pick Overlay Position

Status: added.

Likely connected to existing move/position screen:

- `DungeonTrackerPositionScreen.java`
- `/drt move` or onboarding button likely opens this.

Remaining:

- Only polish if user finds UX issue.

#### 2.2 Kuudra Pet Toggle With Rarity and Level

Status: added.

Important correction:

- No mythic option for Kuudra pet.
- Rarity/level should only appear when pet toggle is enabled.
- It should expand/shrink cleanly.

Remaining:

- Test config persistence after toggling.

#### 2.3 Force Salvage for Armor, Wands, Equipment Separately

Status: added as dropdown behavior.

Original asked for separate armor/wands/equipment settings. Current UI apparently represents force salvage through dropdowns. If continuing, verify if the data model actually preserves all three separate categories or if UI collapsed them too much.

Remaining:

- Confirm the final UI/data matches Ryan's intended separate behavior.

#### 2.4 Cool Forged Per Level Essence Bonus

Original item:

`Add option to enable cool forged per with each level increasing essence when salvaging (1/2/3/4/5 increasing by 4/8/12/16/20 respectively)`

Status: added.

Remaining:

- Test salvage/essence calculations in real Kuudra context.

#### 2.5 Command to Re-run Onboarding / Edit Later

Status: added.

Known commands:

- `/drt config`
- `/drt setup`
- `/drt onboarding`

Also `/drt` alone should show invalid syntax:

`[DRT] Invalid syntax, use /drt <config/loot/move/toggle>`

Remaining:

- Verify commands in-game.

#### 2.6 Add Option to Change Bazaar Instant / Order Pricing

Original item was later added to TBA:

`Add option to change bz insta or buy order`

Then changed after Tomato's feedback.

Final chosen UI:

- `Instant`
- `Order`

Tomato's reasoning:

- It should switch between Instant and Order so it also works for buying things like keys and kismets.
- Mods often say instant buy/sell in a way that means one side uses order price and the other instant price.
- Better model:
  - `Instant`: selling uses instant sell, buying costs use instant buy
  - `Order`: selling uses sell offer, buying costs use buy order

Status: implemented.

Config migration:

- Old `INSTANT_SELL` and `INSTANT_BUY` map to `INSTANT`.
- Old `SELL_OFFER` and `BUY_ORDER` map to `ORDER`.

Remaining:

- Test values in overlay switch correctly when changing mode.
- Test key/kismet costs switch buy side as well as loot value sell side.

#### 2.7 Reopen/Edit Persists Settings

Status: added.

Remaining:

- Verify settings persist between first setup, reopen, edit, restart.

### 3. Best Chest to Open / Croesus Overlay

Original item:

`Add an option to show best chest to open`

Status: implemented and expanded.

Added:

- Croesus reward menu overlay listing every chest from Bedrock to Wood, regardless of profit.
- Profit/loss color:
  - profit green
  - loss red
- Chest color/icon:
  - icon and text should use chest color/style.
- `Open` recommendation section:
  - line 1 is best chest normally
  - line 2 only appears if buying/using a dungeon chest key is profitable
- Hovering overlay lines highlights the associated chest slot.
- Highlight colors:
  - best normal chest is green
  - second/key chest is yellow
- Highlight should render above any other highlighting.
- Clicking a line opens/views that chest.
- Hover tooltip says `Click to view xxx chest`.

Final display Ryan wanted:

```text
Bedrock +5.8m
Obsidian +1.2m
Emerald -918k
Diamond -512k
Gold +215k
Wood +100k

Open
1. CHESTICON Chest +5.8m
2. KEYICON CHESTICON Chest +927k
```

Important clarification:

- The list is sorted by chest type, not by profit:
  - Bedrock
  - Obsidian
  - Emerald
  - Diamond
  - Gold
  - Wood

Known screenshot context:

- Ryan provided Croesus screenshots.
- One screenshot showed a chest menu with chest icons and runs.
- Another showed the Croesus chest menu/reward chests.
- Ryan wanted the display where the overlay sits, in the Croesus menu, on all chests.

Known bugs found:

- On 26.1.2 chest highlighting worked.
- On 1.21.11 chest highlighting was misaligned.
- Fix added an accessor mixin for real container positions.
- In the main `Croesus` menu with all runs, Ryan wanted unopened chests highlighted in green.
- First attempt highlighted everything, including already opened chests, because lore contains `Opened Chest: xxxx`.

Current expected main menu unopened detection:

- Only highlight if lore explicitly indicates unopened/unclaimed/openable/not opened/available reward states.
- Do not highlight simply because chest type appears in `Opened Chest: xxxx`.

Remaining:

- Test 1.21.11 alignment after accessor fix.
- Test 26.1.2 still correct.
- Test main Croesus menu only highlights runs with actual unopened/unclaimed chests.
- If main menu still highlights too much, inspect exact run item lore.

#### 3.1 Two Chests If Dungeon Chest Key Is Worth It + Button to Run `/bz dungeon chest`

Original item:

`Including option to display two chests if one is valued high enough for dungeon chest key to be worth it and a button to run command /bz dungeon chest`

Status: partially/mostly implemented, with command detail changed.

Implemented:

- Show second best chest only if dungeon chest key makes it worth it.
- Key recommendation line includes key icon.
- Chest key modifier line/icon in the reward overlay is clickable.
- Click runs `/bz dungeon chest key`.
- Tooltip says `Click to buy a dungeon chest key`.

Open detail:

- Original text said `/bz dungeon chest`, later request specifically said `/bz dungeon chest key`.
- Current implementation should use `/bz dungeon chest key`.

Remaining:

- Verify clickable key line/icon in live game.
- Confirm command is exactly what Ryan wants long-term.

### 4. TAP/TWAP

Original item:

`Add support for tap/twap affecting overall profit or profit per runs`

Status: implemented earlier in the chat.

Known from prior handoff:

- It was completed before the later kismet/Croesus/onboarding bug passes.
- Ryan asked what testing would look like for it.

Remaining:

- Real-world verification.
- Check whether it should affect overall profit, profit per run, or both exactly as Ryan expects.

### Fix: Loot Log Numbering

Original item:

`Fix loot log numbering on all chests showing multiple of same number due to multiple floors being logged`

Status: implemented.

Known implementation:

- Uses `nextChestLogNumber` rather than reusing floor/run numbering.

Remaining:

- Test multiple floors/chests logged in sequence.

### Fix: Logs After 500 Being Trimmed

Original item:

`Fix logs after 500 being trimmed`

Status: implemented.

Known behavior:

- Loot log no longer trims at 500 records.

Remaining:

- Check config file growth/performance eventually, but not urgent.

### Fix: Kuudra Key Cost and Faction

Original item:

`Fix kuudra key cost not being counted for in kuudra chests + add option in onboarding to select faction`

Status: implemented enough in config/UI, needs validation.

Added:

- Kuudra faction dropdown in onboarding.
- Kuudra key cost field in cost breakdown/log model.

Remaining:

- Real Kuudra test.
- Verify faction affects key cost correctly.
- Verify cost appears in profit and saved records.

## Chronological Work Done In This Chat

This is a high-level timeline so a continuing agent understands why the code evolved the way it did.

1. Ryan pasted the DRT todo list.
2. He asked to do item 1.
3. Item 1 was implemented: loot logger support for dungeon keys, kismet, wheel, and related data models.
4. Ryan asked to implement item 2.
5. Onboarding screen was implemented.
6. Ryan asked if it needed testing; guidance was given.
7. Ryan asked to do item 3.
8. Best chest / Croesus overlay work began.
9. Ryan asked if item 3 needed testing; guidance was given.
10. Ryan asked to implement item 4.
11. TAP/TWAP support was implemented.
12. Ryan asked what testing looked like.
13. Ryan pasted repeated startup warnings: `[DRT] skullFromTexture failed: Components not bound yet`.
14. Startup warning/cache behavior was investigated/fixed.
15. Ryan said dungeon chest key was not acknowledged.
16. Ryan clarified there is no dungeon chest key message.
17. Detection moved away from relying on a chat message and toward menu/button/lore signals.
18. Ryan showed/startup crash logs with the same skull warning spam and suspected no new jar was built.
19. Fresh jar handling was corrected.
20. Ryan later said "Key worked."
21. Ryan tested kismet, thought it did not work, and described weird costs/barrier icon.
22. Ryan clarified Prism log location and active profile `26.1.2`.
23. Ryan described an M5 chest where original cost was 5.6m due to Livid Dagger and reroll changed open cost.
24. Ryan asked what to do; the kismet behavior was adjusted.
25. Ryan said it worked and asked to check `drt.json` for two M3 chests. That was checked earlier in the chat.
26. Ryan asked what next implementation pass was.
27. Chest overlay was identified as the next pass.
28. Ryan specified the overlay should be on the right side, with icon support and color coding.
29. Initial overlay was too large/backgrounded, Ryan told us to dial it back.
30. Overlay was changed to no background, closer/tighter, right side.
31. Ryan said overlay was still dark/not above chest, item names bad, colors needed, header bold.
32. Overlay name/color/header rendering was changed.
33. Header became mushed/unreadable; adjusted.
34. Ryan asked what prices were used.
35. Ryan asked why manual essence config was used when Athen provides essence, and asked instant sell vs sell order.
36. Pricing/essence use was adjusted/clarified.
37. Ryan said instant sell might be better because selling books instantly gives that value.
38. Later Tomato suggested better Instant/Order mode covering both sell and buy sides.
39. Ryan requested layout with blank lines after header/value/open cost.
40. Bold bank/chest type text became blurry/broken; fixed by investigating rendering cause and re-adding bold.
41. Profit sign color forced green/red.
42. Ryan asked how open cost detects key chest.
43. Ryan provided screenshot/lore for kismet reroll state.
44. Ryan provided screenshot/lore for key-needed open reward chest button.
45. Ryan asked if concrete lore detection was better for loot logger too.
46. Concrete detection was added.
47. Ryan asked next pass.
48. Discussion moved to Croesus best chest display.
49. Ryan said no coding, talking stage.
50. Ryan described desired Croesus overlay list and `Open` recommendations.
51. Ryan asked to check Prism latest.log when game did not open; then said never mind.
52. Ryan provided Croesus screenshots.
53. Ryan wanted bedrock-to-wood order, red losses, green profits, chest color/icon.
54. Ryan wanted best two chests highlighted:
    - hover chest line highlights chest
    - hover best/open line highlights normal best in green
    - hover second/key line highlights in yellow
    - render above other highlighting
55. Ryan wanted clicking a line to open that chest and tooltip text.
56. Feature worked mostly; issues:
    - tooltip/lore rendered below overlay
    - already opened chests should show `Opened` in pink
    - quantity text grey
57. Ryan discovered a new bad problem and asked about spark profile in Downloads.
58. Ryan gave strict jar replacement rule: kill MC, wait 2s, replace jar.
59. Ryan asked next implementation pass.
60. Ryan asked to make chest key modifier line clickable with tooltip.
61. It was implemented.
62. Game got stuck launching.
63. Ryan pasted SkyHanni crash:
    - `RenderSystem called from wrong thread`
    - stack from SkyHanni `InstanceChestProfit`
    - triggered by DRT/SkyHanni renderable creation context
64. Thread/render timing issue was considered/fixed enough to proceed.
65. Ryan said it was time to do onboarding screen and emphasized UI quality.
66. Onboarding implemented.
67. Ryan said it failed to open again.
68. Ryan said Kuudra pet has no mythic, otherwise good.
69. Ryan requested onboarding UI changes:
    - skip button bottom-left
    - salvage/essence part of Kuudra, no separate headings
    - pet rarity/level hidden unless pet toggled
    - faction dropdown
    - force salvage dropdown
70. Ryan requested triangle dropdown icons.
71. Ryan requested a non-coding status summary.
72. A too-light `CatchUP.md` was created.
73. Ryan asked whether it included TBA state.
74. Ryan then clarified he wants the catch-up to be huge and include every shred needed for a new agent.

## Known Logs / Error Messages From Chat

Repeated startup warning:

```text
[DRT] skullFromTexture failed: Components not bound yet
```

Context:

- It spammed heavily on render thread during startup.
- It happened around Taunahi shader requests too, but the warning is DRT.
- The likely issue was trying to create skull/item textures before components/resources were bound.
- A fix was made to retry later and reduce spam.

SkyHanni crash Ryan pasted:

```text
SkyHanni 7.29.0 26.1.2: Caught a IllegalStateException in
at.hannibal2.skyhanni.features.combat.InstanceChestProfit.onInventoryFullyOpened(...)
Rendersystem called from wrong thread

Caused by java.lang.IllegalStateException: Rendersystem called from wrong thread
    at com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread(RenderSystem.java:108)
    ...
    at SH.features.combat.InstanceChestProfit.createDisplay(InstanceChestProfit.kt:367)
    at SH.features.combat.InstanceChestProfit.onInventoryFullyOpened(InstanceChestProfit.kt:178)
```

Context:

- This was SkyHanni crash output, but it appeared during the same testing session after DRT changes.
- The suspected class of issue is rendering/font/icon width/renderable construction happening on the wrong thread or too early.
- Be cautious with any new overlay/renderable work that computes font width or bakes glyphs outside render thread.

DRT flush debug:

- Useful lines include `[DRT][FLUSH]`.
- Flush line should include fields like:
  - `base`
  - `key`
  - `kismet`
  - `wheel`
  - `kuudraKey`
  - `profit`

## Current Kismet Detection State

This is the most fragile active area.

Ryan observed:

- Rerolling a chest did not add Kismet under open cost.
- It did not show in loot log.
- After reroll, all chests show `You already rerolled a chest` as an indication.
- There was a chat message too, but exact message text was not captured in this handoff.

Earlier incorrect assumption:

- The parser treated reroll marker in chest lore as per-chest.
- Since `You already rerolled a chest` appears on all chests, that made every cached offer look kismeted or made state ambiguous.

Latest intended design:

- `You already rerolled a chest` is global state for the reward menu.
- Seeing it arms `rewardMenuKismetRerollPending` / next opened chest.
- Do not create a loot log record from the reroll itself.
- When a chest is opened, consume the pending kismet flag and attach Kismet to that opened chest.
- If no chest is opened, nothing is logged.

Files/functions likely involved:

- `src/client/java/dev/vy/drt/client/tracker/DungeonRunTrackerFeature.java`
- `rewardMenuKismetRerollPending`
- `nextOpenedChestUsesKismetFeather`
- `lastRewardModifierScanHadKismetMarker`
- `armNextOpenedKismetReroll()`
- `assignPendingOpenedChest`
- `handleModifierMessage`
- `lineIndicatesKismetRerollState`
- `applyModifierLoreHints`

The latest patch intentionally removed per-offer `lineIndicatesKismetRerollState` use from `applyModifierLoreHints`, because that global text should not mark every offer as kismeted.

Test matrix:

1. Open reward menu, reroll, leave without opening:
   - expected: no loot-log record
   - expected: no fake chest save
2. Open reward menu, reroll, open chest:
   - expected: overlay shows Kismet cost under Open Cost
   - expected: loot log chest header has feather icon
   - expected: saved record has kismet cost
3. Open chest using dungeon chest key after reroll:
   - expected: key and kismet both appear
   - expected: profit subtracts both
4. Reroll one chest but open another:
   - current intended behavior likely counts kismet on the opened chest because Hypixel reroll applies to the chest opened after reroll. Confirm with Ryan if gameplay differs.

## Dungeon Chest Key Detection State

Important chat finding:

- Ryan said there is no dungeon chest key message.

Therefore:

- Do not rely on chat for dungeon chest key detection.
- Use concrete lore/button detection in the reward chest menu.

Ryan provided screenshot context:

- If a chest needs a key to be opened, the open reward chest button says so.

Current behavior:

- Key support worked in Ryan's test.
- Keep concrete detection as the source of truth.
- Key modifier line/icon is clickable in overlay and runs `/bz dungeon chest key`.

Test cases:

1. Free/open normal chest:
   - no dungeon chest key cost
2. Open second chest with dungeon chest key:
   - key cost appears
   - key icon appears in log/header
   - profit subtracts key cost
3. Open key chest after kismet:
   - key and kismet both appear

## Wheel Of Fate State

Wheel of Fate support was part of item 1 and was implemented structurally.

Current unknown:

- No clear real-world test result from Ryan yet.
- Need verify detection source.
- Need verify cost value from Athen/price cache.
- Need verify icon in overlay/log.

Do not mark this fully done without a live test.

## Pricing Model State

Ryan asked:

- What price do we use currently?
- Why manual essence config when Athen provides essence?
- Are we using instant sell or sell order?
- Wouldn't instant sell be better if the player receives a book and instantly sells it?

Then Tomato suggested:

- Use `Instant` vs `Order` rather than separate instant buy/sell labels.
- This also applies cleanly to buying keys/kismets.

Final chosen model:

- `Instant`
  - Loot value uses instant sell.
  - Modifier/key/kismet costs use instant buy.
- `Order`
  - Loot value uses sell offer.
  - Modifier/key/kismet costs use buy order.

Known config migration:

- `INSTANT_SELL` -> `INSTANT`
- `INSTANT_BUY` -> `INSTANT`
- `SELL_OFFER` -> `ORDER`
- `BUY_ORDER` -> `ORDER`

Likely files:

- `src/main/java/dev/vy/drt/config/DrtConfig.java`
- `src/main/java/dev/vy/drt/config/DrtConfigManager.java`
- `src/main/java/dev/vy/drt/price/PriceCache.java`
- `src/main/java/dev/vy/drt/price/DungeonProfitPricing.java`
- `src/client/java/dev/vy/drt/client/screen/DrtOnboardingScreen.java`

Test cases:

1. Set onboarding pricing to `Instant`.
   - A dungeon book/item should use instant sell for value.
   - Kismet/key cost should use instant buy.
2. Set onboarding pricing to `Order`.
   - Loot value should use sell offer.
   - Kismet/key cost should use buy order.
3. Restart and reopen onboarding.
   - Pricing mode persists.

## Essence State

Ryan asked why manual essence config is used because Athen provides essence.

Current intent:

- Use Athen price data for essence where possible.
- Do not force user to manually configure essence price if Athen has it.

Overlay behavior:

- Essence rows appear after item rows.
- Essence names should be pink.
- Quantity part should be grey, e.g. `Undead Essence x41` where `x41` is grey.

Remaining:

- Confirm actual source of essence pricing in `DungeonProfitPricing` / `PriceCache`.
- Confirm no stale manual override remains in onboarding unless still needed.

## Chest Overlay Final Requirements

Position:

- Right side of reward chest.
- Close to the right side of the chest UI.
- No big background.
- Dense line spacing.
- Render above chest/inventory dark overlay.

Layout:

```text
Chest header

Item #1
Item #2
Item #3
Item #4
Essence #1
Essence #2

Value: ...

Key/Open Cost: ...
Kismet: ...
Chest Key: ...
Wheel of Fate: ...

Profit: ...
```

Ryan's final wording:

```text
Chest
Empty
Item #1-4
Essence 1
Essence 2
Empty
Value
Empty
Open Cost modifiers (Kismet, chest key)
Empty
Profit
```

Text requirements:

- Chest header bold.
- Chest name readable, not mushed together.
- Book names simplified:
  - `Enchanted Book (Overload 1)` -> `Overload 1`
- Overload-style books blue.
- Ultimate enchants bold pink.
- Essence pink.
- Quantity grey.
- Profit green if positive, red if negative.
- Negative profit red even if value formatting has a special sign.
- Positive profit green.

Interaction:

- Dungeon chest key modifier line and key icon clickable.
- Tooltip: `Click to buy a dungeon chest key`.
- Command: `/bz dungeon chest key`.

Known old bugs:

- First overlay had a big background and was too far/large.
- Later overlay text was dark because it rendered under the chest shade.
- Header became mushed/unreadable.
- Bold text became blurry/broken in the bank/chest type text. The fix should preserve bold, not remove it.

## Croesus Overlay Final Requirements

Croesus chest reward menu:

- Show all chests, always in this order:
  - Bedrock
  - Obsidian
  - Emerald
  - Diamond
  - Gold
  - Wood
- Do not sort this main list by profit.
- Profits green, losses red.
- Chest icon and chest text color should match chest type.

Recommendation section:

```text
Open
1. CHESTICON Chest +5.8m
2. KEYICON CHESTICON Chest +927k
```

Rules:

- Line 1 is best normal/open choice.
- Line 2 appears only if using/buying a dungeon chest key makes the second chest worth opening.
- The second line should include key icon.

Hover/click:

- Hovering each chest row highlights its chest slot.
- Hovering the `Open` line for best chest highlights that chest green.
- Hovering the second/key line highlights that chest yellow.
- Highlight renders above other highlights.
- Clicking a line opens/views that chest.
- Tooltip says `Click to view xxx chest`.

Opened handling:

- If chest lore says `Already Opened`, display `Opened` in pink instead of profit.
- Ryan specifically noticed both the chest and open chest button can show `Already Opened`.

Tooltip layering:

- Ryan asked if tooltips can render above the overlay.
- This may still need work. Minecraft tooltip/layer ordering may require rendering overlay at the right phase or skipping overlay under mouse tooltip.

Main Croesus menu:

- Ryan wants unopened runs/chests highlighted green in the main `Croesus` menu with all runs.
- Do not highlight already opened runs.
- The lore line `Opened Chest: xxxx` means already opened, not unopened.
- First attempt incorrectly highlighted everything because it saw chest names in `Opened Chest:`.
- Current logic should require explicit unopened/unclaimed/openable/not opened language.

1.21.11 alignment:

- Ryan found 26.1.2 highlight worked, 1.21.11 was misaligned.
- Accessor mixin added to use real container `leftPos`, `topPos`, `imageWidth`, `imageHeight`.
- Needs real test.

Likely files:

- `src/client/java/dev/vy/drt/client/tracker/DungeonRunTrackerFeature.java`
- `src/client/java/dev/vy/drt/mixin/AbstractContainerScreenAccessor.java`
- `src/main/resources/drt.mixins.json`

## Onboarding Final Requirements

Ryan's UI critique:

- He does not want huge UI.
- He does not want poorly optimized rounded corners.
- He wants compact, intentional layout.
- Do not fill the middle with deadspace.
- Buttons need alignment.

Current onboarding requirements:

- First launch shows onboarding.
- `/drt config`, `/drt setup`, `/drt onboarding` reopen it.
- `/drt` alone gives invalid syntax message.
- Skip button bottom-left for people who "don't give a fuck".
- Overlay position button.
- Kuudra section:
  - faction dropdown
  - pet toggle
  - pet rarity and level only visible when pet toggle on
  - no mythic rarity
  - force salvage dropdown
  - Cool Forged toggle/level
  - Bazaar pricing mode dropdown
- Salvage/essence should be part of Kuudra area, not separate headings.
- Dropdowns:
  - use `▲` / `▼`
  - do not move the whole menu when opened
  - highlight selected option
  - no `[x]` or `[ ]`
- Width should be reduced compared to earlier version.
- Controls should not cross the user's red-line boundary from screenshot; align to a clean right side.

Likely files:

- `src/client/java/dev/vy/drt/client/screen/DrtOnboardingScreen.java`
- `src/client/java/dev/vy/drt/client/DrtClient.java`
- `src/main/java/dev/vy/drt/config/DrtConfig.java`
- `src/main/java/dev/vy/drt/config/DrtConfigManager.java`

Remaining:

- Real UI pass if Ryan sees more alignment issues.
- Check persistence.
- Check GUI scaling.

## Commands And Syntax

Known user-facing commands:

- `/drt config`
- `/drt setup`
- `/drt onboarding`
- `/drt loot`
- `/drt move`
- `/drt toggle`

Required invalid syntax output for bare `/drt`:

```text
[DRT] Invalid syntax, use /drt <config/loot/move/toggle>
```

Overlay click command:

```text
/bz dungeon chest key
```

Original todo mentioned `/bz dungeon chest`, but later specific request was `/bz dungeon chest key`.

## Important Files

Core tracker and render/event logic:

- `src/client/java/dev/vy/drt/client/tracker/DungeonRunTrackerFeature.java`

Client entrypoint, commands, screens:

- `src/client/java/dev/vy/drt/client/DrtClient.java`
- `src/client/java/dev/vy/drt/client/screen/DungeonLootScreen.java`
- `src/client/java/dev/vy/drt/client/screen/DungeonTrackerPositionScreen.java`
- `src/client/java/dev/vy/drt/client/screen/DrtOnboardingScreen.java`

Config/model:

- `src/main/java/dev/vy/drt/config/DrtConfig.java`
- `src/main/java/dev/vy/drt/config/DrtConfigManager.java`
- `src/main/java/dev/vy/drt/config/DungeonFloor.java`
- `src/main/java/dev/vy/drt/config/DungeonRunRecord.java`
- `src/main/java/dev/vy/drt/config/ChestCostBreakdown.java`
- `src/main/java/dev/vy/drt/config/DungeonChestOffer.java`

Pricing:

- `src/main/java/dev/vy/drt/price/PriceCache.java`
- `src/main/java/dev/vy/drt/price/DungeonProfitPricing.java`

NEU/item resolution:

- `src/client/java/dev/vy/drt/client/tracker/NeuItemResolver.java`

Cosmetics/skulls/startup warning area:

- `src/client/java/dev/vy/drt/client/cosmetics/CapeTextureManager.java`
- `src/main/java/dev/vy/drt/mixin/EntityRendererCosmeticsMixin.java`

Mixin accessor for screen bounds:

- `src/client/java/dev/vy/drt/mixin/AbstractContainerScreenAccessor.java`
- `src/main/resources/drt.mixins.json`

Build files:

- `build.gradle.kts`
- `build.obf.gradle.kts`

## Current Dirty Worktree Snapshot

At last handoff summary, modified files included:

- `build.gradle.kts`
- `build.obf.gradle.kts`
- `src/client/java/dev/vy/drt/client/DrtClient.java`
- `src/client/java/dev/vy/drt/client/cosmetics/CapeTextureManager.java`
- `src/client/java/dev/vy/drt/client/screen/DungeonLootScreen.java`
- `src/client/java/dev/vy/drt/client/screen/DungeonTrackerPositionScreen.java`
- `src/client/java/dev/vy/drt/client/tracker/DungeonRunTrackerFeature.java`
- `src/client/java/dev/vy/drt/client/tracker/NeuItemResolver.java`
- `src/client/java/dev/vy/drt/mixin/EntityRendererCosmeticsMixin.java`
- `src/main/java/dev/vy/drt/config/DrtConfig.java`
- `src/main/java/dev/vy/drt/config/DrtConfigManager.java`
- `src/main/java/dev/vy/drt/config/DungeonFloor.java`
- `src/main/java/dev/vy/drt/config/DungeonRunRecord.java`
- `src/main/java/dev/vy/drt/price/DungeonProfitPricing.java`
- `src/main/java/dev/vy/drt/price/PriceCache.java`
- `src/main/resources/drt.mixins.json`

New/untracked at that time:

- `src/client/java/dev/vy/drt/client/screen/DrtOnboardingScreen.java`
- `src/client/java/dev/vy/drt/mixin/AbstractContainerScreenAccessor.java`
- `src/main/java/dev/vy/drt/config/ChestCostBreakdown.java`
- `src/main/java/dev/vy/drt/config/DungeonChestOffer.java`
- `CatchUP.md`

Important:

- Some dirty files may include earlier/user changes.
- Do not blindly revert anything.
- If making a targeted fix, inspect the relevant file first.

## Implementation Details Worth Preserving

### Loot Log Data Model

`DungeonRunRecord` was expanded to store cost fields:

- base chest/open cost
- dungeon chest key cost
- kismet feather cost
- Wheel of Fate cost
- Kuudra key cost
- likely profit/value fields

`ChestCostBreakdown` was introduced to carry structured cost info.

`DungeonChestOffer` was introduced to represent cached chest/menu offers.

The loot log no longer trims to 500 records.

The duplicate numbering fix uses a separate next-number mechanism (`nextChestLogNumber`) rather than deriving display number from floor/run in a way that duplicates across floors.

### Item Name Cleanup

Needed behavior:

- Strip `Enchanted Book (...)`.
- Convert enchant roman numerals where appropriate.
- Display e.g. `Overload 1`, not `Enchanted Book (Overload I)`.

Preserve existing loot logger style for colors.

### Bold Text Rendering

Ryan noticed bold text in bank/chest header became blurry/broken.

Do not remove bold to hide this.

The fix should avoid whatever path causes bad font rendering. Earlier pass fixed this and re-added bold chest headers. Be careful if touching render code or text components.

### Skull / Icon Caching

Startup warning:

`[DRT] skullFromTexture failed: Components not bound yet`

Earlier approach likely tried to build skull item/icon too early. Fix was to defer/retry and reduce warning spam. If this warning comes back, focus on initialization timing rather than suppressing with blanket catch/log spam.

### Render Thread Safety

SkyHanni crashed with RenderSystem wrong-thread while creating display/renderables on inventory fully opened.

Lesson:

- Do not measure text/glyph widths or bake icons on a non-render thread.
- Do not create renderables in event contexts if they might cause GL/font uploads off render thread.
- Prefer store raw data from inventory events, then build visual renderables during render tick/thread.

## Testing Checklist

### Build

Run:

```powershell
.\gradlew.bat build
```

Then copy built jars to dist if needed:

```powershell
Copy-Item -LiteralPath .\versions\26.1.2\build\libs\DungeonRunTracker-1.0.2+26.1.2.jar -Destination .\dist\DungeonRunTracker-1.0.2+26.1.2.jar -Force
Copy-Item -LiteralPath .\versions\1.21.11\build\libs\DungeonRunTracker-1.0.2+1.21.11.jar -Destination .\dist\DungeonRunTracker-1.0.2+1.21.11.jar -Force
Get-ChildItem .\dist\*.jar | Select-Object Name,LastWriteTime,Length
```

Only copy into Prism if Ryan asks, and follow kill/wait/copy rule.

### Startup

Check:

- Game launches.
- No repeated `[DRT] skullFromTexture failed: Components not bound yet` spam.
- No render-thread crash.
- `/drt` syntax works.
- `/drt config` opens onboarding.

### Onboarding

Check:

- First launch onboarding opens only when expected.
- Skip bottom-left works.
- Panel is compact.
- No huge dead space.
- Buttons align.
- Dropdown uses `▲` / `▼`.
- Opening dropdown does not move whole menu.
- Selected dropdown rows highlight.
- No `[x]` / `[ ]` in dropdowns.
- Kuudra pet off hides rarity/level.
- Kuudra pet on shows rarity/level.
- No mythic rarity.
- Faction dropdown persists.
- Force salvage dropdown persists.
- Cool Forged level persists.
- Pricing mode `Instant` / `Order` persists.

### Reward Chest Overlay

Check:

- Overlay appears on right side of chest UI.
- It is close to chest UI.
- It has no big background.
- Text is readable above chest/menu darkening.
- Header is bold and not blurry/mushed.
- Item names clean.
- Colors match loot logger.
- Quantities are grey.
- Essence is pink.
- Value/open cost/profit layout has blank-line spacing.
- Negative profit red.
- Positive profit green.
- Already opened chest shows `Opened` in pink.
- Chest key line/icon clickable.
- Tooltip says `Click to buy a dungeon chest key`.
- Click runs `/bz dungeon chest key`.

### Dungeon Chest Key

Check:

- Normal chest open: no key cost.
- Key chest open: key icon/cost appears.
- Loot log shows separate key-opened chest/tab/entry.
- Profit subtracts key cost.
- Detection works without chat message.

### Kismet

Check:

- Reroll and do not open:
  - no loot log entry
  - no fake saved chest
- Reroll and open:
  - Kismet cost under open cost
  - feather icon in loot log
  - saved record has kismet cost
- Reroll plus dungeon chest key:
  - both Kismet and key appear
  - profit subtracts both
- If failing, inspect exact latest.log and item lore.

### Wheel Of Fate

Check:

- Wheel state is detected.
- Wheel cost appears under open cost.
- Wheel icon appears if intended.
- Profit subtracts wheel cost.
- Loot log persists wheel field.

### Croesus Reward Menu

Check:

- List order bedrock to wood.
- Profit/loss colors correct.
- Opened chests display `Opened` pink.
- Best chest recommendation correct.
- Second/key recommendation only appears when key is profitable.
- Hovering rows highlights slot.
- First recommendation highlight green.
- Second/key recommendation highlight yellow.
- Highlight renders over other highlights.
- Clicking rows opens/views chest.
- Tooltip says `Click to view xxx chest`.
- Tooltip layering acceptable.

### Main Croesus Menu

Check:

- Runs with actual unopened/unclaimed/openable chests highlight green.
- Already opened runs do not highlight.
- Lore line `Opened Chest: xxxx` should not count as unopened.
- If highlighting everything, tighten lore detection again.

### Version Alignment

Check both:

- 26.1.2
- 1.21.11

Known issue:

- Croesus highlight was aligned on 26.1.2 but misaligned on 1.21.11 before accessor fix.

After accessor mixin:

- Test both versions.
- If 1.21.11 remains off, inspect screen/container coordinates and slot x/y mapping.

### Pricing

Check:

- `Instant` mode:
  - loot uses instant sell
  - costs use instant buy
- `Order` mode:
  - loot uses sell offer
  - costs use buy order
- Essence pricing from Athen works.
- Manual essence config is not unexpectedly overriding Athen.

### Kuudra

Check:

- Faction setting affects key cost.
- Kuudra key cost included in profit.
- Pet settings affect salvage/essence calculation as expected.
- Cool Forged level increases essence bonus by:
  - level 1: 4
  - level 2: 8
  - level 3: 12
  - level 4: 16
  - level 5: 20
- Force salvage armor/wands/equipment behavior matches settings.

### TAP/TWAP

Check:

- TAP/TWAP affects overall profit or profit per run as intended.
- Verify with known sample before/after values.

## Likely Next Implementation / Debug Passes

If Ryan returns and asks "what next", suggested order:

1. Verify/fix kismet reroll behavior.
   - This is currently the highest-risk and most recently changed feature.
2. Verify Croesus main menu unopened detection.
   - It previously highlighted everything.
3. Verify 1.21.11 highlight alignment.
   - Accessor fix is in, but needs live proof.
4. Test Wheel of Fate.
   - Structurally added but not proven.
5. Test Kuudra key/faction and Cool Forged math.
6. Polish onboarding if Ryan reports more UI issues.
7. Clean up/commit once behavior is stable.

## Exact User Statements That Matter

These are paraphrased or exact enough to preserve intent:

- "Ok do 1"
- "Impliment 2"
- "Do number 3"
- "Impliment 4"
- "I dont get what to test, im confused"
- "It didnt acknowledge the dungeon chest key"
- "There is no dungeon chest key message"
- "Key worked."
- "Ok so im gonna reroll a kismet chest"
- "I dont think it worked, i rerolled a chest and the numbers just dont make sense."
- "The log is prism launcher not .minecraft"
- "And its my 26.1.2 profile"
- "I want the overlay to appear on the RIGHT side."
- "I want no background, close together like the one on the left side"
- "The text/overlay still isnt above the chest so its dark."
- "I dont want Enchanted book (Overload 1), i want it to just say Overload 1"
- "The names need colour"
- "The chest header needs to be bold too."
- "The chest header is weird, its mushed together the text isnt readable."
- "Why are we using manual essence config? athen provides essence"
- "Also are we using insta sell or sell order prices?"
- "Wouldnt instant sell be better?"
- "The bold text in bank is blurry and broken again. I dont want it removed i want you to figure out whats causing it."
- "For the profit, if its negative, make it red, if its profit, make it green. No matter the value"
- "Do you think that concrete lore detection is better for the loot logger aswell than our current system?"
- "Still dont code, this is talking stage"
- "I want it listed from bedrock to wood, regardless of profit."
- "Hovering over each chest highlights the one"
- "Hovering over the open chests 1st chest highlights it in green"
- "hovering over the 2nd best to open chests highlights it in yellow."
- "I want this to render above any other highlighting"
- "so CLICKING one of the lines opens that chest?"
- "tooltip/lore displays Already Opened... change the profit to Opened in pink"
- "for the overlay for the quantity so undead essence x41, make the quantity text grey"
- "When you replace a jar for me, kill minecraft instance, wait 2s, replace jar in prism"
- "Okay make chest key modifier line clickable. text Click to buy a dungeon chest key. make key icon clickable."
- "Okay its time to do the onboarding screen, this is an important one because i need the ui to be good and not like codex normally does it."
- "Kuudra pet doesnt have a mythic"
- "Add a skip button in the bottom left corner for people who dont give a fuck"
- "The entire onboarding menu doesnt need to move when a dropdown appears"
- "The dropdowns dont need [ X ] or [ ] just make it highlight if enabled"
- "All the buttons arent aligned"
- "if i run /drt just say [DRT] Invalid syntax, use /drt <config/loot/move/toggle>"
- Tomato feedback: switch between `Instant` and `Order` for both selling and buying sides.
- "The chest highlighting in the croesus menu works on 26.1.2, but its mis-aligned on 1.21.11"
- "in the main Croesus menu with all the runs, i want you to highlight unopened chests in green"
- "how does the chest highlighting work? because rn its highlighting everything, even chests ive already opened. Each one has lore saying Opened Chest: xxxx"
- "Kismet detection is either not working or its shit."
- "I rerolled a chest, it says You already rerolled a chest on all chests as indication."
- "It doesnt appear in the chest cost ui under open cost. It doenst show in loot log."
- "I want it to be FUCKING HUGE. Include every shred from this chat that an agent would need to continue"

## Things Not To Assume

- Do not assume chat messages exist for dungeon chest key usage.
- Do not assume `You already rerolled a chest` is per-chest. It appears globally on all chests.
- Do not assume Croesus main menu lore mentioning `Opened Chest:` means unopened.
- Do not assume the latest built jar is installed in Prism. Check timestamps.
- Do not copy jar into Prism unless Ryan asks.
- Do not test in `.minecraft` if Ryan says Prism profile log.
- Do not remove bold text to fix blurry bold.
- Do not make onboarding wider or more decorative unless Ryan asks.
- Do not add large overlay backgrounds.
- Do not sort Croesus main chest list by profit.

## Quick Start For Next Agent

1. Read this file.
2. Run:

```powershell
git status --short
```

3. Inspect the active file before editing, especially:

```powershell
rg -n "rewardMenuKismet|Kismet|Already Opened|Opened Chest|bazaarPriceMode|nextChestLogNumber|AbstractContainerScreenAccessor|/bz dungeon chest key" src
```

4. If Ryan asks to build:

```powershell
.\gradlew.bat build
Copy-Item -LiteralPath .\versions\26.1.2\build\libs\DungeonRunTracker-1.0.2+26.1.2.jar -Destination .\dist\DungeonRunTracker-1.0.2+26.1.2.jar -Force
Copy-Item -LiteralPath .\versions\1.21.11\build\libs\DungeonRunTracker-1.0.2+1.21.11.jar -Destination .\dist\DungeonRunTracker-1.0.2+1.21.11.jar -Force
Get-ChildItem .\dist\*.jar | Select-Object Name,LastWriteTime,Length
```

5. If Ryan asks to install to Prism, kill Java for `Skyblock 26.1.2(1)`, wait 2 seconds, then copy.

6. If Ryan reports kismet still broken:
   - Ask for latest.log around reroll/open.
   - Inspect actual item lore if possible.
   - Focus on global reroll state vs opened chest assignment.

7. If Ryan reports Croesus highlight wrong:
   - Check whether it is reward chest menu or main Croesus run list.
   - For main menu, inspect exact lore. `Opened Chest:` is not unopened.
   - For 1.21.11 alignment, inspect container bounds and slot coordinate calculations.

8. If Ryan asks "what's still left":
   - kismet real test
   - Wheel of Fate real test
   - Croesus main unopened detection real test
   - 1.21.11 highlight alignment real test
   - Kuudra key/faction real test
   - onboarding polish only if he reports issues

