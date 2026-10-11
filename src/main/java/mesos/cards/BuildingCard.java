package mesos.cards;

import mesos.board.Board;
import mesos.cards.characters.CharacterCard;
import mesos.effects.BuildingEffect;
import mesos.effects.ExtraTopRowPickEffect;
import mesos.effects.NoBuildingEffect;
import mesos.enums.CharacterType;
import mesos.enums.Era;
import mesos.enums.EventType;
import mesos.order.TurnOrderSpace;
import mesos.player.Player;

/**
 * A building card that a player can purchase and add to their tribe.
 * Delegates all effect logic to a {@link BuildingEffect} strategy object.
 */
public class BuildingCard extends Card {
    private static final long serialVersionUID = 1L;
    private final int foodCost;
    private final int printedPrestigePoints;
    private final BuildingEffect effect;

    /**
     * Creates a building card.
     *
     * @param name                  display name
     * @param era                   the era this card belongs to
     * @param foodCost              base food cost before discounts
     * @param printedPrestigePoints prestige points printed on the card (before end-game bonus)
     * @param effect                building effect strategy; {@code null} is treated as no effect
     */
    public BuildingCard(String name, Era era, int foodCost, int printedPrestigePoints, BuildingEffect effect) {
        super(name, era);
        this.foodCost = foodCost;
        this.printedPrestigePoints = printedPrestigePoints;
        this.effect = effect == null ? new NoBuildingEffect() : effect;
    }

    /**
     * Returns the actual food cost after applying the given discount.
     *
     * @param discount total discount from builders and other effects
     * @return the cost after discount, clamped to zero
     */
    public int calculateRealCost(int discount) {
        return Math.max(0, foodCost - discount);
    }

    /** Notifies this card's effect that the building has been acquired by {@code owner}. */
    public void onAcquire(Player owner) {
        effect.onAcquire(owner);
    }

    /** Notifies this card's effect that a character was added to the owner's tribe. */
    public void onCharacterAdded(Player owner, CharacterCard character) {
        effect.onCharacterAdded(owner, character);
    }

    /** Notifies this card's effect that the owner placed their totem on a turn-order space. */
    public void onTurnOrderPlaced(Player owner, TurnOrderSpace space) {
        effect.onTurnOrderPlaced(owner, space);
    }

    /** Notifies this card's effect at the end of the action phase. */
    public void onEndOfActionPhase(Player owner, Board board) {
        effect.onEndOfActionPhase(owner, board);
    }

    /** Returns any extra sustenance discount provided by this building's effect. */
    public int getExtraSustenanceDiscount(Player owner) {
        return effect.getExtraSustenanceDiscount(owner);
    }

    /** Returns any additional shaman icons granted by this building's effect. */
    public int getAdditionalShamanIcons(Player owner) {
        return effect.getAdditionalShamanIcons(owner);
    }

    /** Returns {@code true} if this building protects the owner from the shamanic penalty. */
    public boolean ignoresShamanicPenalty(Player owner) {
        return effect.ignoresShamanicPenalty(owner);
    }

    /** Returns {@code true} if this building doubles the owner's shamanic reward. */
    public boolean doublesShamanicReward(Player owner) {
        return effect.doublesShamanicReward(owner);
    }

    /** Returns extra food per qualifying character for the given event type. */
    public int getExtraFoodPerCharacterForEvent(EventType eventType, CharacterType characterType, Player owner) {
        return effect.getExtraFoodPerCharacterForEvent(eventType, characterType, owner);
    }

    /** Returns extra prestige per qualifying character for the given event type. */
    public int getExtraPrestigePerCharacterForEvent(EventType eventType, CharacterType characterType, Player owner) {
        return effect.getExtraPrestigePerCharacterForEvent(eventType, characterType, owner);
    }

    /** Returns this building's end-game prestige bonus (from its effect, if any). */
    public int getEndGameBonus(Player owner) {
        return effect.getEndGameBonus(owner);
    }

    /** Returns the total prestige awarded at game end (printed PP + end-game bonus). */
    public int getTotalPrestigeAtGameEnd(Player owner) {
        return printedPrestigePoints + getEndGameBonus(owner);
    }

    /** Returns the base food cost of this building before any discounts. */
    public int getFoodCost() {
        return foodCost;
    }

    /** Returns the prestige points printed on the card face. */
    public int getPrintedPrestigePoints() {
        return printedPrestigePoints;
    }

    /**
     * Returns this building's effect cast to {@link ExtraTopRowPickEffect},
     * or {@code null} if the effect is not of that type.
     */
    public ExtraTopRowPickEffect getExtraTopRowPickEffect() {
        return (effect instanceof ExtraTopRowPickEffect e) ? e : null;
    }
}
