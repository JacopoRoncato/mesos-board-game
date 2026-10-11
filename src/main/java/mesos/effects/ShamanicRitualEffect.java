package mesos.effects;

import mesos.player.Player;

/**
 * Building effect that modifies the player's shamanic ritual outcome:
 * may add extra shaman icons, ignore the minority penalty, or double the majority reward.
 */
public class ShamanicRitualEffect extends BuildingEffect {
    private static final long serialVersionUID = 1L;
    private final int extraShamanIcons;
    private final boolean ignorePenaltyIfLowest;
    private final boolean doubleRewardIfHighest;

    /**
     * Creates a shamanic ritual effect.
     *
     * @param extraShamanIcons     bonus shaman icons added to the player's total
     * @param ignorePenaltyIfLowest {@code true} if the player is protected from the minority penalty
     * @param doubleRewardIfHighest {@code true} if the player's majority reward is doubled
     */
    public ShamanicRitualEffect(int extraShamanIcons, boolean ignorePenaltyIfLowest,
                                boolean doubleRewardIfHighest) {
        this.extraShamanIcons = extraShamanIcons;
        this.ignorePenaltyIfLowest = ignorePenaltyIfLowest;
        this.doubleRewardIfHighest = doubleRewardIfHighest;
    }

    @Override
    public int getAdditionalShamanIcons(Player owner) {
        return extraShamanIcons;
    }

    @Override
    public boolean ignoresShamanicPenalty(Player owner) {
        return ignorePenaltyIfLowest;
    }

    @Override
    public boolean doublesShamanicReward(Player owner) {
        return doubleRewardIfHighest;
    }

    @Override
    public String getDescription() {
        return "SHAMANIC_RITUAL";
    }
}
