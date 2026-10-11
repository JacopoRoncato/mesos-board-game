package mesos.client.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import mesos.client.ui.model.GameSnapshot.TribeCard;
import mesos.client.ui.widget.CardComponent;
import mesos.enums.CharacterType;
import org.junit.jupiter.api.Test;

class TribeCountsTest {

    private static TribeCard ch(CharacterType t) {
        return new TribeCard(CardComponent.Kind.CHARACTER, t.name() + "-I-0", t);
    }

    private static TribeCard bld(String name) {
        return new TribeCard(CardComponent.Kind.BUILDING, name, null);
    }

    @Test
    void emptyTribeIsAllZero() {
        TribeCounts c = TribeCounts.of(Collections.emptyList());
        for (CharacterType t : CharacterType.values()) {
            assertEquals(0, c.characters(t));
        }
        assertEquals(0, c.buildings());
        assertEquals(0, c.totalCharacters());
    }

    @Test
    void nullTribeIsAllZero() {
        TribeCounts c = TribeCounts.of(null);
        assertEquals(0, c.totalCharacters());
        assertEquals(0, c.buildings());
    }

    @Test
    void countsCharactersByTypeAndBuildings() {
        List<TribeCard> tribe = Arrays.asList(
                ch(CharacterType.HUNTER), ch(CharacterType.HUNTER),
                ch(CharacterType.SHAMAN),
                bld("Pair Food"), bld("Set Food"));
        TribeCounts c = TribeCounts.of(tribe);
        assertEquals(2, c.characters(CharacterType.HUNTER));
        assertEquals(1, c.characters(CharacterType.SHAMAN));
        assertEquals(0, c.characters(CharacterType.ARTIST));
        assertEquals(2, c.buildings());
        assertEquals(3, c.totalCharacters());
    }

    @Test
    void buildingWithNullTypeNotCountedAsCharacter() {
        TribeCounts c = TribeCounts.of(Collections.singletonList(bld("Hunt Bonus")));
        assertEquals(0, c.totalCharacters());
        assertEquals(1, c.buildings());
    }
}
