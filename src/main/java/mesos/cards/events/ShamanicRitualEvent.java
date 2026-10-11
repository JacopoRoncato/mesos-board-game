package mesos.cards.events;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mesos.enums.Era;
import mesos.enums.EventType;
import mesos.enums.PlayerCount;
import mesos.player.Player;

/**
 * Shamanic ritual event: the player(s) with the most shaman icons earn a PP reward;
 * the player(s) with the fewest shaman icons lose a PP penalty.
 */
public class ShamanicRitualEvent extends EventCard {
    private static final long serialVersionUID = 1L;
    private final int ppReward;
    private final int ppPenalty;

    /**
     * Creates a shamanic ritual event card.
     *
     * @param ppReward  prestige awarded to the majority holder(s)
     * @param ppPenalty prestige lost by the minority holder(s)
     */
    public ShamanicRitualEvent(String name, Era era, Set<PlayerCount> allowedPlayerCounts, int ppReward, int ppPenalty) {
        super(name, era, allowedPlayerCounts, EventType.SHAMANIC_RITUAL);
        this.ppReward = ppReward;
        this.ppPenalty = ppPenalty;
    }

    @Override
    public void resolveEvent(List<Player> players) {
        if (players.isEmpty()) {
            return;
        }

        Map<Player, Integer> iconsByPlayer = new HashMap<>();
        int max = Integer.MIN_VALUE;
        int min = Integer.MAX_VALUE;

        for (Player player : players) {
            int icons = player.getTribe().getTotalShamanIcons() + player.getTribe().getAdditionalShamanIcons(player);
            iconsByPlayer.put(player, icons);
            max = Math.max(max, icons);
            min = Math.min(min, icons);
        }

        final int maxIcons = max;
        long maxCount = iconsByPlayer.values().stream().filter(value -> value == maxIcons).count();

        for (Player player : players) {
            int icons = iconsByPlayer.get(player);
            boolean tiedForMax = (icons == maxIcons && maxCount > 1);

            // Reward (majority): awarded only when the player actually holds the maximum.
            if (icons == max) {
                int reward = ppReward;
                // Double reward only when there is no tie and the player owns the building effect.
                if (!tiedForMax && player.getTribe().doublesShamanicReward(player)) {
                    reward = ppReward * 2;
                }
                player.addPrestige(reward);
            }

            // Penalty (minority): uses a separate "if" (not "else if") to handle the total-tie case.
            // When all players are tied (max == min), everyone receives the penalty, even players
            // whose building would normally protect them — ignoresShamanicPenalty only protects
            // against a real minority, not a total tie.
            boolean totalTie = (max == min);
            if (icons == min && (totalTie || !player.getTribe().ignoresShamanicPenalty(player))) {
                player.removePrestige(ppPenalty);
            }
        }
    }

    /** Returns the PP reward granted to the player(s) with the most shaman icons. */
    public int getPpReward() {
        return ppReward;
    }

    /** Returns the PP penalty applied to the player(s) with the fewest shaman icons. */
    public int getPpPenalty() {
        return ppPenalty;
    }
}
