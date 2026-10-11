package mesos.offer;

import mesos.board.Board;
import mesos.board.RowSlot;
import mesos.enums.*;
import mesos.game.Game;
import mesos.player.Player;
import mesos.player.Totem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests OfferChoiceValidator (package-private) directly.
 * Uses a real 2-player game board for cases that require populated rows.
 */
class OfferChoiceValidatorTest {

    private Game game;
    private Player alice;
    private Board board;
    private OfferTile tileB;  // 0top, 1bottom
    private OfferTile tileC;  // 1top, 0bottom
    private OfferTile tileD;  // 0top, 2bottom

    @BeforeEach
    void setUp() {
        alice = new Player("alice", new Totem(TotemColor.RED));
        Player bob = new Player("bob", new Totem(TotemColor.BLUE));
        Player charlie = new Player("charlie", new Totem(TotemColor.WHITE));
        game = new Game(PlayerCount.THREE, List.of(alice, bob, charlie));
        game.startGame();
        board = game.getBoard();
        alice.gainFood(20); // plenty of food for affordability tests
        List<OfferTile> tiles = board.getOfferTrack().getTiles();
        tileB = tiles.stream().filter(t -> t.getId() == OfferTileId.B).findFirst().orElseThrow();
        tileC = tiles.stream().filter(t -> t.getId() == OfferTileId.C).findFirst().orElseThrow();
        tileD = tiles.stream().filter(t -> t.getId() == OfferTileId.D).findFirst().orElseThrow();
    }

    // ---- null / wrong-tile guards ----

    @Test
    void isValid_nullChoice_returnsFalse() {
        assertFalse(OfferChoiceValidator.isValid(tileB, null, alice, board));
    }

    @Test
    void isValid_nullPlayer_returnsFalse() {
        OfferChoice choice = tileB.createChoice();
        assertFalse(OfferChoiceValidator.isValid(tileB, choice, null, board));
    }

    @Test
    void isValid_nullBoard_returnsFalse() {
        OfferChoice choice = tileB.createChoice();
        assertFalse(OfferChoiceValidator.isValid(tileB, choice, alice, null));
    }

    @Test
    void isValid_choiceForWrongTile_returnsFalse() {
        OfferChoice choice = tileB.createChoice(); // bound to tileB
        assertFalse(OfferChoiceValidator.isValid(tileC, choice, alice, board)); // validated for tileC
    }

    // ---- food-only tile (0top, 0bottom) ----

    @Test
    void isValid_foodOnlyTile_emptyChoiceValid() {
        OfferTile foodTile = new OfferTile(OfferTileId.A);
        foodTile.setCardsFromTop(0);
        foodTile.setCardsFromBottom(0);
        foodTile.setDirectFoodBonus(3);
        OfferChoice empty = foodTile.createChoice();
        assertTrue(OfferChoiceValidator.isValid(foodTile, empty, alice, board));
    }

    @Test
    void isValid_foodOnlyTile_nonEmptyChoiceInvalid() {
        OfferTile foodTile = new OfferTile(OfferTileId.A);
        foodTile.setCardsFromTop(0);
        foodTile.setCardsFromBottom(0);
        OfferChoice choice = foodTile.createChoice();
        choice.addPick(new CardPick(VisibleRow.TOP, 0));
        assertFalse(OfferChoiceValidator.isValid(foodTile, choice, alice, board));
    }

    // ---- budget bounds ----

    @Test
    void isValid_exceedsTopBudget_returnsFalse() {
        // tileC: cardsFromTop=1 — adding 2 TOP picks exceeds budget
        OfferChoice choice = tileC.createChoice();
        choice.addPick(new CardPick(VisibleRow.TOP, 0));
        choice.addPick(new CardPick(VisibleRow.TOP, 1));
        assertFalse(OfferChoiceValidator.isValid(tileC, choice, alice, board));
    }

    @Test
    void isValid_exceedsBottomBudget_returnsFalse() {
        // tileB: cardsFromBottom=1 — adding 2 BOTTOM picks exceeds budget
        OfferChoice choice = tileB.createChoice();
        choice.addPick(new CardPick(VisibleRow.BOTTOM, 0));
        choice.addPick(new CardPick(VisibleRow.BOTTOM, 1));
        assertFalse(OfferChoiceValidator.isValid(tileB, choice, alice, board));
    }

    // ---- invalid slot index ----

    @Test
    void isValid_nonExistentSlotIndex_returnsFalse() {
        OfferChoice choice = tileB.createChoice();
        choice.addPick(new CardPick(VisibleRow.BOTTOM, 9999));
        assertFalse(OfferChoiceValidator.isValid(tileB, choice, alice, board));
    }

    // ---- valid character picks (bottom row always has characters after setup) ----

    @Test
    void isValid_validCharacterPickFromBottom_returnsTrue() {
        // Bottom row always contains character cards (events go to top row during setup)
        Optional<RowSlot> charSlot = board.getVisibleRow(VisibleRow.BOTTOM).getSlots().stream()
                .filter(s -> s.containsCharacter() && s.isSelectable())
                .findFirst();
        assertTrue(charSlot.isPresent(), "Bottom row must have at least one character");

        OfferChoice choice = tileB.createChoice();
        choice.addPick(new CardPick(VisibleRow.BOTTOM, charSlot.get().getIndex()));
        assertTrue(OfferChoiceValidator.isValid(tileB, choice, alice, board));
    }

    @Test
    void isValid_validCharacterPickFromTop_returnsTrue() {
        Optional<RowSlot> charSlot = board.getVisibleRow(VisibleRow.TOP).getSlots().stream()
                .filter(s -> s.containsCharacter() && s.isSelectable())
                .findFirst();
        assertTrue(charSlot.isPresent(), "Top row must have at least one character");

        OfferChoice choice = tileC.createChoice();
        choice.addPick(new CardPick(VisibleRow.TOP, charSlot.get().getIndex()));
        assertTrue(OfferChoiceValidator.isValid(tileC, choice, alice, board));
    }

    // ---- empty choice when selectable cards exist ----

    @Test
    void isValid_emptyChoiceWhenSelectableCardsExist_returnsFalse() {
        // Bottom row has selectable cards → budgetBottom >= 1 → empty choice invalid
        long selectableInBottom = board.getVisibleRow(VisibleRow.BOTTOM).getSlots().stream()
                .filter(RowSlot::isSelectable).count();
        if (selectableInBottom > 0) {
            OfferChoice emptyChoice = tileB.createChoice();
            assertFalse(OfferChoiceValidator.isValid(tileB, emptyChoice, alice, board));
        }
    }

    // ---- two-bottom picks for tile D ----

    @Test
    void isValid_tileD_twoValidBottomPicks_returnsTrue() {
        List<RowSlot> charSlots = board.getVisibleRow(VisibleRow.BOTTOM).getSlots().stream()
                .filter(s -> s.containsCharacter() && s.isSelectable())
                .toList();
        if (charSlots.size() < 2) return; // only 1 char in bottom — skip

        OfferChoice choice = tileD.createChoice();
        choice.addPick(new CardPick(VisibleRow.BOTTOM, charSlots.get(0).getIndex()));
        choice.addPick(new CardPick(VisibleRow.BOTTOM, charSlots.get(1).getIndex()));
        assertTrue(OfferChoiceValidator.isValid(tileD, choice, alice, board));
    }

    @Test
    void isValid_tileD_exceedsThreeBottomPicks_returnsFalse() {
        // tileD: cardsFromBottom=2 — 3 picks is too many
        OfferChoice choice = tileD.createChoice();
        choice.addPick(new CardPick(VisibleRow.BOTTOM, 0));
        choice.addPick(new CardPick(VisibleRow.BOTTOM, 1));
        choice.addPick(new CardPick(VisibleRow.BOTTOM, 2));
        assertFalse(OfferChoiceValidator.isValid(tileD, choice, alice, board));
    }

    // ---- empty choice when top row has selectable cards ----

    @Test
    void isValid_emptyChoice_topRowHasSelectableCards_returnsFalse() {
        // tileC picks 1 from top; top row has selectable cards → budgetTop=1 → empty choice fails
        assertTrue(board.getVisibleRow(VisibleRow.TOP).getSlots().stream()
                .anyMatch(RowSlot::isSelectable),
                "Top row must contain at least one selectable card");
        assertFalse(OfferChoiceValidator.isValid(tileC, tileC.createChoice(), alice, board));
    }

    // ---- building pick is valid when characters are also present ----

    @Test
    void isValid_topPick_buildingWhenCharAvailable_returnsTrue() {
        // Buildings and characters are on the same level: picking a building while
        // characters are also in the top row is valid, as long as the player has food.
        List<RowSlot> topSlots = board.getVisibleRow(VisibleRow.TOP).getSlots();
        Optional<RowSlot> buildingSlot = topSlots.stream()
                .filter(RowSlot::containsBuilding).findFirst();
        if (buildingSlot.isEmpty()) return; // no building exposed — skip gracefully

        OfferChoice choice = tileC.createChoice();
        choice.addPick(new CardPick(VisibleRow.TOP, buildingSlot.get().getIndex()));
        // alice has 20 food (setUp) — enough to afford any Era-I building
        assertTrue(OfferChoiceValidator.isValid(tileC, choice, alice, board));
    }

    // ---- buildings and characters may be mixed in any order ----

    @Test
    void isValid_buildingAndCharacterPick_anyOrder_returnsTrue() {
        // A 2-top-pick tile allows [building, char] in any order — both picks valid.
        List<RowSlot> topSlots = board.getVisibleRow(VisibleRow.TOP).getSlots();
        Optional<RowSlot> charSlot = topSlots.stream()
                .filter(s -> s.containsCharacter() && s.isSelectable()).findFirst();
        Optional<RowSlot> buildingSlot = topSlots.stream()
                .filter(RowSlot::containsBuilding).findFirst();
        if (charSlot.isEmpty() || buildingSlot.isEmpty()) return;

        topSlots.stream()
                .filter(s -> s != charSlot.get() && s != buildingSlot.get())
                .forEach(RowSlot::clearCard);

        OfferTile twoTopTile = new OfferTile(OfferTileId.F);
        twoTopTile.setCardsFromTop(2);
        twoTopTile.setCardsFromBottom(0);

        OfferChoice choice = twoTopTile.createChoice();
        choice.addPick(new CardPick(VisibleRow.TOP, buildingSlot.get().getIndex())); // building first
        choice.addPick(new CardPick(VisibleRow.TOP, charSlot.get().getIndex()));     // char second
        // alice has 20 food (setUp)
        assertTrue(OfferChoiceValidator.isValid(twoTopTile, choice, alice, board));
    }

    // ---- event-only row: pick is optional when no selectable cards remain ----

    @Test
    void isValid_tileE_bottomHasOnlyEvents_emptyBottomPickAccepted() {
        // Tile E: 1 top, 1 bottom. Clear all non-event cards from bottom → budget becomes 0.
        OfferTile tileE = board.getOfferTrack().getTiles().stream()
                .filter(t -> t.getId() == OfferTileId.E).findFirst().orElseThrow();

        List<RowSlot> bottomSlots = board.getVisibleRow(VisibleRow.BOTTOM).getSlots();
        bottomSlots.stream()
                .filter(s -> !s.containsEvent() && !s.isEmpty())
                .forEach(RowSlot::clearCard);

        // Top row must still have a selectable character so we can form a valid choice.
        Optional<RowSlot> charSlot = board.getVisibleRow(VisibleRow.TOP).getSlots().stream()
                .filter(s -> s.containsCharacter() && s.isSelectable()).findFirst();
        if (charSlot.isEmpty()) return;

        OfferChoice choice = tileE.createChoice();
        choice.addPick(new CardPick(VisibleRow.TOP, charSlot.get().getIndex()));
        assertTrue(OfferChoiceValidator.isValid(tileE, choice, alice, board),
                "Pick from top only should be valid when bottom row has only events");
    }

    // ---- building cost check ----

    @Test
    void isValid_buildingPick_noCharsInTopRow_evaluatesBuildingCost() {
        // Building pick is valid as long as the player can afford it.
        List<RowSlot> topSlots = board.getVisibleRow(VisibleRow.TOP).getSlots();
        topSlots.stream().filter(RowSlot::containsCharacter).forEach(RowSlot::clearCard);

        Optional<RowSlot> buildingSlot = topSlots.stream()
                .filter(RowSlot::containsBuilding).findFirst();
        if (buildingSlot.isEmpty()) return;

        OfferChoice choice = tileC.createChoice();
        choice.addPick(new CardPick(VisibleRow.TOP, buildingSlot.get().getIndex()));
        // alice has 20 food (setUp) — enough to afford any Era-I building
        assertTrue(OfferChoiceValidator.isValid(tileC, choice, alice, board));
    }
}
