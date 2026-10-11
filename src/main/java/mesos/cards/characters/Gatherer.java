package mesos.cards.characters;

import java.util.Set;
import mesos.enums.CharacterType;
import mesos.enums.Era;
import mesos.enums.PlayerCount;
import mesos.player.Player;

/** A Gatherer character card. Reduces the sustenance cost by 3 food. */
public class Gatherer extends CharacterCard {
    private static final long serialVersionUID = 1L;
    private final int sustenanceDiscount = 3;

    public Gatherer(String name, Era era, Set<PlayerCount> allowedPlayerCounts) {
        super(name, era, allowedPlayerCounts);
    }

    @Override
    public void onPurchase(Player player) {
    }

    @Override
    public CharacterType getType() {
        return CharacterType.GATHERER;
    }

    @Override
    public int getBaseSustenanceDiscount() {
        return sustenanceDiscount;
    }
}
