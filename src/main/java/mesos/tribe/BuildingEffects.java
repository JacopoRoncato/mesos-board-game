package mesos.tribe;

import java.util.List;
import mesos.board.Board;
import mesos.cards.BuildingCard;
import mesos.cards.characters.CharacterCard;
import mesos.effects.ExtraTopRowPickEffect;
import mesos.enums.CharacterType;
import mesos.enums.EventType;
import mesos.order.TurnOrderSpace;
import mesos.player.Player;

/**
 * Stateless aggregator over a player's building cards. Centralizes the
 * "broadcast to every building, then collect" pattern (notify hooks +
 * sum/anyMatch reducers) so {@link Tribe} stays a thin holder.
 */
final class BuildingEffects {

    private BuildingEffects() { }

    // ---- broadcast hooks ----

    static void onCharacterAdded(List<BuildingCard> buildings, Player owner, CharacterCard character) {
        for (BuildingCard b : buildings) b.onCharacterAdded(owner, character);
    }

    static void onTurnOrderPlaced(List<BuildingCard> buildings, Player owner, TurnOrderSpace space) {
        for (BuildingCard b : buildings) b.onTurnOrderPlaced(owner, space);
    }

    static void onEndOfActionPhase(List<BuildingCard> buildings, Player owner, Board board) {
        for (BuildingCard b : buildings) b.onEndOfActionPhase(owner, board);
    }

    // ---- aggregating reducers ----

    static int extraSustenanceDiscount(List<BuildingCard> buildings, Player owner) {
        return buildings.stream().mapToInt(b -> b.getExtraSustenanceDiscount(owner)).sum();
    }

    static int additionalShamanIcons(List<BuildingCard> buildings, Player owner) {
        return buildings.stream().mapToInt(b -> b.getAdditionalShamanIcons(owner)).sum();
    }

    static boolean ignoresShamanicPenalty(List<BuildingCard> buildings, Player owner) {
        return buildings.stream().anyMatch(b -> b.ignoresShamanicPenalty(owner));
    }

    static boolean doublesShamanicReward(List<BuildingCard> buildings, Player owner) {
        return buildings.stream().anyMatch(b -> b.doublesShamanicReward(owner));
    }

    static int extraFoodPerCharacterForEvent(List<BuildingCard> buildings,
                                             EventType eventType,
                                             CharacterType characterType,
                                             Player owner) {
        return buildings.stream()
                .mapToInt(b -> b.getExtraFoodPerCharacterForEvent(eventType, characterType, owner))
                .sum();
    }

    static int extraPrestigePerCharacterForEvent(List<BuildingCard> buildings,
                                                 EventType eventType,
                                                 CharacterType characterType,
                                                 Player owner) {
        return buildings.stream()
                .mapToInt(b -> b.getExtraPrestigePerCharacterForEvent(eventType, characterType, owner))
                .sum();
    }

    static ExtraTopRowPickEffect findExtraTopRowPickEffect(List<BuildingCard> buildings) {
        for (BuildingCard b : buildings) {
            ExtraTopRowPickEffect effect = b.getExtraTopRowPickEffect();
            if (effect != null) return effect;
        }
        return null;
    }
}
