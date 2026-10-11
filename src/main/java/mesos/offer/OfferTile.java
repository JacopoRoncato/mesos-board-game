package mesos.offer;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.Set;
import mesos.board.Board;
import mesos.cards.BuildingCard;
import mesos.cards.Card;
import mesos.cards.characters.CharacterCard;
import mesos.enums.OfferTileId;
import mesos.enums.PlayerCount;
import mesos.order.TurnOrderSpace;
import mesos.player.Player;
import mesos.player.Totem;

/**
 * One Offer Tile on the offer track. Holds its pick budget
 * (top/bottom cards + direct food bonus), the player-count restriction,
 * and the totem currently parked on it. Validation of a player's choice
 * is delegated to {@link OfferChoiceValidator}.
 */
public class OfferTile implements Serializable {
    private static final long serialVersionUID = 1L;
    private final OfferTileId id;
    private final Set<PlayerCount> allowedPlayerCounts;
    private int cardsFromTop;
    private int cardsFromBottom;
    private int directFoodBonus;
    private Totem occupiedBy;

    /**
     * Creates an offer tile with the given identifier.
     * All player counts are allowed by default.
     */
    public OfferTile(OfferTileId id) {
        this.id = id;
        this.allowedPlayerCounts = EnumSet.allOf(PlayerCount.class);
    }

    /** Returns {@code true} if no totem is currently placed on this tile. */
    public boolean isAvailable() {
        return occupiedBy == null;
    }

    /** Returns a new empty {@link OfferChoice} for this tile. */
    public OfferChoice createChoice() {
        return new OfferChoice(this);
    }

    /**
     * Returns {@code true} if {@code choice} is a legal selection for this tile
     * given the current board state and the player's resources.
     */
    public boolean validateChoice(OfferChoice choice, Player player, Board board) {
        return OfferChoiceValidator.isValid(this, choice, player, board);
    }

    /**
     * Executes the player's choice: grants the direct food bonus, takes the selected
     * cards from the board, places the player's totem on the turn-order tile,
     * and applies any relevant building effects.
     *
     * @throws IllegalArgumentException if the choice fails validation
     */
    public void executeChoice(OfferChoice choice, Player player, Board board) {
        if (!validateChoice(choice, player, board)) {
            throw new IllegalArgumentException("Invalid offer choice for tile " + id);
        }
        if (directFoodBonus > 0) {
            player.gainFood(directFoodBonus);
        }
        for (CardPick pick : choice.getOrderedPicks()) {
            Card card = board.takeCard(pick);
            if (card instanceof CharacterCard characterCard) {
                player.acquireCharacter(characterCard);
            } else if (card instanceof BuildingCard buildingCard) {
                player.acquireBuilding(buildingCard);
            }
        }
        TurnOrderSpace space = board.getTurnOrderTile().addTotem(player.getTotem(), player);
        board.getTurnOrderTile().applySpaceEffect(space, player);
        player.getTribe().notifyBuildingsOnTurnOrderPlaced(player, space);
        occupiedBy = null;
        player.clearSelectedOfferTile();
    }

    // ---- getters / setters ----

    /** Returns the identifier of this tile. */
    public OfferTileId getId() {
        return id;
    }

    /** Returns the set of player counts for which this tile is in play. */
    public Set<PlayerCount> getAllowedPlayerCounts() {
        return EnumSet.copyOf(allowedPlayerCounts);
    }

    /**
     * Replaces the allowed player counts for this tile.
     * Passing no arguments (or {@code null}) restores all player counts.
     */
    public void setAllowedPlayerCounts(PlayerCount... playerCounts) {
        allowedPlayerCounts.clear();
        if (playerCounts == null || playerCounts.length == 0) {
            allowedPlayerCounts.addAll(EnumSet.allOf(PlayerCount.class));
        } else {
            for (PlayerCount count : playerCounts) {
                allowedPlayerCounts.add(count);
            }
        }
    }

    /** Returns the number of cards the player may pick from the top row. */
    public int getCardsFromTop()                 { return cardsFromTop; }
    /** Sets the number of cards the player may pick from the top row. */
    public void setCardsFromTop(int n)           { this.cardsFromTop = n; }
    /** Returns the number of cards the player may pick from the bottom row. */
    public int getCardsFromBottom()              { return cardsFromBottom; }
    /** Sets the number of cards the player may pick from the bottom row. */
    public void setCardsFromBottom(int n)        { this.cardsFromBottom = n; }
    /** Returns the food bonus granted to the player when they select this tile. */
    public int getDirectFoodBonus()              { return directFoodBonus; }
    /** Sets the direct food bonus granted when a player selects this tile. */
    public void setDirectFoodBonus(int bonus)    { this.directFoodBonus = bonus; }
    /** Returns the totem currently on this tile, or {@code null} if the tile is free. */
    public Totem getOccupiedBy()                 { return occupiedBy; }
    /** Places or removes the totem on this tile. */
    public void setOccupiedBy(Totem totem)       { this.occupiedBy = totem; }
}
