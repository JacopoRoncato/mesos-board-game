package mesos.effects;

import java.util.ArrayList;
import java.util.List;
import mesos.board.Board;
import mesos.board.RowSlot;
import mesos.cards.BuildingCard;
import mesos.cards.Card;
import mesos.cards.characters.CharacterCard;
import mesos.enums.VisibleRow;
import mesos.offer.CardPick;
import mesos.player.Player;

public class ExtraTopRowPickEffect extends BuildingEffect {
    private static final long serialVersionUID = 1L;

    /**
     * Returns the list of valid CardPick options from the top row
     * that the player may choose from (characters and affordable buildings).
     * An empty list means no picks are available. The pick is optional —
     * the player may also choose not to pick at all.
     */
    public List<CardPick> getAvailableTopRowPicks(Player owner, Board board) {
        List<CardPick> picks = new ArrayList<>();
        if (owner == null || board == null) {
            return picks;
        }
        for (RowSlot slot : board.getVisibleRow(VisibleRow.TOP).getSelectableSlots()) {
            Card card = slot.getCard();
            if (card instanceof CharacterCard) {
                picks.add(new CardPick(VisibleRow.TOP, slot.getIndex()));
            } else if (card instanceof BuildingCard buildingCard) {
                int cost = buildingCard.calculateRealCost(owner.getTribe().calculateBuildingDiscount());
                if (owner.getFood() >= cost) {
                    picks.add(new CardPick(VisibleRow.TOP, slot.getIndex()));
                }
            }
        }
        return picks;
    }

    /**
     * Executes the player's chosen pick. The pick must be one of the
     * options returned by getAvailableTopRowPicks(). Pass null to skip.
     *
     * Pre-validates the slot + affordability before removing the card from
     * the row, so a rejected pick can't leave the row in an inconsistent state.
     */
    public void executeTopRowPick(Player owner, Board board, CardPick pick) {
        if (pick == null || owner == null || board == null) {
            return;
        }
        RowSlot slot = board.getVisibleRow(pick.getRow()).getSlot(pick.getSlotIndex());
        if (slot == null || !slot.isSelectable()) {
            return;
        }
        Card preview = slot.getCard();
        if (preview instanceof BuildingCard buildingPreview) {
            int cost = buildingPreview.calculateRealCost(owner.getTribe().calculateBuildingDiscount());
            if (owner.getFood() < cost) {
                return;
            }
        } else if (!(preview instanceof CharacterCard)) {
            return; // events / final-event markers can't be picked
        }

        Card card = board.takeCard(pick);
        if (card instanceof CharacterCard characterCard) {
            owner.acquireCharacter(characterCard);
        } else if (card instanceof BuildingCard buildingCard) {
            owner.acquireBuilding(buildingCard);
        }
    }

    @Override
    public void onEndOfActionPhase(Player owner, Board board) {
        // No-op: the controller must call getAvailableTopRowPicks() and
        // executeTopRowPick() to handle this effect interactively.
    }

    @Override
    public String getDescription() {
        return "EXTRA_TOP_ROW_PICK";
    }
}
