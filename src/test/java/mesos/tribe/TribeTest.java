package mesos.tribe;

import mesos.cards.BuildingCard;
import mesos.cards.characters.*;
import mesos.effects.*;
import mesos.enums.*;
import mesos.player.Player;
import mesos.player.Totem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests both Tribe (public API) and CharacterStats (package-private, same package).
 */
class TribeTest {

    private Player owner;
    private Tribe tribe;

    @BeforeEach
    void setUp() {
        owner = new Player("p", new Totem(TotemColor.RED));
        tribe = owner.getTribe();
    }

    // ---- CharacterStats.countByType (via Tribe.countCharacters) ----

    @Test
    void countCharacters_emptyTribe_returnsZero() {
        assertEquals(0, tribe.countCharacters(CharacterType.HUNTER));
    }

    @Test
    void countCharacters_onlyTargetType() {
        tribe.addCharacter(new Hunter("h", Era.I, null, false));
        tribe.addCharacter(new Hunter("h2", Era.I, null, false));
        assertEquals(2, tribe.countCharacters(CharacterType.HUNTER));
        assertEquals(0, tribe.countCharacters(CharacterType.ARTIST));
    }

    @Test
    void countCharacters_mixedTypes() {
        tribe.addCharacter(new Hunter("h", Era.I, null, false));
        tribe.addCharacter(new Artist("a", Era.I, null));
        tribe.addCharacter(new Artist("a2", Era.I, null));
        assertEquals(1, tribe.countCharacters(CharacterType.HUNTER));
        assertEquals(2, tribe.countCharacters(CharacterType.ARTIST));
        assertEquals(0, tribe.countCharacters(CharacterType.SHAMAN));
    }

    // ---- CharacterStats.buildingDiscount (via Tribe.calculateBuildingDiscount) ----

    @Test
    void calculateBuildingDiscount_noBuilders_returnsZero() {
        tribe.addCharacter(new Artist("a", Era.I, null));
        assertEquals(0, tribe.calculateBuildingDiscount());
    }

    @Test
    void calculateBuildingDiscount_sumsBuilderDiscounts() {
        tribe.addCharacter(new Builder("b1", Era.I, null, 1, 2)); // discount 2
        tribe.addCharacter(new Builder("b2", Era.I, null, 1, 3)); // discount 3
        assertEquals(5, tribe.calculateBuildingDiscount());
    }

    // ---- CharacterStats.sustenanceDiscount (via Tribe.calculateSustenanceDiscount) ----

    @Test
    void calculateSustenanceDiscount_noGatherers_returnsZero() {
        tribe.addCharacter(new Hunter("h", Era.I, null, false));
        assertEquals(0, tribe.calculateSustenanceDiscount());
    }

    @Test
    void calculateSustenanceDiscount_gathererProvidesDiscount() {
        tribe.addCharacter(new Gatherer("g", Era.I, null));
        assertTrue(tribe.calculateSustenanceDiscount() > 0);
    }

    @Test
    void calculateSustenanceDiscount_multipleGatherers_sumsDiscounts() {
        tribe.addCharacter(new Gatherer("g1", Era.I, null));
        tribe.addCharacter(new Gatherer("g2", Era.I, null));
        int single = new Gatherer("g", Era.I, null).getBaseSustenanceDiscount();
        assertEquals(single * 2, tribe.calculateSustenanceDiscount());
    }

    // ---- CharacterStats.totalShamanIcons (via Tribe.getTotalShamanIcons) ----

    @Test
    void getTotalShamanIcons_noShamans_returnsZero() {
        assertEquals(0, tribe.getTotalShamanIcons());
    }

    @Test
    void getTotalShamanIcons_sumsShamanIcons() {
        tribe.addCharacter(new Shaman("s1", Era.I, null, 2));
        tribe.addCharacter(new Shaman("s2", Era.I, null, 3));
        assertEquals(5, tribe.getTotalShamanIcons());
    }

    // ---- CharacterStats.distinctInventionIcons (via Tribe.countDistinctInventionIcons) ----

    @Test
    void countDistinctInventionIcons_noInventors_returnsZero() {
        assertEquals(0, tribe.countDistinctInventionIcons());
    }

    @Test
    void countDistinctInventionIcons_allSameIcon_returnsOne() {
        tribe.addCharacter(new Inventor("i1", Era.I, null, InventionIcon.ICON_1));
        tribe.addCharacter(new Inventor("i2", Era.I, null, InventionIcon.ICON_1));
        assertEquals(1, tribe.countDistinctInventionIcons());
    }

    @Test
    void countDistinctInventionIcons_differentIcons_returnsCount() {
        tribe.addCharacter(new Inventor("i1", Era.I, null, InventionIcon.ICON_1));
        tribe.addCharacter(new Inventor("i2", Era.I, null, InventionIcon.ICON_2));
        tribe.addCharacter(new Inventor("i3", Era.I, null, InventionIcon.ICON_3));
        assertEquals(3, tribe.countDistinctInventionIcons());
    }

    // ---- CharacterStats.completeCharacterSets (via Tribe.countCompleteCharacterSets) ----

    @Test
    void countCompleteCharacterSets_emptyTribe_returnsZero() {
        assertEquals(0, tribe.countCompleteCharacterSets());
    }

    @Test
    void countCompleteCharacterSets_missingType_returnsZero() {
        tribe.addCharacter(new Hunter("h", Era.I, null, false));
        tribe.addCharacter(new Artist("a", Era.I, null));
        assertEquals(0, tribe.countCompleteCharacterSets()); // missing 4 types
    }

    @Test
    void countCompleteCharacterSets_oneOfEach_returnsOne() {
        tribe.addCharacter(new Hunter("h", Era.I, null, false));
        tribe.addCharacter(new Shaman("s", Era.I, null, 1));
        tribe.addCharacter(new Artist("a", Era.I, null));
        tribe.addCharacter(new Builder("b", Era.I, null, 1, 1));
        tribe.addCharacter(new Inventor("i", Era.I, null, InventionIcon.ICON_1));
        tribe.addCharacter(new Gatherer("g", Era.I, null));
        assertEquals(1, tribe.countCompleteCharacterSets());
    }

    @Test
    void countCompleteCharacterSets_limitedByMinimumType() {
        // 2 hunters but only 1 of every other type → 1 complete set
        tribe.addCharacter(new Hunter("h1", Era.I, null, false));
        tribe.addCharacter(new Hunter("h2", Era.I, null, false));
        tribe.addCharacter(new Shaman("s", Era.I, null, 1));
        tribe.addCharacter(new Artist("a", Era.I, null));
        tribe.addCharacter(new Builder("b", Era.I, null, 1, 1));
        tribe.addCharacter(new Inventor("i", Era.I, null, InventionIcon.ICON_1));
        tribe.addCharacter(new Gatherer("g", Era.I, null));
        assertEquals(1, tribe.countCompleteCharacterSets());
    }

    // ---- CharacterStats.inventorPairs (via Tribe.countInventorPairs) ----

    @Test
    void countInventorPairs_noInventors_returnsZero() {
        assertEquals(0, tribe.countInventorPairs());
    }

    @Test
    void countInventorPairs_oneInventor_returnsZero() {
        tribe.addCharacter(new Inventor("i", Era.I, null, InventionIcon.ICON_1));
        assertEquals(0, tribe.countInventorPairs());
    }

    @Test
    void countInventorPairs_twoDifferentIcons_returnsZero() {
        tribe.addCharacter(new Inventor("i1", Era.I, null, InventionIcon.ICON_1));
        tribe.addCharacter(new Inventor("i2", Era.I, null, InventionIcon.ICON_2));
        assertEquals(0, tribe.countInventorPairs());
    }

    @Test
    void countInventorPairs_twoSameIcon_returnsOne() {
        tribe.addCharacter(new Inventor("i1", Era.I, null, InventionIcon.ICON_1));
        tribe.addCharacter(new Inventor("i2", Era.I, null, InventionIcon.ICON_1));
        assertEquals(1, tribe.countInventorPairs());
    }

    @Test
    void countInventorPairs_fourSameIcon_returnsTwo() {
        for (int i = 0; i < 4; i++) {
            tribe.addCharacter(new Inventor("i" + i, Era.I, null, InventionIcon.ICON_3));
        }
        assertEquals(2, tribe.countInventorPairs());
    }

    @Test
    void countInventorPairs_mixedIcons() {
        tribe.addCharacter(new Inventor("i1", Era.I, null, InventionIcon.ICON_1));
        tribe.addCharacter(new Inventor("i2", Era.I, null, InventionIcon.ICON_1)); // pair
        tribe.addCharacter(new Inventor("i3", Era.I, null, InventionIcon.ICON_2));
        assertEquals(1, tribe.countInventorPairs());
    }

    // ---- CharacterStats.artistPairs (via Tribe.countArtistPairs) ----

    @Test
    void countArtistPairs_noArtists_returnsZero() {
        assertEquals(0, tribe.countArtistPairs());
    }

    @Test
    void countArtistPairs_oneArtist_returnsZero() {
        tribe.addCharacter(new Artist("a", Era.I, null));
        assertEquals(0, tribe.countArtistPairs());
    }

    @Test
    void countArtistPairs_twoArtists_returnsOne() {
        tribe.addCharacter(new Artist("a1", Era.I, null));
        tribe.addCharacter(new Artist("a2", Era.I, null));
        assertEquals(1, tribe.countArtistPairs());
    }

    @Test
    void countArtistPairs_threeArtists_returnsOne() {
        tribe.addCharacter(new Artist("a1", Era.I, null));
        tribe.addCharacter(new Artist("a2", Era.I, null));
        tribe.addCharacter(new Artist("a3", Era.I, null));
        assertEquals(1, tribe.countArtistPairs());
    }

    @Test
    void countArtistPairs_fourArtists_returnsTwo() {
        for (int i = 0; i < 4; i++) tribe.addCharacter(new Artist("a" + i, Era.I, null));
        assertEquals(2, tribe.countArtistPairs());
    }

    // ---- findExtraTopRowPickEffect ----

    @Test
    void findExtraTopRowPickEffect_noBuildings_returnsNull() {
        assertNull(tribe.findExtraTopRowPickEffect());
    }

    @Test
    void findExtraTopRowPickEffect_buildingWithoutEffect_returnsNull() {
        owner.gainFood(2);
        tribe.addBuilding(new BuildingCard("plain", Era.I, 0, 1, new NoBuildingEffect()));
        assertNull(tribe.findExtraTopRowPickEffect());
    }

    @Test
    void findExtraTopRowPickEffect_buildingWithEffect_returnsEffect() {
        ExtraTopRowPickEffect effect = new ExtraTopRowPickEffect();
        tribe.addBuilding(new BuildingCard("extra", Era.I, 0, 4, effect));
        assertNotNull(tribe.findExtraTopRowPickEffect());
    }

    // ---- SustenanceDiscountEffect via Tribe ----

    @Test
    void getExtraSustenanceDiscount_buildingWithEffect_returnsDiscount() {
        tribe.addCharacter(new Artist("a1", Era.I, null));
        tribe.addCharacter(new Artist("a2", Era.I, null));
        // building gives 1 discount per ARTIST
        SustenanceDiscountEffect effect = new SustenanceDiscountEffect(CharacterType.ARTIST, 1);
        tribe.addBuilding(new BuildingCard("disc", Era.I, 0, 1, effect));
        assertEquals(2, tribe.getExtraSustenanceDiscount(owner)); // 2 artists × 1
    }

    // ---- Direct CharacterStats access (same package) ----

    @Test
    void characterStats_countByType_directCall() {
        List<CharacterCard> cards = new ArrayList<>();
        cards.add(new Hunter("h", Era.I, null, false));
        cards.add(new Artist("a", Era.I, null));
        assertEquals(1, CharacterStats.countByType(cards, CharacterType.HUNTER));
        assertEquals(0, CharacterStats.countByType(cards, CharacterType.SHAMAN));
    }

    @Test
    void characterStats_buildingDiscount_directCall() {
        List<CharacterCard> cards = new ArrayList<>();
        cards.add(new Builder("b", Era.I, null, 1, 3));
        assertEquals(3, CharacterStats.buildingDiscount(cards));
    }

    @Test
    void characterStats_sustenanceDiscount_emptyList() {
        assertEquals(0, CharacterStats.sustenanceDiscount(new ArrayList<>()));
    }

    @Test
    void characterStats_totalShamanIcons_empty() {
        assertEquals(0, CharacterStats.totalShamanIcons(new ArrayList<>()));
    }

    @Test
    void characterStats_distinctInventionIcons_empty() {
        assertEquals(0, CharacterStats.distinctInventionIcons(new ArrayList<>()));
    }

    @Test
    void characterStats_completeCharacterSets_emptyList() {
        assertEquals(0, CharacterStats.completeCharacterSets(new ArrayList<>()));
    }

    @Test
    void characterStats_inventorPairs_emptyList() {
        assertEquals(0, CharacterStats.inventorPairs(new ArrayList<>()));
    }

    @Test
    void characterStats_artistPairs_oddCount() {
        List<CharacterCard> cards = new ArrayList<>();
        cards.add(new Artist("a1", Era.I, null));
        cards.add(new Artist("a2", Era.I, null));
        cards.add(new Artist("a3", Era.I, null));
        assertEquals(1, CharacterStats.artistPairs(cards));
    }
}
