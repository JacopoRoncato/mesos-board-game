package mesos.cards.events;

import java.util.List;
import java.util.Set;
import mesos.enums.CharacterType;
import mesos.enums.Era;
import mesos.enums.EventType;
import mesos.enums.PlayerCount;
import mesos.player.Player;

/**
 * Hunting event: each player gains 1 food per hunter plus any building bonuses,
 * and earns prestige per hunter according to the printed reward value.
 */
public class HuntingEvent extends EventCard {
    private static final long serialVersionUID = 1L;
    private final int ppRewardPerHunter;

    /**
     * Creates a hunting event card.
     *
     * @param ppRewardPerHunter prestige points awarded per hunter the player owns
     */
    public HuntingEvent(String name, Era era, Set<PlayerCount> allowedPlayerCounts, int ppRewardPerHunter) {
        super(name, era, allowedPlayerCounts, EventType.HUNTING);
        this.ppRewardPerHunter = ppRewardPerHunter;
    }

    @Override
    public void resolveEvent(List<Player> players) {
        for (Player player : players) {
            int hunters = player.getTribe().countCharacters(CharacterType.HUNTER);
            int extraFood = player.getTribe().getExtraFoodPerCharacterForEvent(getEventType(), CharacterType.HUNTER, player);
            int extraPrestige = player.getTribe().getExtraPrestigePerCharacterForEvent(getEventType(), CharacterType.HUNTER, player);
            player.gainFood(hunters * (1 + extraFood));
            player.addPrestige(hunters * (ppRewardPerHunter + extraPrestige));
        }
    }

    /** Returns the prestige reward per hunter for this event. */
    public int getPpRewardPerHunter() {
        return ppRewardPerHunter;
    }
}
