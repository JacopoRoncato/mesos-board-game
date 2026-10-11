package mesos.board;

import mesos.cards.events.*;
import mesos.enums.*;
import mesos.game.Game;
import mesos.player.Player;
import mesos.player.Totem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoardTest {

    private static Player p(String id, TotemColor color) {
        return new Player(id, new Totem(color));
    }

    // ---- buildingCountsFor THREE / FOUR / FIVE ----

    @Test
    void startGame_threePlayers_boardSetUp() {
        Game game = new Game(PlayerCount.THREE,
                List.of(p("a", TotemColor.RED), p("b", TotemColor.BLUE), p("c", TotemColor.WHITE)));
        game.startGame();
        assertNotNull(game.getBoard());
    }

    @Test
    void startGame_fourPlayers_boardSetUp() {
        Game game = new Game(PlayerCount.FOUR,
                List.of(p("a", TotemColor.RED), p("b", TotemColor.BLUE),
                        p("c", TotemColor.WHITE), p("d", TotemColor.YELLOW)));
        game.startGame();
        assertNotNull(game.getBoard());
    }

    @Test
    void startGame_fivePlayers_boardSetUp() {
        Game game = new Game(PlayerCount.FIVE,
                List.of(p("a", TotemColor.RED), p("b", TotemColor.BLUE),
                        p("c", TotemColor.WHITE), p("d", TotemColor.YELLOW),
                        p("e", TotemColor.PURPLE)));
        game.startGame();
        assertNotNull(game.getBoard());
    }

    // ---- sortEventsForResolution — covers all 4 eventPriority cases ----

    @Test
    void sortEventsForResolution_ordersAllFourTypes() {
        Game game = new Game(PlayerCount.TWO,
                List.of(p("a", TotemColor.RED), p("b", TotemColor.BLUE)));
        game.startGame();
        Board board = game.getBoard();

        List<EventCard> events = List.of(
                new SustenanceEvent("S", Era.I, null, 2),
                new CavePaintingsEvent("C", Era.I, null, 2, 3, 2),
                new ShamanicRitualEvent("R", Era.I, null, 1, 1),
                new HuntingEvent("H", Era.I, null, 1)
        );

        List<EventCard> sorted = board.sortEventsForResolution(events);

        assertEquals(EventType.HUNTING,         sorted.get(0).getEventType());
        assertEquals(EventType.SHAMANIC_RITUAL,  sorted.get(1).getEventType());
        assertEquals(EventType.CAVE_PAINTINGS,   sorted.get(2).getEventType());
        assertEquals(EventType.SUSTENANCE,       sorted.get(3).getEventType());
    }

    // ---- applyEraTransition(Era.III) — covers Era.III branch + discardBottomBuildingsForEraIII ----

    @Test
    void applyEraTransition_eraIII_doesNotThrow() {
        Game game = new Game(PlayerCount.TWO,
                List.of(p("a", TotemColor.RED), p("b", TotemColor.BLUE)));
        game.startGame();
        assertDoesNotThrow(() -> game.getBoard().applyEraTransition(Era.III));
    }

    @Test
    void discardBottomBuildingsForEraIII_doesNotThrow() {
        Game game = new Game(PlayerCount.TWO,
                List.of(p("a", TotemColor.RED), p("b", TotemColor.BLUE)));
        game.startGame();
        assertDoesNotThrow(() -> game.getBoard().discardBottomBuildingsForEraIII());
    }
}
