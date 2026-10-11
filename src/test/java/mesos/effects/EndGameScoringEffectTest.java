package mesos.effects;

import mesos.cards.BuildingCard;
import mesos.cards.characters.*;
import mesos.enums.*;
import mesos.player.Player;
import mesos.player.Totem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EndGameScoringEffectTest {

    private Player alice;

    @BeforeEach
    void setUp() {
        alice = new Player("alice", new Totem(TotemColor.RED));
    }

    // ---- PER_CHARACTER_TYPE ----

    @Test
    void getEndGameBonus_PER_CHARACTER_TYPE_noCharacters_returnsZero() {
        EndGameScoringEffect effect = new EndGameScoringEffect(
                EndGameScoringType.PER_CHARACTER_TYPE, 3, CharacterType.HUNTER);
        assertEquals(0, effect.getEndGameBonus(alice));
    }

    @Test
    void getEndGameBonus_PER_CHARACTER_TYPE_scalesWithCount() {
        alice.getTribe().addCharacter(new Hunter("h1", Era.I, null, false));
        alice.getTribe().addCharacter(new Hunter("h2", Era.I, null, false));
        EndGameScoringEffect effect = new EndGameScoringEffect(
                EndGameScoringType.PER_CHARACTER_TYPE, 3, CharacterType.HUNTER);
        assertEquals(6, effect.getEndGameBonus(alice)); // 2 × 3
    }

    @Test
    void getEndGameBonus_PER_CHARACTER_TYPE_wrongType_returnsZero() {
        alice.getTribe().addCharacter(new Artist("a", Era.I, null));
        EndGameScoringEffect effect = new EndGameScoringEffect(
                EndGameScoringType.PER_CHARACTER_TYPE, 3, CharacterType.HUNTER);
        assertEquals(0, effect.getEndGameBonus(alice));
    }

    // ---- PER_COMPLETE_SET ----

    @Test
    void getEndGameBonus_PER_COMPLETE_SET_noSet_returnsZero() {
        EndGameScoringEffect effect = new EndGameScoringEffect(
                EndGameScoringType.PER_COMPLETE_SET, 6, null);
        assertEquals(0, effect.getEndGameBonus(alice));
    }

    @Test
    void getEndGameBonus_PER_COMPLETE_SET_oneSet() {
        alice.getTribe().addCharacter(new Hunter("h", Era.I, null, false));
        alice.getTribe().addCharacter(new Shaman("s", Era.I, null, 1));
        alice.getTribe().addCharacter(new Artist("a", Era.I, null));
        alice.getTribe().addCharacter(new Builder("b", Era.I, null, 1, 1));
        alice.getTribe().addCharacter(new Inventor("i", Era.I, null, InventionIcon.ICON_1));
        alice.getTribe().addCharacter(new Gatherer("g", Era.I, null));
        EndGameScoringEffect effect = new EndGameScoringEffect(
                EndGameScoringType.PER_COMPLETE_SET, 6, null);
        assertEquals(6, effect.getEndGameBonus(alice)); // 1 set × 6
    }

    // ---- DOUBLE_BUILDERS ----

    @Test
    void getEndGameBonus_DOUBLE_BUILDERS_noBuilders_returnsZero() {
        EndGameScoringEffect effect = new EndGameScoringEffect(
                EndGameScoringType.DOUBLE_BUILDERS, 0, null);
        assertEquals(0, effect.getEndGameBonus(alice));
    }

    @Test
    void getEndGameBonus_DOUBLE_BUILDERS_sumsBuilderPpValues() {
        alice.getTribe().addCharacter(new Builder("b1", Era.I, null, 2, 1));
        alice.getTribe().addCharacter(new Builder("b2", Era.I, null, 3, 1));
        EndGameScoringEffect effect = new EndGameScoringEffect(
                EndGameScoringType.DOUBLE_BUILDERS, 0, null);
        assertEquals(5, effect.getEndGameBonus(alice)); // 2+3=5
    }

    @Test
    void getEndGameBonus_DOUBLE_BUILDERS_nonBuilderIgnored() {
        alice.getTribe().addCharacter(new Hunter("h", Era.I, null, false));
        alice.getTribe().addCharacter(new Builder("b", Era.I, null, 4, 1));
        EndGameScoringEffect effect = new EndGameScoringEffect(
                EndGameScoringType.DOUBLE_BUILDERS, 0, null);
        assertEquals(4, effect.getEndGameBonus(alice)); // only builder counts
    }

    // ---- getDescription ----

    @Test
    void getDescription_returnsScoringTypeName() {
        EndGameScoringEffect effect = new EndGameScoringEffect(EndGameScoringType.PER_CHARACTER_TYPE, 3, CharacterType.HUNTER);
        assertEquals("PER_CHARACTER_TYPE", effect.getDescription());
    }

    // ---- via BuildingCard.getTotalPrestigeAtGameEnd ----

    @Test
    void buildingCard_totalPrestige_printedPlusBonus() {
        alice.getTribe().addCharacter(new Hunter("h", Era.I, null, false));
        EndGameScoringEffect effect = new EndGameScoringEffect(EndGameScoringType.PER_CHARACTER_TYPE, 5, CharacterType.HUNTER);
        BuildingCard card = new BuildingCard("scored", Era.II, 0, 3, effect);
        // 3 printed + 1 Hunter × 5 = 8
        assertEquals(8, card.getTotalPrestigeAtGameEnd(alice));
    }
}
