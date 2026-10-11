package mesos.board;

import mesos.cards.BuildingCard;
import mesos.cards.characters.Hunter;
import mesos.cards.events.SustenanceEvent;
import mesos.effects.NoBuildingEffect;
import mesos.enums.Era;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VisibleRowStateTest {

    private VisibleRowState row;

    @BeforeEach
    void setUp() {
        row = new VisibleRowState();
    }

    // ---- appendCard(null) ----

    @Test
    void appendCard_null_doesNothing() {
        row.appendCard(null);
        assertTrue(row.getSlots().isEmpty());
    }

    // ---- removeCardAt — slot not found ----

    @Test
    void removeCardAt_nonExistentIndex_returnsNull() {
        assertNull(row.removeCardAt(9999));
    }

    // ---- removeBuildings ----

    @Test
    void removeBuildings_removesOnlyBuildingSlots() {
        Hunter hunter = new Hunter("h", Era.I, null, false);
        BuildingCard building = new BuildingCard("b", Era.I, 2, 1, new NoBuildingEffect());
        row.appendCard(hunter);
        row.appendCard(building);

        row.removeBuildings();

        assertTrue(row.getSlots().stream().noneMatch(RowSlot::containsBuilding));
        assertTrue(row.getSlots().stream().anyMatch(RowSlot::containsCharacter));
    }

    // ---- removeCharactersAndEvents — event-only slot (right side of OR) ----

    @Test
    void removeCharactersAndEvents_withEventOnlySlot_clearsSlot() {
        SustenanceEvent event = new SustenanceEvent("e", Era.I, null, 2);
        row.appendCard(event);

        row.removeCharactersAndEvents();

        assertTrue(row.getSlots().stream().allMatch(RowSlot::isEmpty));
    }
}
