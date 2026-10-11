package mesos.cards.characters;

import java.util.Set;
import mesos.enums.CharacterType;
import mesos.enums.Era;
import mesos.enums.PlayerCount;
import mesos.player.Player;

/** A Builder character card. Provides a building cost discount and scores extra PP. */
public class Builder extends CharacterCard {
    private static final long serialVersionUID = 1L;
    private final int ppValue;
    private final int discountValue;

    /**
     * Creates a Builder card.
     *
     * @param ppValue       prestige points granted by this builder at end-game
     * @param discountValue food discount applied when purchasing a building
     */
    public Builder(String name, Era era, Set<PlayerCount> allowedPlayerCounts, int ppValue, int discountValue) {
        super(name, era, allowedPlayerCounts);
        this.ppValue = ppValue;
        this.discountValue = discountValue;
    }

    @Override
    public void onPurchase(Player player) {
    }

    @Override
    public CharacterType getType() {
        return CharacterType.BUILDER;
    }

    /** Returns the prestige points this builder is worth at game end. */
    public int getPpValue() {
        return ppValue;
    }

    /** Returns the food discount this builder provides when purchasing a building. */
    public int getDiscountValue() {
        return discountValue;
    }
}
