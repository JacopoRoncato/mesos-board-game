package mesos.cards.characters;

import java.util.Set;
import mesos.enums.CharacterType;
import mesos.enums.Era;
import mesos.enums.PlayerCount;
import mesos.player.Player;

/** A Shaman character card. Contributes shaman icons used in the shamanic ritual event. */
public class Shaman extends CharacterCard {
    private static final long serialVersionUID = 1L;
    private final int shamanIcons;

    /**
     * Creates a Shaman card.
     *
     * @param shamanIcons the number of shaman icons on this card (typically 1 or 2)
     */
    public Shaman(String name, Era era, Set<PlayerCount> allowedPlayerCounts, int shamanIcons) {
        super(name, era, allowedPlayerCounts);
        this.shamanIcons = shamanIcons;
    }

    @Override
    public void onPurchase(Player player) {
    }

    @Override
    public CharacterType getType() {
        return CharacterType.SHAMAN;
    }

    @Override
    public int getShamanIcons() {
        return shamanIcons;
    }
}
