package mesos.player;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import mesos.cards.BuildingCard;
import mesos.cards.characters.CharacterCard;
import mesos.offer.OfferTile;
import mesos.tribe.Tribe;

/**
 * Represents one player: holds food, prestige points, their totem,
 * their tribe, and the offer tile they have selected this round.
 */
public class Player implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id;
    private int food;
    private int prestigePoints;
    private final Totem totem;
    private final Tribe tribe;
    private OfferTile selectedOfferTile;
    private transient PropertyChangeListener listener;

    /**
     * Creates a player with the given identifier and totem.
     *
     * @param id    the unique player identifier (nickname)
     * @param totem the player's totem
     */
    public Player(String id, Totem totem) {
        this.id = id;
        this.totem = totem;
        this.tribe = new Tribe();
    }

    /**
     * Sets the listener that will be notified whenever this player's state changes.
     *
     * @param listener the listener to register; may be {@code null} to remove the current listener
     */
    public void setListener(PropertyChangeListener listener) {
        this.listener = listener;
    }

    private void fire() {
        if (listener != null)
            listener.propertyChange(new PropertyChangeEvent(this, "update", null, null));
    }

    /**
     * Deducts the given amount of food from this player.
     *
     * @param amount the amount to pay
     * @return {@code true} if payment succeeded; {@code false} if the player had insufficient food
     */
    public boolean payFood(int amount) {
        if (amount <= 0) return true;
        if (food < amount) return false;
        food -= amount;
        fire();
        return true;
    }

    /**
     * Adds the given amount of food to this player's supply.
     *
     * @param amount the amount to gain (ignored if zero or negative)
     */
    public void gainFood(int amount) {
        if (amount > 0) {
            food += amount;
            fire();
        }
    }

    /** Returns the player's current food supply. */
    public int getFood() {
        return food;
    }

    /**
     * Awards the given amount of prestige to this player.
     *
     * @param amount the amount to add (ignored if zero or negative)
     */
    public void addPrestige(int amount) {
        if (amount > 0) {
            prestigePoints += amount;
            fire();
        }
    }

    /**
     * Deducts the given amount of prestige from this player.
     *
     * @param amount the amount to remove (ignored if zero or negative)
     */
    public void removePrestige(int amount) {
        if (amount > 0) {
            prestigePoints -= amount;
            fire();
        }
    }

    /**
     * Records the offer tile the player has selected during the placement phase.
     *
     * @param tile the selected tile
     */
    public void chooseOfferTile(OfferTile tile) {
        this.selectedOfferTile = tile;
    }

    /** Clears the player's selected offer tile after it has been executed. */
    public void clearSelectedOfferTile() {
        this.selectedOfferTile = null;
    }

    /** Returns the offer tile this player has selected, or {@code null} if none. */
    public OfferTile getSelectedOfferTile() {
        return selectedOfferTile;
    }

    /**
     * Adds the given character card to the player's tribe, triggering purchase effects
     * and notifying buildings.
     *
     * @param card the character card to acquire
     */
    public void acquireCharacter(CharacterCard card) {
        tribe.addCharacter(card);
        card.onPurchase(this);
        tribe.notifyBuildingsOnCharacterAdded(this, card);
    }

    /**
     * Purchases a building card: deducts the food cost and adds the card to the tribe.
     *
     * @param card the building card to acquire
     * @throws IllegalStateException if the player cannot afford the building
     */
    public void acquireBuilding(BuildingCard card) {
        int discount = tribe.calculateBuildingDiscount();
        int realCost = card.calculateRealCost(discount);
        if (!payFood(realCost)) {
            throw new IllegalStateException(
                "Cannot acquire building " + card.getName()
                        + " (cost=" + realCost + ", food=" + food + ")");
        }
        tribe.addBuilding(card);
        card.onAcquire(this);
    }

    /** Returns the player's unique identifier (nickname). */
    public String getId() {
        return id;
    }

    /** Returns the player's current prestige points. */
    public int getPrestigePoints() {
        return prestigePoints;
    }

    /** Returns the player's totem. */
    public Totem getTotem() {
        return totem;
    }

    /** Returns the player's tribe (character and building cards). */
    public Tribe getTribe() {
        return tribe;
    }
}
