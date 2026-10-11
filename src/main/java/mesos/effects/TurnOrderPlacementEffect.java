package mesos.effects;

import mesos.order.TurnOrderSpace;
import mesos.player.Player;

/**
 * Building effect that grants extra food when the player places their totem
 * on a turn-order space that has a food bonus (and is not the penalty space).
 */
public class TurnOrderPlacementEffect extends BuildingEffect {
    private static final long serialVersionUID = 1L;
    private final int extraFoodIfBonusSpace;

    /**
     * Creates a turn-order placement effect.
     *
     * @param extraFoodIfBonusSpace extra food granted when the player occupies a bonus space
     */
    public TurnOrderPlacementEffect(int extraFoodIfBonusSpace) {
        this.extraFoodIfBonusSpace = extraFoodIfBonusSpace;
    }

    @Override
    public void onTurnOrderPlaced(Player owner, TurnOrderSpace space) {
        if (space != null && space.getFoodBonus() > 0 && !space.isLastSpace()) {
            owner.gainFood(extraFoodIfBonusSpace);
        }
    }

    @Override
    public String getDescription() {
        return "TURN_ORDER";
    }
}
