package mesos.effects;

import java.io.Serializable;
import mesos.board.Board;
import mesos.cards.characters.CharacterCard;
import mesos.enums.CharacterType;
import mesos.enums.EventType;
import mesos.order.TurnOrderSpace;
import mesos.player.Player;

/**
 * Strategy base class for all building card effects.
 * All hook methods are no-ops and all query methods return neutral defaults,
 * so concrete subclasses only need to override what they actually change.
 */
public abstract class BuildingEffect implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Called when the building is acquired by {@code owner}. */
    public void onAcquire(Player owner) {
    }

    /** Called when a character is added to {@code owner}'s tribe. */
    public void onCharacterAdded(Player owner, CharacterCard character) {
    }

    /** Called when {@code owner} places their totem on a turn-order space. */
    public void onTurnOrderPlaced(Player owner, TurnOrderSpace space) {
    }

    /** Called at the end of the action phase for {@code owner}. */
    public void onEndOfActionPhase(Player owner, Board board) {
    }

    /** Returns any extra sustenance discount this effect provides. Defaults to {@code 0}. */
    public int getExtraSustenanceDiscount(Player owner) {
        return 0;
    }

    /** Returns any additional shaman icons this effect grants. Defaults to {@code 0}. */
    public int getAdditionalShamanIcons(Player owner) {
        return 0;
    }

    /** Returns {@code true} if this effect protects the owner from the shamanic penalty. */
    public boolean ignoresShamanicPenalty(Player owner) {
        return false;
    }

    /** Returns {@code true} if this effect doubles the owner's shamanic reward. */
    public boolean doublesShamanicReward(Player owner) {
        return false;
    }

    /** Returns extra food per qualifying character for the given event type. Defaults to {@code 0}. */
    public int getExtraFoodPerCharacterForEvent(EventType eventType, CharacterType characterType, Player owner) {
        return 0;
    }

    /** Returns extra prestige per qualifying character for the given event type. Defaults to {@code 0}. */
    public int getExtraPrestigePerCharacterForEvent(EventType eventType, CharacterType characterType, Player owner) {
        return 0;
    }

    /** Returns the end-game prestige bonus provided by this effect. Defaults to {@code 0}. */
    public int getEndGameBonus(Player owner) {
        return 0;
    }

    /** Returns a short description of this effect (used for debugging and snapshots). */
    public String getDescription() {
        return "";
    }
}
