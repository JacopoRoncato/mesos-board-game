package mesos.effects;

import mesos.board.Board;
import mesos.board.RowSlot;
import mesos.cards.BuildingCard;
import mesos.cards.characters.CharacterCard;
import mesos.enums.*;
import mesos.game.Game;
import mesos.offer.CardPick;
import mesos.player.Player;
import mesos.player.Totem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ExtraTopRowPickEffectTest {

    private ExtraTopRowPickEffect effect;
    private Player alice;
    private Board board;

    @BeforeEach
    void setUp() {
        effect = new ExtraTopRowPickEffect();
        alice = new Player("alice", new Totem(TotemColor.RED));
        Player bob = new Player("bob", new Totem(TotemColor.BLUE));
        Game game = new Game(PlayerCount.TWO, List.of(alice, bob));
        game.startGame();
        board = game.getBoard();
        alice.gainFood(100);
    }

    // ---- getAvailableTopRowPicks ----

    @Test
    void getAvailableTopRowPicks_nullOwner_returnsEmpty() {
        assertTrue(effect.getAvailableTopRowPicks(null, board).isEmpty());
    }

    @Test
    void getAvailableTopRowPicks_nullBoard_returnsEmpty() {
        assertTrue(effect.getAvailableTopRowPicks(alice, null).isEmpty());
    }

    @Test
    void getAvailableTopRowPicks_normalBoard_returnsNonEmpty() {
        List<CardPick> picks = effect.getAvailableTopRowPicks(alice, board);
        assertFalse(picks.isEmpty(), "Top row should have selectable cards after startGame");
    }

    @Test
    void getAvailableTopRowPicks_allPicksFromTopRow() {
        List<CardPick> picks = effect.getAvailableTopRowPicks(alice, board);
        picks.forEach(p -> assertEquals(VisibleRow.TOP, p.getRow()));
    }

    @Test
    void getAvailableTopRowPicks_noFood_onlyCharacterPicks() {
        alice.payFood(alice.getFood()); // drain ALL food → 0
        List<CardPick> picks = effect.getAvailableTopRowPicks(alice, board);
        // With 0 food, no building (cost > 0) should appear
        for (CardPick pick : picks) {
            RowSlot slot = board.getVisibleRow(VisibleRow.TOP).getSlot(pick.getSlotIndex());
            assertNotNull(slot);
            assertTrue(slot.getCard() instanceof CharacterCard,
                    "With no food, only character picks should be available");
        }
    }

    // ---- executeTopRowPick ----

    @Test
    void executeTopRowPick_nullPick_noOp() {
        int tribeBefore = alice.getTribe().getCharacterCards().size();
        effect.executeTopRowPick(alice, board, null);
        assertEquals(tribeBefore, alice.getTribe().getCharacterCards().size());
    }

    @Test
    void executeTopRowPick_nullOwner_noOp() {
        Optional<RowSlot> charSlot = firstCharSlot();
        charSlot.ifPresent(slot ->
            assertDoesNotThrow(() ->
                effect.executeTopRowPick(null, board, new CardPick(VisibleRow.TOP, slot.getIndex())))
        );
    }

    @Test
    void executeTopRowPick_nullBoard_noOp() {
        assertDoesNotThrow(() ->
            effect.executeTopRowPick(alice, null, new CardPick(VisibleRow.TOP, 0)));
    }

    @Test
    void executeTopRowPick_invalidSlotIndex_noOp() {
        int tribeBefore = alice.getTribe().getCharacterCards().size();
        effect.executeTopRowPick(alice, board, new CardPick(VisibleRow.TOP, 9999));
        assertEquals(tribeBefore, alice.getTribe().getCharacterCards().size());
    }

    @Test
    void executeTopRowPick_characterSlot_addsCharacterToTribe() {
        Optional<RowSlot> charSlot = firstCharSlot();
        assertTrue(charSlot.isPresent(), "Top row should have a character card");

        int tribeBefore = alice.getTribe().getCharacterCards().size();
        effect.executeTopRowPick(alice, board, new CardPick(VisibleRow.TOP, charSlot.get().getIndex()));
        assertEquals(tribeBefore + 1, alice.getTribe().getCharacterCards().size());
    }

    @Test
    void executeTopRowPick_characterSlot_slotBecomesEmpty() {
        Optional<RowSlot> charSlot = firstCharSlot();
        assertTrue(charSlot.isPresent(), "Top row should have a character card");

        int index = charSlot.get().getIndex();
        effect.executeTopRowPick(alice, board, new CardPick(VisibleRow.TOP, index));
        assertTrue(board.getVisibleRow(VisibleRow.TOP).getSlot(index).isEmpty());
    }

    @Test
    void executeTopRowPick_buildingSlot_deductsFood() {
        Optional<RowSlot> buildingSlot = firstBuildingSlot();
        if (buildingSlot.isEmpty()) {
            return; // no buildings in top row — test not applicable
        }
        BuildingCard building = (BuildingCard) buildingSlot.get().getCard();
        int cost = building.calculateRealCost(alice.getTribe().calculateBuildingDiscount());
        int foodBefore = alice.getFood();

        effect.executeTopRowPick(alice, board, new CardPick(VisibleRow.TOP, buildingSlot.get().getIndex()));
        assertEquals(foodBefore - cost, alice.getFood());
    }

    @Test
    void executeTopRowPick_buildingSlotInsufficientFood_noOp() {
        Optional<RowSlot> buildingSlot = firstBuildingSlot();
        if (buildingSlot.isEmpty()) {
            return; // no buildings in top row — test not applicable
        }
        BuildingCard building = (BuildingCard) buildingSlot.get().getCard();
        int cost = building.calculateRealCost(alice.getTribe().calculateBuildingDiscount());
        if (cost == 0) {
            return; // free building — can't test insufficient food
        }
        alice.payFood(alice.getFood()); // drain ALL food to 0
        int tribeBefore = alice.getTribe().getBuildingCards().size();
        int foodBefore = alice.getFood();

        effect.executeTopRowPick(alice, board, new CardPick(VisibleRow.TOP, buildingSlot.get().getIndex()));
        assertEquals(tribeBefore, alice.getTribe().getBuildingCards().size());
        assertEquals(foodBefore, alice.getFood());
    }

    @Test
    void executeTopRowPick_slotAlreadyEmpty_isNoOp() {
        Optional<RowSlot> charSlot = firstCharSlot();
        assertTrue(charSlot.isPresent(), "Top row should have a character card");
        int index = charSlot.get().getIndex();
        CardPick pick = new CardPick(VisibleRow.TOP, index);
        // first pick removes the card — slot is now non-null but not selectable (empty)
        effect.executeTopRowPick(alice, board, pick);
        int tribeBefore = alice.getTribe().getCharacterCards().size();
        // second pick on same slot: slot != null but !isSelectable() → no-op
        effect.executeTopRowPick(alice, board, pick);
        assertEquals(tribeBefore, alice.getTribe().getCharacterCards().size());
    }

    // ---- other hooks ----

    @Test
    void onEndOfActionPhase_isNoOp() {
        assertDoesNotThrow(() -> effect.onEndOfActionPhase(alice, board));
    }

    @Test
    void getDescription_returnsExtraTopRowPick() {
        assertEquals("EXTRA_TOP_ROW_PICK", effect.getDescription());
    }

    // ---- helpers ----

    private Optional<RowSlot> firstCharSlot() {
        return board.getVisibleRow(VisibleRow.TOP).getSlots().stream()
                .filter(s -> s.containsCharacter() && s.isSelectable())
                .findFirst();
    }

    private Optional<RowSlot> firstBuildingSlot() {
        return board.getVisibleRow(VisibleRow.TOP).getSlots().stream()
                .filter(s -> s.containsBuilding() && s.isSelectable())
                .findFirst();
    }
}
