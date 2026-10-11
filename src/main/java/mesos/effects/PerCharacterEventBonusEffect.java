package mesos.effects;

import mesos.enums.CharacterType;
import mesos.enums.EventType;
import mesos.player.Player;

/**
 * Building effect that grants extra food or prestige per qualifying character
 * whenever a specific event type is resolved.
 */
public class PerCharacterEventBonusEffect extends BuildingEffect {
    private static final long serialVersionUID = 1L;
    private final EventType targetEventType;
    private final CharacterType targetCharacterType;
    private final int extraFoodPerCharacter;
    private final int extraPrestigePerCharacter;

    /**
     * Creates a per-character event bonus effect.
     *
     * @param targetEventType         the event type that triggers this bonus
     * @param targetCharacterType     the character type that contributes to the bonus
     * @param extraFoodPerCharacter   extra food per qualifying character (0 if none)
     * @param extraPrestigePerCharacter extra prestige per qualifying character (0 if none)
     */
    public PerCharacterEventBonusEffect(EventType targetEventType, CharacterType targetCharacterType,
                                        int extraFoodPerCharacter, int extraPrestigePerCharacter) {
        this.targetEventType = targetEventType;
        this.targetCharacterType = targetCharacterType;
        this.extraFoodPerCharacter = extraFoodPerCharacter;
        this.extraPrestigePerCharacter = extraPrestigePerCharacter;
    }

    @Override
    public int getExtraFoodPerCharacterForEvent(EventType eventType, CharacterType characterType, Player owner) {
        if (eventType == targetEventType && characterType == targetCharacterType) {
            return extraFoodPerCharacter;
        }
        return 0;
    }

    @Override
    public int getExtraPrestigePerCharacterForEvent(EventType eventType, CharacterType characterType, Player owner) {
        if (eventType == targetEventType && characterType == targetCharacterType) {
            return extraPrestigePerCharacter;
        }
        return 0;
    }

    @Override
    public String getDescription() {
        return targetEventType.name();
    }
}
