package mesos.tribe;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mesos.cards.characters.Builder;
import mesos.cards.characters.CharacterCard;
import mesos.cards.characters.Inventor;
import mesos.enums.CharacterType;
import mesos.enums.InventionIcon;

/**
 * Pure-math aggregator over a player's character cards. No dependency on
 * {@code Player} or {@code Board} — these methods only inspect the cards
 * themselves, so {@link Tribe} delegates to keep its surface focused on
 * the mutable list + observer notifications.
 */
final class CharacterStats {

    private CharacterStats() { }

    static int countByType(List<CharacterCard> cards, CharacterType type) {
        return (int) cards.stream().filter(c -> c.getType() == type).count();
    }

    static int buildingDiscount(List<CharacterCard> cards) {
        return cards.stream()
                .filter(Builder.class::isInstance)
                .map(Builder.class::cast)
                .mapToInt(Builder::getDiscountValue)
                .sum();
    }

    static int sustenanceDiscount(List<CharacterCard> cards) {
        return cards.stream().mapToInt(CharacterCard::getBaseSustenanceDiscount).sum();
    }

    static int totalShamanIcons(List<CharacterCard> cards) {
        return cards.stream().mapToInt(CharacterCard::getShamanIcons).sum();
    }

    static int distinctInventionIcons(List<CharacterCard> cards) {
        Set<InventionIcon> icons = new HashSet<>();
        for (CharacterCard card : cards) {
            if (card instanceof Inventor inventor) {
                icons.add(inventor.getInventionIcon());
            }
        }
        return icons.size();
    }

    static int completeCharacterSets(List<CharacterCard> cards) {
        int min = Integer.MAX_VALUE;
        for (CharacterType type : CharacterType.values()) {
            min = Math.min(min, countByType(cards, type));
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    static int inventorPairs(List<CharacterCard> cards) {
        Map<InventionIcon, Integer> occurrences = new HashMap<>();
        for (CharacterCard card : cards) {
            if (card instanceof Inventor inventor) {
                InventionIcon icon = inventor.getInventionIcon();
                occurrences.merge(icon, 1, Integer::sum);
            }
        }
        return occurrences.values().stream().mapToInt(v -> v / 2).sum();
    }

    static int artistPairs(List<CharacterCard> cards) {
        return countByType(cards, CharacterType.ARTIST) / 2;
    }
}
