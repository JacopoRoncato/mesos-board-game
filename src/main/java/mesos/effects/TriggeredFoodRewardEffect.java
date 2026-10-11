package mesos.effects;

import mesos.cards.characters.CharacterCard;
import mesos.enums.TriggeredFoodRewardType;
import mesos.player.Player;

/**
 * Building effect that grants food whenever the player's tribe reaches a new
 * complete set or a new matching inventor pair (depending on the trigger type).
 */
public class TriggeredFoodRewardEffect extends BuildingEffect {
    private static final long serialVersionUID = 1L;
    private final TriggeredFoodRewardType triggerType;
    private final int foodReward;
    private int rewardedOccurrences;

    /**
     * Creates a triggered food reward effect.
     *
     * @param triggerType the condition that triggers food gain
     * @param foodReward  food awarded each time a new trigger condition is met
     */
    public TriggeredFoodRewardEffect(TriggeredFoodRewardType triggerType, int foodReward) {
        this.triggerType = triggerType;
        this.foodReward = foodReward;
        this.rewardedOccurrences = 0;
    }

    @Override
    public void onAcquire(Player owner) {
        // Rules 17.1, 17.5: do NOT reward retroactively for sets/pairs that
        // already existed when the building was acquired. Snapshot the count.
        rewardedOccurrences = countCurrentOccurrences(owner);
    }

    @Override
    public void onCharacterAdded(Player owner, CharacterCard character) {
        int currentOccurrences = countCurrentOccurrences(owner);
        int newOccurrences = Math.max(0, currentOccurrences - rewardedOccurrences);
        if (newOccurrences > 0) {
            owner.gainFood(newOccurrences * foodReward);
            rewardedOccurrences = currentOccurrences;
        }
    }

    private int countCurrentOccurrences(Player owner) {
        return switch (triggerType) {
            case COMPLETE_SET -> owner.getTribe().countCompleteCharacterSets();
            case MATCHING_INVENTOR_PAIR -> owner.getTribe().countInventorPairs();
        };
    }

    @Override
    public String getDescription() {
        return triggerType.name();
    }
}
