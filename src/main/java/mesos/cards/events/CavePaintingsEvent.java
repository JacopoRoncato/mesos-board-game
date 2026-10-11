package mesos.cards.events;

import java.util.List;
import java.util.Set;
import mesos.enums.CharacterType;
import mesos.enums.Era;
import mesos.enums.EventType;
import mesos.enums.PlayerCount;
import mesos.player.Player;

/**
 * Cave paintings event: players with at least {@code artistThreshold} artists earn PP per artist;
 * those below the threshold lose a fixed PP penalty.
 */
public class CavePaintingsEvent extends EventCard {
    private static final long serialVersionUID = 1L;
    private final int artistThreshold;
    private final int ppPenalty;
    private final int ppRewardPerArtist;

    /**
     * Creates a cave paintings event card.
     *
     * @param artistThreshold   minimum artist count required to earn the reward instead of the penalty
     * @param ppPenalty         prestige lost when the player is below the threshold
     * @param ppRewardPerArtist prestige earned per artist when at or above the threshold
     */
    public CavePaintingsEvent(String name, Era era, Set<PlayerCount> allowedPlayerCounts,
                              int artistThreshold, int ppPenalty, int ppRewardPerArtist) {
        super(name, era, allowedPlayerCounts, EventType.CAVE_PAINTINGS);
        this.artistThreshold = artistThreshold;
        this.ppPenalty = ppPenalty;
        this.ppRewardPerArtist = ppRewardPerArtist;
    }

    @Override
    public void resolveEvent(List<Player> players) {
        for (Player player : players) {
            int artists = player.getTribe().countCharacters(CharacterType.ARTIST);
            if (artists >= artistThreshold) {
                player.addPrestige(artists * ppRewardPerArtist);
            } else {
                player.removePrestige(ppPenalty);
            }
            int extraFood = player.getTribe().getExtraFoodPerCharacterForEvent(getEventType(), CharacterType.ARTIST, player);
            player.gainFood(artists * extraFood);
        }
    }

    /** Returns the minimum artist count required to earn the reward. */
    public int getArtistThreshold() {
        return artistThreshold;
    }

    /** Returns the PP penalty applied when the player is below the artist threshold. */
    public int getPpPenalty() {
        return ppPenalty;
    }

    /** Returns the PP reward per artist when the player meets or exceeds the threshold. */
    public int getPpRewardPerArtist() {
        return ppRewardPerArtist;
    }
}
