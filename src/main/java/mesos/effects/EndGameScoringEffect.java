package mesos.effects;

import mesos.cards.characters.Builder;
import mesos.enums.CharacterType;
import mesos.enums.EndGameScoringType;
import mesos.player.Player;

/**
 * Building effect that awards extra prestige at game end based on the
 * player's tribe composition (character counts, complete sets, or builders).
 */
public class EndGameScoringEffect extends BuildingEffect {
    private static final long serialVersionUID = 1L;
    private final EndGameScoringType scoringType;
    private final int pointsPerUnit;
    private final CharacterType targetCharacterType;

    /**
     * Creates an end-game scoring effect.
     *
     * @param scoringType         the scoring strategy to apply
     * @param pointsPerUnit       prestige points per unit (character, set, etc.)
     * @param targetCharacterType the character type counted when {@code scoringType} is
     *                            {@link EndGameScoringType#PER_CHARACTER_TYPE}; ignored otherwise
     */
    public EndGameScoringEffect(EndGameScoringType scoringType, int pointsPerUnit, CharacterType targetCharacterType) {
        this.scoringType = scoringType;
        this.pointsPerUnit = pointsPerUnit;
        this.targetCharacterType = targetCharacterType;
    }

    @Override
    public int getEndGameBonus(Player owner) {
        return switch (scoringType) {
            case PER_CHARACTER_TYPE -> owner.getTribe().countCharacters(targetCharacterType) * pointsPerUnit;
            case PER_COMPLETE_SET -> owner.getTribe().countCompleteCharacterSets() * pointsPerUnit;
            case DOUBLE_BUILDERS -> owner.getTribe().getCharacterCards().stream()
                    .filter(Builder.class::isInstance)
                    .map(Builder.class::cast)
                    .mapToInt(Builder::getPpValue)
                    .sum();
        };
    }

    @Override
    public String getDescription() {
        return scoringType.name();
    }
}
