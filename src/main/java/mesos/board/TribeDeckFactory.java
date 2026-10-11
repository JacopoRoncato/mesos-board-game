package mesos.board;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import mesos.cards.TribeDeckCard;
import mesos.cards.characters.Artist;
import mesos.cards.characters.Builder;
import mesos.cards.characters.Gatherer;
import mesos.cards.characters.Hunter;
import mesos.cards.characters.Inventor;
import mesos.cards.characters.Shaman;
import mesos.cards.events.CavePaintingsEvent;
import mesos.cards.events.HuntingEvent;
import mesos.cards.events.ShamanicRitualEvent;
import mesos.cards.events.SustenanceEvent;
import mesos.enums.Era;
import mesos.enums.InventionIcon;
import mesos.enums.PlayerCount;

/**
 * Builds a single Era's Tribe deck slice (6 character types × N copies + 4 events),
 * pre-filtered to those allowed at the given player count.
 *
 * Player-count restrictions are derived from the physical card markers (bottom-right corner)
 * as extracted from the print-ready PDF (docs/immagini carte/Cards_total_front_NO PROMO.pdf,
 * pages 0–95, verified 2026-05-26).
 *
 * The PDF is ordered ERA-first, then TYPE within each era. Events (pages 84–95) are
 * grouped last across all eras; they carry no player-count marker.
 * Era boundaries: I=0–28 (29 cards), II=29–56 (28 cards), III=57–83 (27 cards).
 * Per-type real copy counts per era: Hunter 5/6/4, Builder 4/4/4, Gatherer 4/4/3,
 * Artist 5/4/4, Inventor 7/6/7, Shaman 4/4/5.
 *
 * This factory uses copiesPerType=5 for all types to match Board.CARDS_PER_ERA_TYPE.
 * Where the real count is smaller, the extra copy is tagged FIVE_ONLY (adds one card
 * only for 5-player games). Where the real count is larger, only the first 5 copies
 * are created (some high-index restricted copies are dropped — minor inaccuracy).
 */
final class TribeDeckFactory {

    private static final Set<PlayerCount> ALL_COUNTS    = EnumSet.allOf(PlayerCount.class);
    private static final Set<PlayerCount> THREE_OR_MORE = EnumSet.of(PlayerCount.THREE, PlayerCount.FOUR, PlayerCount.FIVE);
    private static final Set<PlayerCount> FOUR_OR_MORE  = EnumSet.of(PlayerCount.FOUR, PlayerCount.FIVE);
    private static final Set<PlayerCount> FIVE_ONLY     = EnumSet.of(PlayerCount.FIVE);

    private TribeDeckFactory() { }

    static List<TribeDeckCard> createEraSlice(Era era, PlayerCount playerCount, int copiesPerType) {
        List<TribeDeckCard> cards = new ArrayList<>();
        for (int i = 0; i < copiesPerType; i++) {
            cards.add(new Hunter(  "Hunter-"   + era + '-' + i, era, hunterAllowed(era, i),   i % 2 == 0));
            cards.add(new Shaman(  "Shaman-"   + era + '-' + i, era, shamanAllowed(era, i),   (i % 3) + 1));
            cards.add(new Artist(  "Artist-"   + era + '-' + i, era, artistAllowed(era, i)));
            cards.add(new Builder( "Builder-"  + era + '-' + i, era, builderAllowed(era, i),  (i % 3) + 1, (i % 2) + 1));
            cards.add(new Inventor("Inventor-" + era + '-' + i, era, inventorAllowed(era, i),
                    InventionIcon.values()[(era.ordinal() * copiesPerType + i) % InventionIcon.values().length]));
            cards.add(new Gatherer("Gatherer-" + era + '-' + i, era, gathererAllowed(era, i)));
        }
        cards.add(new HuntingEvent(     "Hunting-"   + era, era, ALL_COUNTS, era.ordinal() + 1));
        cards.add(new ShamanicRitualEvent("Shamanic-" + era, era, ALL_COUNTS,
                (era.ordinal() + 1) * 5, era.ordinal() * 2 + 3));
        cards.add(new CavePaintingsEvent("Cave-"      + era, era, ALL_COUNTS,
                era.ordinal() + 1, 2, era.ordinal() + 1));
        cards.add(new SustenanceEvent(  "Sustenance-" + era, era, ALL_COUNTS, era.ordinal() + 1));

        return cards.stream()
                .filter(card -> card.getAllowedPlayerCounts().contains(playerCount))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    // ---- per-type per-era allowed-player-count tables ----
    // Derived from PDF marker scan (2026-05-26). copiesPerType=5.
    // Where real copy count < 5: fabricated copies use FIVE_ONLY.
    // Where real copy count > 5: extra real copies (at high indices) are dropped.

    // Real counts per era: 5 / 6 / 4
    // Era I:   ALL,ALL,ALL,3+,3+           (5 real copies, exact)
    // Era II:  ALL,ALL,3+,ALL,4+           (6 real; 5-only copy at index 5 dropped)
    // Era III: 5-only,ALL,ALL,ALL + fab    (4 real; fabricated index 4 = FIVE_ONLY)
    private static Set<PlayerCount> hunterAllowed(Era era, int i) {
        return switch (era) {
            case I   -> (i >= 3) ? THREE_OR_MORE : ALL_COUNTS;
            case II  -> switch (i) {
                case 2  -> THREE_OR_MORE;
                case 4  -> FOUR_OR_MORE;
                default -> ALL_COUNTS;
            };
            case III -> (i == 0) ? FIVE_ONLY : ALL_COUNTS;
        };
    }

    // Real counts per era: 4 / 4 / 4  (all eras same)
    // Era I:   ALL,ALL,5-only,ALL + fab
    // Era II:  ALL,ALL,3+,ALL + fab
    // Era III: ALL,ALL,5-only,ALL + fab
    private static Set<PlayerCount> builderAllowed(Era era, int i) {
        if (i == 4) return FIVE_ONLY;
        return switch (era) {
            case I, III -> (i == 2) ? FIVE_ONLY      : ALL_COUNTS;
            case II     -> (i == 2) ? THREE_OR_MORE  : ALL_COUNTS;
        };
    }

    // Real counts per era: 4 / 4 / 3
    // Era I:   ALL,ALL,3+,5-only + fab
    // Era II:  ALL,3+,4+,5-only + fab
    // Era III: 5-only,4+,ALL + fab + fab
    private static Set<PlayerCount> gathererAllowed(Era era, int i) {
        if (i >= 3 && era == Era.III) return FIVE_ONLY;
        return switch (era) {
            case I  -> switch (i) {
                case 2  -> THREE_OR_MORE;
                case 3  -> FIVE_ONLY;
                default -> ALL_COUNTS;
            };
            case II -> switch (i) {
                case 1  -> THREE_OR_MORE;
                case 2  -> FOUR_OR_MORE;
                case 3  -> FIVE_ONLY;
                default -> ALL_COUNTS;
            };
            case III -> switch (i) {
                case 0  -> FIVE_ONLY;
                case 1  -> FOUR_OR_MORE;
                default -> ALL_COUNTS;
            };
        };
    }

    // Real counts per era: 5 / 4 / 4
    // Era I:   ALL,ALL,ALL,3+,4+           (5 real copies, exact)
    // Era II:  3+,ALL,ALL,ALL + fab
    // Era III: 5-only,ALL,ALL,ALL + fab
    private static Set<PlayerCount> artistAllowed(Era era, int i) {
        if (i == 4 && era != Era.I) return FIVE_ONLY;
        return switch (era) {
            case I   -> switch (i) {
                case 3  -> THREE_OR_MORE;
                case 4  -> FOUR_OR_MORE;
                default -> ALL_COUNTS;
            };
            case II  -> (i == 0) ? THREE_OR_MORE : ALL_COUNTS;
            case III -> (i == 0) ? FIVE_ONLY      : ALL_COUNTS;
        };
    }

    // Real counts per era: 7 / 6 / 7  (all > 5; extra copies at high indices dropped)
    // Era I:   ALL,ALL,ALL,ALL,4+          (real indices 5,6 also 4+ — dropped)
    // Era II:  ALL,4+,ALL,ALL,ALL          (real index 5 is ALL — dropped)
    // Era III: 4+,3+,3+,ALL,ALL            (real indices 5,6 are ALL — dropped)
    private static Set<PlayerCount> inventorAllowed(Era era, int i) {
        return switch (era) {
            case I   -> (i >= 4) ? FOUR_OR_MORE  : ALL_COUNTS;
            case II  -> (i == 1) ? FOUR_OR_MORE  : ALL_COUNTS;
            case III -> switch (i) {
                case 0  -> FOUR_OR_MORE;
                case 1, 2 -> THREE_OR_MORE;
                default -> ALL_COUNTS;
            };
        };
    }

    // Real counts per era: 4 / 4 / 5
    // Era I:   5-only,ALL,ALL,4+ + fab
    // Era II:  ALL,ALL,5-only,5-only + fab
    // Era III: 3+,ALL,ALL,ALL,4+           (5 real copies, exact)
    private static Set<PlayerCount> shamanAllowed(Era era, int i) {
        if (i == 4 && era != Era.III) return FIVE_ONLY;
        return switch (era) {
            case I  -> switch (i) {
                case 0  -> FIVE_ONLY;
                case 3  -> FOUR_OR_MORE;
                default -> ALL_COUNTS;
            };
            case II -> switch (i) {
                case 2, 3 -> FIVE_ONLY;
                default    -> ALL_COUNTS;
            };
            case III -> switch (i) {
                case 0  -> THREE_OR_MORE;
                case 4  -> FOUR_OR_MORE;
                default -> ALL_COUNTS;
            };
        };
    }
}
