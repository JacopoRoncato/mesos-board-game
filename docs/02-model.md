# 02 — Model

> 🇮🇹 Versione italiana: [`it/02-modello.md`](it/02-modello.md)

The Model is **pure game state + business logic**. It does not know about networks, sockets, the controller, or any view. The only outward-facing wire is `setListener(PropertyChangeListener)` on `Game` and `Player` — and even then, the Model just calls `listener.propertyChange(...)` without caring who's listening.

## Package map

```
mesos/
├── game/   Game            ── top-level orchestrator (round/era/phase)
│        ScoreCalculator    ── final-prestige math, delegated from Game.calculateFinalScore
├── board/  Board            ── tribe deck, top/bottom rows, offer track, turn-order tile
│        BuildingDeckFactory ── per-Era Building card pools (factored out of Board)
│        TribeDeckFactory    ── per-Era Tribe deck slices (factored out of Board)
│        VisibleRowState     ── one of the two visible rows
│        RowSlot             ── one slot in a visible row, holds 0 or 1 card
│        RowRefillResult     ── flags returned from refill (era transition, deck exhausted)
├── cards/  Card             ── abstract base, has name + Era
│        TribeDeckCard       ── any card that lives in the tribe deck
│        BuildingCard        ── building (food cost + PP + effect strategy)
│        FinalEventCard      ── marker at the bottom of the deck
│   ├── characters/   CharacterCard (abstract) + 6 concrete types
│   └── events/       EventCard     (abstract) + 4 concrete event types
├── effects/  BuildingEffect (abstract) + 8 concrete strategies
├── enums/    Era, PlayerCount, GamePhase, EventType, CharacterType, …
├── offer/    OfferTrack         ── the row of A..G offer tiles
│         OfferTile             ── one tile (cards to take + food bonus)
│         OfferChoice           ── ordered list of CardPicks for one tile resolution
│         OfferChoiceValidator  ── stateless validator (upper-bound + food simulator)
│         CardPick              ── { row, slotIndex } pointing into the board
├── order/   TurnOrderTile      ── 2..5 spaces, each with food bonus + lastSpace flag
│         TurnOrderSpace        ── one space, optionally occupied by a Totem
├── player/  Player              ── id, food, PP, totem, tribe, selected tile
│         Totem                  ── colour
└── tribe/   Tribe               ── per-player collection (delegates to helpers)
           CharacterStats        ── pure-math aggregator over character cards
           BuildingEffects       ── broadcast hooks + reducers over building cards
```

## Class responsibilities

### `Game` ([`mesos.game.Game`](../src/main/java/mesos/game/Game.java))

The orchestrator. Holds:
- `currentRound`, `currentEra`, `phase` — the temporal state
- `MAX_ROUNDS = 10` — fixed by rules
- `playerCount` — for setup decisions
- `players: List<Player>` — turn-order (NOT ranking — `getRanking()` returns a sorted copy)
- `board: Board` — physical state
- `deckExhaustedFlag` — set when refill draws the FinalEventCard, triggers the final-round path

Lifecycle methods:
- `setup()` — builds decks (via factories), configures the board, places totems randomly on the Turn Order Tile, assigns initial food
- `startGame()` — transitions SETUP → PLACEMENT
- `placementPhase()` — re-asserts PLACEMENT (idempotent, doesn't clear spaces — totems are removed individually as they're placed on Offer Tiles)
- `actionPhase()` — sets ACTION_RESOLUTION, builds `executionOrder` (players sorted left-to-right on the offer track), resets `executionTurnIndex` to 0
- `getCurrentExecutionPlayer()` — returns the player at `executionOrder[executionTurnIndex]`; null when all have executed
- `setExtraPickPending(Player)` — sets `extraPickPendingId` to the player's id and fires a snapshot broadcast; called by the Controller when the player owns the B020 extra-pick building effect
- `getExtraPickPendingId()` — returns the nick of the player currently awaiting their extra top-row pick, or null
- `markPlayerDone(Player)` — adds player to `finishedThisRound`, increments `executionTurnIndex`, clears `extraPickPendingId`, fires a broadcast
- `endOfRound()` — bifurcates: normal round (`resolveNormalRound`) vs final round (`resolveFinalRound`)
- `placeTotem(Player, OfferTile)` — single Model entry-point for moving a totem from Turn Order Tile to an Offer Tile
- `calculateFinalScore()` — delegates to [`ScoreCalculator.awardFinalPrestige`](../src/main/java/mesos/game/ScoreCalculator.java)
- `getRanking()` — sorted copy of players (PP desc, Food desc) — does NOT mutate `getPlayers()`

Listener hooks (see [06 — Listeners](06-listeners.md)):
- `setListener(PropertyChangeListener)` — registers a listener AND propagates it to all `Player`s
- `fire(prop, old, new)` — private helper that delegates to the listener
- `setPhase / setCurrentEra / setCurrentRound` — internal setters that fire the corresponding event before storing

### `ScoreCalculator` ([`mesos.game.ScoreCalculator`](../src/main/java/mesos/game/ScoreCalculator.java))

Stateless helper, package-private. Computes end-game prestige and stamps it onto each player:

- **Inventors × distinct icons** (§16 — *PP pari al numero di Inventori moltiplicato per il numero di icone invenzione diverse*)
- **10 PP per pair of Artists** (§16 — *10 PP per ogni 2 Artisti*)
- **Builder printed PP** (§16 — *PP dei Costruttori*)
- **Building printed PP + end-game bonuses** (§16 — *PP degli Edifici, più i PP derivanti dagli eventuali effetti di fine partita*)

`Game.calculateFinalScore()` is now a one-line delegation; the math lives here so it can evolve (or be tested) without touching round flow.

### `Board` ([`mesos.board.Board`](../src/main/java/mesos/board/Board.java))

The physical layout: tribe deck, top + bottom visible rows, offer track, turn-order tile, building decks (kept aside for Era II/III).

Key operations:
- `buildTribeDeck(playerCount)` — assembles the deck in the order required by rules §3.3: Era I (top) → Era II → Era III → 2 `FinalEventCard`s (bottom). Each Era is shuffled separately. Deck construction itself is delegated to [`TribeDeckFactory.createEraSlice`](../src/main/java/mesos/board/TribeDeckFactory.java).
- `buildBuildingDecks(playerCount)` — draws a shuffled subset of buildings per Era according to the rules §3.6 count table. Card construction is delegated to [`BuildingDeckFactory.createPool`](../src/main/java/mesos/board/BuildingDeckFactory.java).
- `revealInitialRows(playerCount)` — implements §3.4/§3.5: deal `n+1` to the bottom row, redirecting events to the top; then fill the top row up to `n+4`.
- `refillTopRow(playerCount)` — runs the four-step end-of-round sequence: discard bottom characters/events → move top characters/events down → refill top from deck → detect era transitions or final-event marker.
- `applyEraTransition(newEra)` — at Era III start, discards bottom buildings; at any Era II/III start, moves top buildings down and reveals the new era's buildings on top.
- `getActiveRoundEvents()` / `getAllVisibleEvents()` / `sortEventsForResolution(events)` — used by `Game` for both the per-round and final-round event flow. Sustenance is always last (priority 99).

Player-count to int conversion is on the enum itself (`playerCount.toInt()`) — no Board-side helper.

### Card hierarchy

```
Card  (abstract: name, era)
├── BuildingCard          (foodCost, printedPP, BuildingEffect strategy)
└── TribeDeckCard  (abstract: allowedPlayerCounts)
    ├── FinalEventCard    (marker)
    ├── CharacterCard  (abstract)
    │   ├── Hunter        (hasImmediateFoodIcon)
    │   ├── Shaman        (shamanIcons: 1..3)
    │   ├── Artist
    │   ├── Builder       (ppValue, discountValue)
    │   ├── Inventor      (inventionIcon: 1..10)
    │   └── Gatherer      (sustenanceDiscount = 3)
    └── EventCard  (abstract: eventType)
        ├── SustenanceEvent      (ppPenaltyPerUnfedCharacter)
        ├── HuntingEvent         (ppRewardPerHunter)
        ├── ShamanicRitualEvent  (ppReward, ppPenalty — handles ties carefully)
        └── CavePaintingsEvent   (artistThreshold, ppPenalty, ppRewardPerArtist)
```

### Building effects (Strategy pattern)

[`BuildingEffect`](../src/main/java/mesos/effects/BuildingEffect.java) defines a flat interface of lifecycle hooks (`onAcquire`, `onCharacterAdded`, `onTurnOrderPlaced`, `onEndOfActionPhase`, `getExtraSustenanceDiscount`, …). Each concrete effect overrides only the hooks it needs:

| Effect | Hook(s) | Rule |
|---|---|---|
| `NoBuildingEffect` | none | placeholder for "flat 25 PP" + similar |
| `SustenanceDiscountEffect` | `getExtraSustenanceDiscount` | §17.2 |
| `ShamanicRitualEffect` | `getAdditionalShamanIcons`, `ignoresShamanicPenalty`, `doublesShamanicReward` | §17.3, §17.6, §17.7 |
| `TurnOrderPlacementEffect` | `onTurnOrderPlaced` | §17.4 |
| `TriggeredFoodRewardEffect` | `onAcquire` (snapshot) + `onCharacterAdded` | §17.1, §17.5 |
| `PerCharacterEventBonusEffect` | `getExtraFoodPerCharacterForEvent`, `getExtraPrestigePerCharacterForEvent` | §17.8, §17.10 |
| `ExtraTopRowPickEffect` | `getAvailableTopRowPicks` / `executeTopRowPick` (controller-driven) | §17.13 |
| `EndGameScoringEffect` | `getEndGameBonus` | §17.9, §17.11, §17.12, §17.14 |

`BuildingCard` doesn't implement any rules itself — it forwards every hook to its `BuildingEffect`. This is what lets all 21 cards reuse the same `BuildingCard` class with different effect instances.

### `Player` ([`mesos.player.Player`](../src/main/java/mesos/player/Player.java))

Owns `food`, `prestigePoints`, a `Totem`, a `Tribe`, and the currently-selected `OfferTile`. The four resource mutators (`gainFood`, `payFood`, `addPrestige`, `removePrestige`) are the choke points — every PP/food change in the game routes through them, which makes them the right place to fire `FOOD/...` and `PRESTIGE/...` events (see [06 — Listeners](06-listeners.md)).

`acquireBuilding(BuildingCard)` throws `IllegalStateException` if the food can't be paid. All callers either pre-validate (`OfferChoiceValidator`, `ExtraTopRowPickEffect.executeTopRowPick`) or treat the exception as a hard error.

### `Tribe` ([`mesos.tribe.Tribe`](../src/main/java/mesos/tribe/Tribe.java))

Per-player collection of `CharacterCard` + `BuildingCard`. After the round-3 split, `Tribe` is a thin facade:

- Character-side math (`countCharacters`, `calculateBuildingDiscount`, `calculateSustenanceDiscount`, `getTotalShamanIcons`, `countDistinctInventionIcons`, `countCompleteCharacterSets`, `countInventorPairs`, `countArtistPairs`) is delegated to [`CharacterStats`](../src/main/java/mesos/tribe/CharacterStats.java).
- Building-side aggregation and broadcast hooks (`notifyBuildingsOn*`, `getExtraSustenanceDiscount`, `getAdditionalShamanIcons`, `ignoresShamanicPenalty`, `doublesShamanicReward`, `getExtraFoodPerCharacterForEvent`, `getExtraPrestigePerCharacterForEvent`, `getBuildingEndGameBonus`, `findExtraTopRowPickEffect`) is delegated to [`BuildingEffects`](../src/main/java/mesos/tribe/BuildingEffects.java).

Adding a new character stat → edit `CharacterStats` only. Adding a new building reducer → edit `BuildingEffects` only. The facade stays stable.

### `OfferTile` + `OfferChoiceValidator`

`OfferTile` ([`mesos.offer.OfferTile`](../src/main/java/mesos/offer/OfferTile.java)) is data + execution: it holds the tile's id, pick budgets, food bonus, and occupant; `executeChoice` mutates the board after a successful validation.

Validation itself lives in [`OfferChoiceValidator`](../src/main/java/mesos/offer/OfferChoiceValidator.java) (package-private, stateless). It enforces:

1. **Tile-type match** — choice's `getOfferTile()` is this tile.
2. **Upper bound** — per-row pick count ≤ effective budget (capped by selectable cards actually in the row; events are never selectable).
3. **Empty-choice** — valid only when the effective budget in both rows is 0 (no selectable cards remain).
4. **Slot validity + food budget** — simulates the picks against a cloned row state, rejecting unknown slot indexes, non-selectable slots, and unaffordable building costs.

`OfferTile.validateChoice` is a one-line delegation to the validator.

## Game lifecycle (one full round)

```
[Round N]
  Game.placementPhase()                  ── controller calls this
       Game.placeTotem(P1, B)            ── for each player in turn-order
       Game.placeTotem(P2, C)
       ...
  Game.actionPhase()                     ── sets each player's selectedOfferTile
       OfferTile.executeChoice(...)      ── controller drives, in left-to-right order
            OfferChoiceValidator.isValid ── upper-bound + food check
            Player.acquireCharacter(...)
            Player.acquireBuilding(...)
            TurnOrderTile.addTotem(...)  ── totem returns to first free space
            applySpaceEffect(...)        ── gains food bonus or pays last-space cost
            notifyBuildingsOnTurnOrderPlaced(...)
       ExtraTopRowPickEffect.executeTopRowPick(...)  ── if any tribe has the effect
  Game.endOfRound()
       notifyBuildingsOnEndOfActionPhase(...)
       (normal path)
         resolve bottom-row events (Sustenance last)
         board.refillTopRow()            ── discard, move, refill, detect era transition
         handleRefillResult()            ── applyEraTransition if needed; set deckExhausted if refill ran out
         currentRound++
         setPhase(PLACEMENT)             ── ready for round N+1
       (final path, when deckExhausted or currentRound >= 10)
         resolve ALL events from BOTH rows together
         triggerEndGame()                ── ScoreCalculator + setPhase(GAME_OVER)
```

## Rules-to-code highlights

| Rule | Where |
|---|---|
| Deck order (Era I top → final events bottom) | `Board.buildTribeDeck` + `TribeDeckFactory` |
| Bottom row `n+1`, top row `n+4`, events redirected up | `Board.revealInitialRows` |
| Initial food (2,3,3,4,4) | `Game.assignInitialFood` |
| Builder discount cumulative, min 0 | `BuildingCard.calculateRealCost` |
| Hunter with food icon: +1 food per Hunter (incl. new) | `Hunter.onPurchase` |

| Sustenance: pay all food, then PP loss per unfed | `SustenanceEvent.resolveEvent` |
| Hunt: 1 food + PP per Hunter (+ building bonuses) | `HuntingEvent.resolveEvent` |
| Shamanic ritual ties (incl. all-tied edge case) | `ShamanicRitualEvent.resolveEvent` (separate `if` not `else if` on min check) |
| Cave Paintings two-threshold | `CavePaintingsEvent.resolveEvent` |
| Sustenance always last | `Board.eventPriority` returns 99 for SUSTENANCE |
| Era II/III transitions (move/discard buildings) | `Board.applyEraTransition` |
| Final-round all-events-together | `Game.resolveFinalRound` |
| Final scoring (Inventors × icons, Artists ÷ 2 × 10, Builders, Buildings) | `ScoreCalculator.awardFinalPrestige` |
| Tiebreaker: PP → Food → shared | `Game.getRanking` |
| Same player can't have totem on two tiles | `OfferTrack.placeTotem` (clears previous first) |

## Open TODOs (data fidelity, not rule semantics)

Both flagged in source headers:

1. **Per-Tribe-card player-count markers** in [`TribeDeckFactory.allowedForCopy`](../src/main/java/mesos/board/TribeDeckFactory.java) — currently a heuristic (last copy = 4p+, second-to-last = 3p+). The physical components have specific markers on each card.
2. **Per-Era Building distribution** in [`BuildingDeckFactory`](../src/main/java/mesos/board/BuildingDeckFactory.java) — total counts (6/7/8) match §3.6; the exact card identities per Era need transcription from the physical box.

Neither affects the architecture; both are data-only.

## Encapsulation

All fields private. Collection getters return defensive copies (`new ArrayList<>(...)`, `EnumSet.copyOf(...)`). No public setters on `Player.food` / `Player.prestigePoints` — only the four resource mutators can change them, which is what makes single-point event firing possible.
