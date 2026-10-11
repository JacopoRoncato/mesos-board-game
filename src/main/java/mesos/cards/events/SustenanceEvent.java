package mesos.cards.events;

import java.util.List;
import java.util.Set;
import mesos.enums.Era;
import mesos.enums.EventType;
import mesos.enums.PlayerCount;
import mesos.player.Player;

/**
 * Sustenance event: each player must pay food equal to their character count minus discounts.
 * For every character they cannot feed, they lose the printed PP penalty.
 */
public class SustenanceEvent extends EventCard {
    private static final long serialVersionUID = 1L;
    private final int ppPenaltyPerUnfedCharacter;

    /**
     * Creates a sustenance event card.
     *
     * @param ppPenaltyPerUnfedCharacter prestige lost per character the player cannot feed
     */
    public SustenanceEvent(String name, Era era, Set<PlayerCount> allowedPlayerCounts, int ppPenaltyPerUnfedCharacter) {
        super(name, era, allowedPlayerCounts, EventType.SUSTENANCE);
        this.ppPenaltyPerUnfedCharacter = ppPenaltyPerUnfedCharacter;
    }

    @Override
    public void resolveEvent(List<Player> players) {
        for (Player player : players) {
            int totalCharacters = player.getTribe().getCharacterCards().size();
            int totalDiscount = player.getTribe().calculateSustenanceDiscount()
                    + player.getTribe().getExtraSustenanceDiscount(player);
            int foodNeeded = Math.max(0, totalCharacters - totalDiscount);
            int foodAvailable = player.getFood();
            int unfedCharacters = Math.max(0, foodNeeded - foodAvailable);
            player.payFood(Math.min(foodNeeded, foodAvailable));
            if (unfedCharacters > 0) {
                player.removePrestige(unfedCharacters * ppPenaltyPerUnfedCharacter);
            }
        }
    }

    /** Returns the prestige penalty per unfed character for this event. */
    public int getPpPenaltyPerUnfedCharacter() {
        return ppPenaltyPerUnfedCharacter;
    }
}
