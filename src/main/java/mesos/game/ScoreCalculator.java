package mesos.game;

import java.util.List;
import mesos.cards.characters.Builder;
import mesos.enums.CharacterType;
import mesos.player.Player;
import mesos.tribe.Tribe;

/**
 * Computes the end-game prestige awarded to each player and stamps it onto
 * their {@code prestigePoints}. Kept separate from {@link Game} so the
 * scoring rules can evolve (or be tested) without touching round flow.
 */
final class ScoreCalculator {

    private static final int ARTIST_PAIR_VALUE = 10;

    private ScoreCalculator() { }

    static void awardFinalPrestige(List<Player> players) {
        for (Player player : players) {
            Tribe tribe = player.getTribe();
            int inventors = tribe.countCharacters(CharacterType.INVENTOR);
            int distinctIcons = tribe.countDistinctInventionIcons();
            int artistPairs = tribe.countArtistPairs();
            int builderPrestige = sumBuilderPrestige(tribe);
            int buildingPrestige = sumBuildingPrestige(tribe, player);

            player.addPrestige(inventors * distinctIcons);
            player.addPrestige(artistPairs * ARTIST_PAIR_VALUE);
            player.addPrestige(builderPrestige);
            player.addPrestige(buildingPrestige);
        }
    }

    private static int sumBuilderPrestige(Tribe tribe) {
        return tribe.getCharacterCards().stream()
                .filter(Builder.class::isInstance)
                .map(Builder.class::cast)
                .mapToInt(Builder::getPpValue)
                .sum();
    }

    private static int sumBuildingPrestige(Tribe tribe, Player player) {
        return tribe.getBuildingCards().stream()
                .mapToInt(b -> b.getTotalPrestigeAtGameEnd(player))
                .sum();
    }
}
