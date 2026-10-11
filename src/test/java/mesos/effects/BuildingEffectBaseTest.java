package mesos.effects;

import mesos.board.Board;
import mesos.cards.characters.Hunter;
import mesos.enums.Era;
import mesos.enums.PlayerCount;
import mesos.enums.TotemColor;
import mesos.game.Game;
import mesos.order.TurnOrderSpace;
import mesos.player.Player;
import mesos.player.Totem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildingEffectBaseTest {

    private NoBuildingEffect effect;
    private Player alice;
    private Board board;

    @BeforeEach
    void setUp() {
        effect = new NoBuildingEffect();
        alice = new Player("alice", new Totem(TotemColor.RED));
        Player bob = new Player("bob", new Totem(TotemColor.BLUE));
        Game game = new Game(PlayerCount.TWO, List.of(alice, bob));
        game.startGame();
        board = game.getBoard();
    }

    @Test
    void onCharacterAdded_defaultIsNoOp() {
        assertDoesNotThrow(() ->
            effect.onCharacterAdded(alice, new Hunter("h", Era.I, null, false)));
    }

    @Test
    void onTurnOrderPlaced_defaultIsNoOp() {
        assertDoesNotThrow(() ->
            effect.onTurnOrderPlaced(alice, new TurnOrderSpace(0, 0, false)));
    }

    @Test
    void onEndOfActionPhase_defaultIsNoOp() {
        assertDoesNotThrow(() -> effect.onEndOfActionPhase(alice, board));
    }

    @Test
    void getExtraSustenanceDiscount_defaultReturnsZero() {
        assertEquals(0, effect.getExtraSustenanceDiscount(alice));
    }

    @Test
    void getAdditionalShamanIcons_defaultReturnsZero() {
        assertEquals(0, effect.getAdditionalShamanIcons(alice));
    }

    @Test
    void ignoresShamanicPenalty_defaultReturnsFalse() {
        assertFalse(effect.ignoresShamanicPenalty(alice));
    }

    @Test
    void doublesShamanicReward_defaultReturnsFalse() {
        assertFalse(effect.doublesShamanicReward(alice));
    }

    @Test
    void getDescription_defaultReturnsEmptyString() {
        assertEquals("", effect.getDescription());
    }
}