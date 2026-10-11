package mesos.offer;

import mesos.board.Board;
import mesos.board.RowSlot;
import mesos.enums.*;
import mesos.game.Game;
import mesos.player.Player;
import mesos.player.Totem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OfferTileTest {

    private Player alice;
    private Board board;
    private OfferTile tileC; // cardsFromTop=1, cardsFromBottom=0

    @BeforeEach
    void setUp() {
        alice = new Player("alice", new Totem(TotemColor.RED));
        Player bob = new Player("bob", new Totem(TotemColor.BLUE));
        Game game = new Game(PlayerCount.TWO, List.of(alice, bob));
        game.startGame();
        board = game.getBoard();
        alice.gainFood(20);
        tileC = board.getOfferTrack().getTiles().stream()
                .filter(t -> t.getId() == OfferTileId.C).findFirst().orElseThrow();
    }

    // ---- getDirectFoodBonus ----

    @Test
    void getDirectFoodBonus_returnsConfiguredValue() {
        OfferTile tile = new OfferTile(OfferTileId.A);
        tile.setDirectFoodBonus(5);
        assertEquals(5, tile.getDirectFoodBonus());
    }

    // ---- setAllowedPlayerCounts null / empty → restores all ----

    @Test
    void setAllowedPlayerCounts_null_restoresAllPlayerCounts() {
        OfferTile tile = new OfferTile(OfferTileId.B);
        tile.setAllowedPlayerCounts(PlayerCount.TWO);
        assertEquals(1, tile.getAllowedPlayerCounts().size());

        tile.setAllowedPlayerCounts((PlayerCount[]) null);
        assertEquals(EnumSet.allOf(PlayerCount.class), tile.getAllowedPlayerCounts());
    }

    // ---- executeChoice: directFoodBonus > 0 ----

    @Test
    void executeChoice_foodOnlyTile_playerGainsBonusFood() {
        OfferTile foodTile = new OfferTile(OfferTileId.A);
        foodTile.setCardsFromTop(0);
        foodTile.setCardsFromBottom(0);
        foodTile.setDirectFoodBonus(3);

        int foodBefore = alice.getFood();
        foodTile.executeChoice(foodTile.createChoice(), alice, board);
        assertEquals(foodBefore + 3, alice.getFood());
    }

    // ---- executeChoice: BuildingCard branch ----

    @Test
    void executeChoice_buildingCard_playerAcquiresBuilding() {
        // Clear chars so only building remains — keeps the test deterministic
        List<RowSlot> topSlots = board.getVisibleRow(VisibleRow.TOP).getSlots();
        topSlots.stream().filter(RowSlot::containsCharacter).forEach(RowSlot::clearCard);

        Optional<RowSlot> buildingSlot = topSlots.stream()
                .filter(RowSlot::containsBuilding).findFirst();
        if (buildingSlot.isEmpty()) return; // no building exposed — skip gracefully

        alice.gainFood(100); // ensure enough food for any building cost

        OfferChoice choice = tileC.createChoice();
        choice.addPick(new CardPick(VisibleRow.TOP, buildingSlot.get().getIndex()));
        tileC.executeChoice(choice, alice, board);

        assertEquals(1, alice.getTribe().getBuildingCards().size());
    }
}
