package mesos.tribe;

import java.io.Serializable;
import java.util.ArrayList;
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
 * One player's collection of acquired characters and buildings. Owns the
 * mutable lists; character-side math is delegated to {@link CharacterStats}
 * and building-side aggregation to {@link BuildingEffects}.
 */
public class Tribe implements Serializable {
    private static final long serialVersionUID = 1L;
    private final List<CharacterCard> characterCards;
    private final List<BuildingCard> buildingCards;

    /** Creates an empty tribe with no characters and no buildings. */
    public Tribe() {
        this.characterCards = new ArrayList<>();
        this.buildingCards = new ArrayList<>();
    }

    /** Adds a character card to this tribe. */
    public void addCharacter(CharacterCard characterCard) {
        characterCards.add(characterCard);
    }

    /** Adds a building card to this tribe. */
    public void addBuilding(BuildingCard buildingCard) {
        buildingCards.add(buildingCard);
    }

    // ---- character-side queries (delegates to CharacterStats) ----

    /** Returns the number of characters of the given type in this tribe. */
    public int countCharacters(CharacterType type) {
        return CharacterStats.countByType(characterCards, type);
    }

    /** Returns the total building-cost discount from all builder characters. */
    public int calculateBuildingDiscount() {
        return CharacterStats.buildingDiscount(characterCards);
    }

    /** Returns the total sustenance discount from all gatherer characters. */
    public int calculateSustenanceDiscount() {
        return CharacterStats.sustenanceDiscount(characterCards);
    }

    /** Returns the total shaman icons across all characters. */
    public int getTotalShamanIcons() {
        return CharacterStats.totalShamanIcons(characterCards);
    }

    /** Returns the number of distinct invention icons across all inventor characters. */
    public int countDistinctInventionIcons() {
        return CharacterStats.distinctInventionIcons(characterCards);
    }

    /** Returns the number of complete character sets (one of each of the six types). */
    public int countCompleteCharacterSets() {
        return CharacterStats.completeCharacterSets(characterCards);
    }

    /** Returns the number of matching inventor pairs (two inventors with the same icon). */
    public int countInventorPairs() {
        return CharacterStats.inventorPairs(characterCards);
    }

    /** Returns the number of artist pairs (every two artists counts as one pair). */
    public int countArtistPairs() {
        return CharacterStats.artistPairs(characterCards);
    }

    // ---- building-side hooks + queries (delegates to BuildingEffects) ----

    /** Notifies all buildings that a character was added to this tribe. */
    public void notifyBuildingsOnCharacterAdded(Player owner, CharacterCard character) {
        BuildingEffects.onCharacterAdded(buildingCards, owner, character);
    }

    /** Notifies all buildings that the owner placed their totem on a turn-order space. */
    public void notifyBuildingsOnTurnOrderPlaced(Player owner, TurnOrderSpace space) {
        BuildingEffects.onTurnOrderPlaced(buildingCards, owner, space);
    }

    /** Notifies all buildings that the action phase has ended. */
    public void notifyBuildingsOnEndOfActionPhase(Player owner, Board board) {
        BuildingEffects.onEndOfActionPhase(buildingCards, owner, board);
    }

    /** Returns the total extra sustenance discount provided by all buildings. */
    public int getExtraSustenanceDiscount(Player owner) {
        return BuildingEffects.extraSustenanceDiscount(buildingCards, owner);
    }

    /** Returns the total additional shaman icons provided by all buildings. */
    public int getAdditionalShamanIcons(Player owner) {
        return BuildingEffects.additionalShamanIcons(buildingCards, owner);
    }

    /** Returns {@code true} if any building protects the owner from the shamanic penalty. */
    public boolean ignoresShamanicPenalty(Player owner) {
        return BuildingEffects.ignoresShamanicPenalty(buildingCards, owner);
    }

    /** Returns {@code true} if any building doubles the owner's shamanic reward. */
    public boolean doublesShamanicReward(Player owner) {
        return BuildingEffects.doublesShamanicReward(buildingCards, owner);
    }

    /** Returns the total extra food per qualifying character for the given event type. */
    public int getExtraFoodPerCharacterForEvent(EventType eventType, CharacterType characterType, Player owner) {
        return BuildingEffects.extraFoodPerCharacterForEvent(buildingCards, eventType, characterType, owner);
    }

    /** Returns the total extra prestige per qualifying character for the given event type. */
    public int getExtraPrestigePerCharacterForEvent(EventType eventType, CharacterType characterType, Player owner) {
        return BuildingEffects.extraPrestigePerCharacterForEvent(buildingCards, eventType, characterType, owner);
    }

    /**
     * Returns the first {@link ExtraTopRowPickEffect} found among this tribe's buildings,
     * or {@code null} if no such building is owned.
     */
    public ExtraTopRowPickEffect findExtraTopRowPickEffect() {
        return BuildingEffects.findExtraTopRowPickEffect(buildingCards);
    }

    // ---- defensive accessors ----

    /** Returns a defensive copy of the list of building cards in this tribe. */
    public List<BuildingCard> getBuildingCards() {
        return new ArrayList<>(buildingCards);
    }

    /** Returns a defensive copy of the list of character cards in this tribe. */
    public List<CharacterCard> getCharacterCards() {
        return new ArrayList<>(characterCards);
    }
}
