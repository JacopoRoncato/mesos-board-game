package mesos.game;

import mesos.cards.BuildingCard;
import mesos.cards.characters.*;
import mesos.effects.EndGameScoringEffect;
import mesos.effects.NoBuildingEffect;
import mesos.enums.*;
import mesos.player.Player;
import mesos.player.Totem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests ScoreCalculator (package-private) directly — same package.
 */
class ScoreCalculatorTest {

    private Player p(String id) {
        return new Player(id, new Totem(TotemColor.RED));
    }

    @Test
    void awardFinalPrestige_emptyTribe_noChange() {
        Player alice = p("alice");
        ScoreCalculator.awardFinalPrestige(List.of(alice));
        assertEquals(0, alice.getPrestigePoints());
    }

    // ---- inventors × distinct icons ----

    @Test
    void awardFinalPrestige_oneInventorOneIcon_addsOne() {
        Player alice = p("alice");
        alice.getTribe().addCharacter(new Inventor("i", Era.I, null, InventionIcon.ICON_1));
        ScoreCalculator.awardFinalPrestige(List.of(alice));
        // 1 inventor × 1 distinct icon = 1 PP
        assertEquals(1, alice.getPrestigePoints());
    }

    @Test
    void awardFinalPrestige_twoInventorsTwoIcons_addsFour() {
        Player alice = p("alice");
        alice.getTribe().addCharacter(new Inventor("i1", Era.I, null, InventionIcon.ICON_1));
        alice.getTribe().addCharacter(new Inventor("i2", Era.I, null, InventionIcon.ICON_2));
        ScoreCalculator.awardFinalPrestige(List.of(alice));
        // 2 inventors × 2 distinct icons = 4 PP
        assertEquals(4, alice.getPrestigePoints());
    }

    @Test
    void awardFinalPrestige_twoInventorsSameIcon_addsTwoPP() {
        Player alice = p("alice");
        alice.getTribe().addCharacter(new Inventor("i1", Era.I, null, InventionIcon.ICON_1));
        alice.getTribe().addCharacter(new Inventor("i2", Era.I, null, InventionIcon.ICON_1));
        ScoreCalculator.awardFinalPrestige(List.of(alice));
        // 2 inventors × 1 distinct icon = 2 PP
        assertEquals(2, alice.getPrestigePoints());
    }

    @Test
    void awardFinalPrestige_noInventors_zeroFromInventorRule() {
        Player alice = p("alice");
        alice.getTribe().addCharacter(new Hunter("h", Era.I, null, false));
        ScoreCalculator.awardFinalPrestige(List.of(alice));
        assertEquals(0, alice.getPrestigePoints()); // no inventor PP
    }

    // ---- artist pairs × 10 ----

    @Test
    void awardFinalPrestige_twoArtists_addsTenPP() {
        Player alice = p("alice");
        alice.getTribe().addCharacter(new Artist("a1", Era.I, null));
        alice.getTribe().addCharacter(new Artist("a2", Era.I, null));
        ScoreCalculator.awardFinalPrestige(List.of(alice));
        assertEquals(10, alice.getPrestigePoints()); // 1 pair × 10
    }

    @Test
    void awardFinalPrestige_fourArtists_addsTwentyPP() {
        Player alice = p("alice");
        for (int i = 0; i < 4; i++) alice.getTribe().addCharacter(new Artist("a" + i, Era.I, null));
        ScoreCalculator.awardFinalPrestige(List.of(alice));
        assertEquals(20, alice.getPrestigePoints()); // 2 pairs × 10
    }

    @Test
    void awardFinalPrestige_oneArtist_zeroPP() {
        Player alice = p("alice");
        alice.getTribe().addCharacter(new Artist("a", Era.I, null));
        ScoreCalculator.awardFinalPrestige(List.of(alice));
        assertEquals(0, alice.getPrestigePoints()); // 0 pairs
    }

    // ---- builder ppValues ----

    @Test
    void awardFinalPrestige_twoBuilders_sumsValues() {
        Player alice = p("alice");
        alice.getTribe().addCharacter(new Builder("b1", Era.I, null, 2, 1));
        alice.getTribe().addCharacter(new Builder("b2", Era.I, null, 3, 1));
        ScoreCalculator.awardFinalPrestige(List.of(alice));
        // 2+3=5 builder prestige (+ 0 from inventors and artists)
        assertEquals(5, alice.getPrestigePoints());
    }

    // ---- building end-game bonus ----

    @Test
    void awardFinalPrestige_buildingWithPrintedPP_adds() {
        Player alice = p("alice");
        // printed 2 PP, no dynamic bonus
        alice.getTribe().addBuilding(new BuildingCard("plain", Era.I, 0, 2, new NoBuildingEffect()));
        ScoreCalculator.awardFinalPrestige(List.of(alice));
        assertEquals(2, alice.getPrestigePoints()); // 2 printed
    }

    @Test
    void awardFinalPrestige_buildingWithEndGameEffect_addsBonus() {
        Player alice = p("alice");
        alice.getTribe().addCharacter(new Hunter("h1", Era.I, null, false));
        alice.getTribe().addCharacter(new Hunter("h2", Era.I, null, false));
        // building: 3 PP per HUNTER + 1 printed
        EndGameScoringEffect effect = new EndGameScoringEffect(
                EndGameScoringType.PER_CHARACTER_TYPE, 3, CharacterType.HUNTER);
        alice.getTribe().addBuilding(new BuildingCard("scored", Era.II, 0, 1, effect));
        ScoreCalculator.awardFinalPrestige(List.of(alice));
        // 1 printed + (2 hunters × 3) = 1 + 6 = 7
        assertEquals(7, alice.getPrestigePoints());
    }

    // ---- multiple players ----

    @Test
    void awardFinalPrestige_multiplePlayers_independentScores() {
        Player alice = p("alice");
        Player bob   = new Player("bob", new Totem(TotemColor.BLUE));
        alice.getTribe().addCharacter(new Artist("a1", Era.I, null));
        alice.getTribe().addCharacter(new Artist("a2", Era.I, null)); // 1 pair → 10 PP
        // bob: no characters
        ScoreCalculator.awardFinalPrestige(List.of(alice, bob));
        assertEquals(10, alice.getPrestigePoints());
        assertEquals(0,  bob.getPrestigePoints());
    }
}
