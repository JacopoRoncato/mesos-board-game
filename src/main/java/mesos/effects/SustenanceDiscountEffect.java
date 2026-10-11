package mesos.effects;

import mesos.enums.CharacterType;
import mesos.player.Player;

/**
 * Building effect that reduces the sustenance cost by a fixed amount per
 * character of the specified type in the player's tribe.
 */
public class SustenanceDiscountEffect extends BuildingEffect {
    private static final long serialVersionUID = 1L;
    private final CharacterType discountedCharacterType;
    private final int discountPerCharacter;

    /**
     * Creates a sustenance discount effect.
     *
     * @param discountedCharacterType the character type that generates the discount
     * @param discountPerCharacter    food discount per character of that type
     */
    public SustenanceDiscountEffect(CharacterType discountedCharacterType, int discountPerCharacter) {
        this.discountedCharacterType = discountedCharacterType;
        this.discountPerCharacter = discountPerCharacter;
    }

    @Override
    public int getExtraSustenanceDiscount(Player owner) {
        return owner.getTribe().countCharacters(discountedCharacterType) * discountPerCharacter;
    }

    @Override
    public String getDescription() {
        return discountedCharacterType.name();
    }
}
