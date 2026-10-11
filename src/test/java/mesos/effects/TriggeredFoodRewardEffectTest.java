package mesos.effects;

import mesos.cards.BuildingCard;
import mesos.cards.characters.*;
import mesos.enums.*;
import mesos.player.Player;
import mesos.player.Totem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TriggeredFoodRewardEffectTest {

    private Player alice;

    @BeforeEach
    void setUp() {
        alice = new Player("alice", new Totem(TotemColor.RED));
    }

    // Helper: acquires a building with the given effect at 0 cost
    private void acquireFree(TriggeredFoodRewardEffect effect) {
        BuildingCard card = new BuildingCard("trigger", Era.I, 0, 1, effect);
        alice.acquireBuilding(card); // calls onAcquire → snapshots
    }

    // ---- COMPLETE_SET trigger ----

    @Test
    void completeSets_newSetAfterAcquire_givesFood() {
        // 5 of 6 types present before acquiring building
        alice.getTribe().addCharacter(new Hunter("h", Era.I, null, false));
        alice.getTribe().addCharacter(new Shaman("s", Era.I, null, 1));
        alice.getTribe().addCharacter(new Artist("a", Era.I, null));
        alice.getTribe().addCharacter(new Builder("b", Era.I, null, 1, 1));
        alice.getTribe().addCharacter(new Inventor("i", Era.I, null, InventionIcon.ICON_1));
        // countCompleteCharacterSets = 0 (missing Gatherer)
        TriggeredFoodRewardEffect effect =
                new TriggeredFoodRewardEffect(TriggeredFoodRewardType.COMPLETE_SET, 5);
        acquireFree(effect); // snapshot = 0
        assertEquals(0, alice.getFood()); // no food yet

        // Adding 6th type completes the set → new occurrence = 1
        alice.acquireCharacter(new Gatherer("g", Era.I, null));
        assertEquals(5, alice.getFood()); // 1 new set × 5 food
    }

    @Test
    void completeSets_noNewSet_noFood() {
        // Already have 1 complete set
        alice.getTribe().addCharacter(new Hunter("h", Era.I, null, false));
        alice.getTribe().addCharacter(new Shaman("s", Era.I, null, 1));
        alice.getTribe().addCharacter(new Artist("a", Era.I, null));
        alice.getTribe().addCharacter(new Builder("b", Era.I, null, 1, 1));
        alice.getTribe().addCharacter(new Inventor("i", Era.I, null, InventionIcon.ICON_1));
        alice.getTribe().addCharacter(new Gatherer("g", Era.I, null));
        // countCompleteCharacterSets = 1
        TriggeredFoodRewardEffect effect =
                new TriggeredFoodRewardEffect(TriggeredFoodRewardType.COMPLETE_SET, 5);
        acquireFree(effect); // snapshot = 1

        // Adding another Hunter doesn't create a new complete set (still limited by min)
        alice.acquireCharacter(new Hunter("h2", Era.I, null, false));
        assertEquals(0, alice.getFood()); // no new sets
    }

    @Test
    void completeSets_notRetroactive_existingSetNotRewarded() {
        // Complete set already exists BEFORE acquiring building
        alice.getTribe().addCharacter(new Hunter("h", Era.I, null, false));
        alice.getTribe().addCharacter(new Shaman("s", Era.I, null, 1));
        alice.getTribe().addCharacter(new Artist("a", Era.I, null));
        alice.getTribe().addCharacter(new Builder("b", Era.I, null, 1, 1));
        alice.getTribe().addCharacter(new Inventor("i", Era.I, null, InventionIcon.ICON_1));
        alice.getTribe().addCharacter(new Gatherer("g", Era.I, null));
        // snapshot = 1 at acquire time → no retroactive reward
        TriggeredFoodRewardEffect effect =
                new TriggeredFoodRewardEffect(TriggeredFoodRewardType.COMPLETE_SET, 5);
        acquireFree(effect);
        assertEquals(0, alice.getFood()); // existing set not rewarded
    }

    // ---- MATCHING_INVENTOR_PAIR trigger ----

    @Test
    void inventorPairs_newPairAfterAcquire_givesFood() {
        alice.getTribe().addCharacter(new Inventor("i1", Era.I, null, InventionIcon.ICON_1));
        // 0 pairs before building
        TriggeredFoodRewardEffect effect =
                new TriggeredFoodRewardEffect(TriggeredFoodRewardType.MATCHING_INVENTOR_PAIR, 3);
        acquireFree(effect); // snapshot = 0

        alice.acquireCharacter(new Inventor("i2", Era.I, null, InventionIcon.ICON_1));
        assertEquals(3, alice.getFood()); // 1 new pair × 3
    }

    @Test
    void inventorPairs_differentIcon_noPair_noFood() {
        alice.getTribe().addCharacter(new Inventor("i1", Era.I, null, InventionIcon.ICON_1));
        TriggeredFoodRewardEffect effect =
                new TriggeredFoodRewardEffect(TriggeredFoodRewardType.MATCHING_INVENTOR_PAIR, 3);
        acquireFree(effect); // snapshot = 0

        alice.acquireCharacter(new Inventor("i2", Era.I, null, InventionIcon.ICON_2));
        assertEquals(0, alice.getFood()); // different icons → no pair
    }

    @Test
    void inventorPairs_notRetroactive_existingPairsNotRewarded() {
        alice.getTribe().addCharacter(new Inventor("i1", Era.I, null, InventionIcon.ICON_1));
        alice.getTribe().addCharacter(new Inventor("i2", Era.I, null, InventionIcon.ICON_1));
        // 1 pair already exists
        TriggeredFoodRewardEffect effect =
                new TriggeredFoodRewardEffect(TriggeredFoodRewardType.MATCHING_INVENTOR_PAIR, 3);
        acquireFree(effect); // snapshot = 1
        assertEquals(0, alice.getFood()); // not rewarded retroactively
    }

    @Test
    void getDescription_completeSets_returnsTypeName() {
        TriggeredFoodRewardEffect effect =
                new TriggeredFoodRewardEffect(TriggeredFoodRewardType.COMPLETE_SET, 5);
        assertEquals("COMPLETE_SET", effect.getDescription());
    }

    @Test
    void getDescription_inventorPair_returnsTypeName() {
        TriggeredFoodRewardEffect effect =
                new TriggeredFoodRewardEffect(TriggeredFoodRewardType.MATCHING_INVENTOR_PAIR, 3);
        assertEquals("MATCHING_INVENTOR_PAIR", effect.getDescription());
    }

    @Test
    void multipleTriggers_eachNewPairRewarded() {
        TriggeredFoodRewardEffect effect =
                new TriggeredFoodRewardEffect(TriggeredFoodRewardType.MATCHING_INVENTOR_PAIR, 2);
        acquireFree(effect); // snapshot = 0

        alice.acquireCharacter(new Inventor("i1", Era.I, null, InventionIcon.ICON_1));
        assertEquals(0, alice.getFood()); // 1 inventor, no pair yet

        alice.acquireCharacter(new Inventor("i2", Era.I, null, InventionIcon.ICON_1));
        assertEquals(2, alice.getFood()); // pair formed → 1 × 2

        alice.acquireCharacter(new Inventor("i3", Era.I, null, InventionIcon.ICON_1));
        assertEquals(2, alice.getFood()); // 3rd inventor, count stays at 1 pair (3/2=1)

        alice.acquireCharacter(new Inventor("i4", Era.I, null, InventionIcon.ICON_1));
        assertEquals(4, alice.getFood()); // 2nd pair formed → +2
    }
}
