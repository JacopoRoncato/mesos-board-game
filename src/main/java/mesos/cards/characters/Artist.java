package mesos.cards.characters;

import java.util.Set;
import mesos.enums.CharacterType;
import mesos.enums.Era;
import mesos.enums.PlayerCount;
import mesos.player.Player;

/** An Artist character card. Scored in pairs during cave-paintings events. */
public class Artist extends CharacterCard {
    private static final long serialVersionUID = 1L;

    public Artist(String name, Era era, Set<PlayerCount> allowedPlayerCounts) {
        super(name, era, allowedPlayerCounts);
    }

    @Override
    public void onPurchase(Player player) {
    }

    @Override
    public CharacterType getType() {
        return CharacterType.ARTIST;
    }
}
