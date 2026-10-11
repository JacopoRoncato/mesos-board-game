package mesos.client.ui.widget;

import java.awt.GridLayout;
import java.util.EnumMap;
import javax.swing.JPanel;
import mesos.client.ui.model.GameSnapshot;
import mesos.client.ui.model.TribeCounts;
import mesos.client.ui.resource.Theme;
import mesos.enums.CharacterType;

/**
 * CORE always-visible tribe summary (the "iconcine"): a compact grid of the six
 * character-type tokens plus a building token, each with its count. Reads from a
 * player's tribe via {@link TribeCounts}.
 *
 * <p>Self-contained and independent of the (removable) card-expansion module.
 */
public final class TribeCountsBar extends JPanel {

    private static final CharacterType[] ORDER = {
        CharacterType.HUNTER, CharacterType.SHAMAN, CharacterType.ARTIST,
        CharacterType.BUILDER, CharacterType.INVENTOR, CharacterType.GATHERER
    };

    private final EnumMap<CharacterType, TypeTokenBadge> tokens = new EnumMap<>(CharacterType.class);
    private final BuildingBadge buildings = new BuildingBadge(0);

    public TribeCountsBar() {
        setOpaque(false);
        // Single tidy row of the 7 tokens (6 character types + buildings):
        // GridLayout(1,7) gives equal cells with no orphan. The old
        // GridLayout(0,4) wrapped 7 cells to 4+3, leaving a dangling empty cell.
        setLayout(new GridLayout(1, 7, Theme.scale(3), Theme.scale(3)));
        for (CharacterType t : ORDER) {
            TypeTokenBadge b = new TypeTokenBadge(t, 0);
            tokens.put(t, b);
            add(b);
        }
        add(buildings);   // 7th cell: 6 character types + buildings
    }

    /** Refreshes all token counts from the given player's tribe. */
    public void update(GameSnapshot.PlayerState player) {
        TribeCounts c = TribeCounts.of(player == null ? null : player.tribe);
        for (CharacterType t : ORDER) tokens.get(t).setCount(c.characters(t));
        buildings.setCount(c.buildings());
    }
}
