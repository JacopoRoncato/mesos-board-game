package mesos.cards.characters;

import mesos.enums.*;
import mesos.player.Player;
import mesos.player.Totem;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ArtistTest {

    private Artist makeArtist() {
        return new Artist("TestArtist", Era.I,
                Set.of(PlayerCount.TWO, PlayerCount.THREE, PlayerCount.FOUR, PlayerCount.FIVE));
    }

    @Test
    void getType_returnsArtist() {
        assertEquals(CharacterType.ARTIST, makeArtist().getType());
    }

    @Test
    void onPurchase_doesNotThrow() {
        Player alice = new Player("alice", new Totem(TotemColor.RED));
        assertDoesNotThrow(() -> makeArtist().onPurchase(alice));
    }

    @Test
    void getBaseSustenanceDiscount_returnsZero() {
        assertEquals(0, makeArtist().getBaseSustenanceDiscount());
    }

    @Test
    void getShamanIcons_returnsZero() {
        assertEquals(0, makeArtist().getShamanIcons());
    }

    @Test
    void getName_returnsConfiguredName() {
        assertEquals("TestArtist", makeArtist().getName());
    }

    @Test
    void getEra_returnsConfiguredEra() {
        assertEquals(Era.I, makeArtist().getEra());
    }
}
