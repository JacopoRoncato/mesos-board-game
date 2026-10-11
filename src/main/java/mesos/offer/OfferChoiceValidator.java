package mesos.offer;

import java.util.ArrayList;
import java.util.List;
import mesos.board.Board;
import mesos.board.RowSlot;
import mesos.cards.BuildingCard;
import mesos.enums.VisibleRow;
import mesos.player.Player;

/**
 * Stateless validator for {@link OfferChoice} against the current Board.
 * Simulates the picks against a cloned row state so we can reject the
 * whole sequence before any real mutation happens.
 *
 * Rules:
 * - Characters and Buildings are on the same level: either may be picked freely.
 * - Building picks require the player to have enough food to pay their cost.
 * - An empty choice is valid only when no selectable cards remain in the rows
 *   the tile draws from (i.e., effective budget is 0 in both rows).
 */
final class OfferChoiceValidator {

    private OfferChoiceValidator() { }

    static boolean isValid(OfferTile tile, OfferChoice choice, Player player, Board board) {
        if (choice == null || player == null || board == null) return false;
        if (choice.getOfferTile() != tile) return false;

        int cardsFromTop = tile.getCardsFromTop();
        int cardsFromBottom = tile.getCardsFromBottom();

        // Tile gives nothing (e.g. food-only Tile A): choice must be empty.
        if (cardsFromTop == 0 && cardsFromBottom == 0) return choice.isEmpty();

        List<RowSlot> realTop = board.getVisibleRow(VisibleRow.TOP).getSlots();
        List<RowSlot> realBottom = board.getVisibleRow(VisibleRow.BOTTOM).getSlots();

        // Effective budget: capped by selectable cards actually in the row.
        // If a row contains only events (not selectable), picks from it are optional.
        int budgetTop    = Math.min(cardsFromTop,    countSelectableCards(realTop));
        int budgetBottom = Math.min(cardsFromBottom, countSelectableCards(realBottom));

        // Empty choice is valid only when there are no selectable cards in either row.
        if (choice.isEmpty()) {
            return budgetTop == 0 && budgetBottom == 0;
        }

        // Upper bound: no more picks than the effective budget per row.
        if (choice.countPicksFromRow(VisibleRow.TOP) > budgetTop) return false;
        if (choice.countPicksFromRow(VisibleRow.BOTTOM) > budgetBottom) return false;

        // Replay the picks against a cloned row state to check slot validity
        // and food affordability for buildings.
        List<RowSlot> simulatedTop = cloneSlots(realTop);
        List<RowSlot> simulatedBottom = cloneSlots(realBottom);
        int remainingTop = budgetTop;
        int remainingBottom = budgetBottom;
        int simulatedFood = player.getFood();

        for (CardPick pick : choice.getOrderedPicks()) {
            List<RowSlot> row = pick.getRow() == VisibleRow.TOP ? simulatedTop : simulatedBottom;
            if (pick.getRow() == VisibleRow.TOP) {
                if (remainingTop-- <= 0) return false;
            } else {
                if (remainingBottom-- <= 0) return false;
            }

            RowSlot slot = row.stream()
                    .filter(s -> s.getIndex() == pick.getSlotIndex())
                    .findFirst()
                    .orElse(null);
            if (slot == null || !slot.isSelectable()) return false;

            if (slot.getCard() instanceof BuildingCard buildingCard) {
                int realCost = buildingCard.calculateRealCost(player.getTribe().calculateBuildingDiscount());
                if (simulatedFood < realCost) return false;
                simulatedFood -= realCost;
            }
            slot.clearCard();
        }
        return true;
    }

    // ---- helpers ----

    private static int countSelectableCards(List<RowSlot> slots) {
        return (int) slots.stream().filter(RowSlot::isSelectable).count();
    }

    private static List<RowSlot> cloneSlots(List<RowSlot> slots) {
        List<RowSlot> copy = new ArrayList<>(slots.size());
        for (RowSlot slot : slots) {
            copy.add(new RowSlot(slot.getIndex(), slot.getCard()));
        }
        return copy;
    }
}