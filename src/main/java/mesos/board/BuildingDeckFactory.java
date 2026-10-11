package mesos.board;

import java.util.ArrayList;
import java.util.List;
import mesos.cards.BuildingCard;
import mesos.effects.EndGameScoringEffect;
import mesos.effects.ExtraTopRowPickEffect;
import mesos.effects.NoBuildingEffect;
import mesos.effects.PerCharacterEventBonusEffect;
import mesos.effects.ShamanicRitualEffect;
import mesos.effects.SustenanceDiscountEffect;
import mesos.effects.TriggeredFoodRewardEffect;
import mesos.effects.TurnOrderPlacementEffect;
import mesos.enums.CharacterType;
import mesos.enums.EndGameScoringType;
import mesos.enums.Era;
import mesos.enums.EventType;
import mesos.enums.TriggeredFoodRewardType;

/**
 * Builds the per-era Building decks (21 total: 6 + 7 + 8).
 * Costs, PP values and era assignment verified against physical card images
 * (docs/immagini carte/Cards_total_front_NO PROMO.pdf, pages 96-116).
 *
 * Character type icons verified against card PDF and rulebook (pag. 5):
 *   ARTIST=yellow eye, INVENTOR=teal hand, GATHERER=orange bowl.
 * Era II "Double Shaman Reward": no PP badge on physical card — PP=0 (verified 2026-06-11).
 */
final class BuildingDeckFactory {

    private BuildingDeckFactory() { }

    static List<BuildingCard> createPool(Era era) {
        return switch (era) {
            case I   -> createEraI();
            case II  -> createEraII();
            case III -> createEraIII();
        };
    }

    private static List<BuildingCard> createEraI() {
        List<BuildingCard> pool = new ArrayList<>();
        pool.add(new BuildingCard("Complete Set Food", Era.I, 4, 3,
                new TriggeredFoodRewardEffect(TriggeredFoodRewardType.COMPLETE_SET, 5)));
        pool.add(new BuildingCard("Sustenance Gatherers", Era.I, 4, 4,
                new SustenanceDiscountEffect(CharacterType.GATHERER, 1)));
        pool.add(new BuildingCard("Sustenance Artists", Era.I, 5, 3,
                new SustenanceDiscountEffect(CharacterType.ARTIST, 1)));
        pool.add(new BuildingCard("No Shaman Penalty", Era.I, 5, 2,
                new ShamanicRitualEffect(0, true, false)));
        pool.add(new BuildingCard("Turn Order Food", Era.I, 3, 3,
                new TurnOrderPlacementEffect(1)));
        pool.add(new BuildingCard("Pair Food", Era.I, 3, 4,
                new TriggeredFoodRewardEffect(TriggeredFoodRewardType.MATCHING_INVENTOR_PAIR, 3)));
        return pool;
    }

    private static List<BuildingCard> createEraII() {
        List<BuildingCard> pool = new ArrayList<>();
        pool.add(new BuildingCard("Double Shaman Reward", Era.II, 7, 0,
                new ShamanicRitualEffect(0, false, true)));
        pool.add(new BuildingCard("Extra Shaman Icons", Era.II, 6, 4,
                new ShamanicRitualEffect(3, false, false)));
        pool.add(new BuildingCard("Sustenance Inventors", Era.II, 7, 4,
                new SustenanceDiscountEffect(CharacterType.INVENTOR, 1)));
        pool.add(new BuildingCard("Hunt Bonus", Era.II, 7, 2,
                new PerCharacterEventBonusEffect(EventType.HUNTING, CharacterType.HUNTER, 1, 1)));
        pool.add(new BuildingCard("Double Builders", Era.II, 6, 4,
                new EndGameScoringEffect(EndGameScoringType.DOUBLE_BUILDERS, 0, null)));
        pool.add(new BuildingCard("Cave Art Food", Era.II, 5, 6,
                new PerCharacterEventBonusEffect(EventType.CAVE_PAINTINGS, CharacterType.ARTIST, 1, 0)));
        pool.add(new BuildingCard("Complete Set PP", Era.II, 5, 6,
                new EndGameScoringEffect(EndGameScoringType.PER_COMPLETE_SET, 6, null)));
        return pool;
    }

    private static List<BuildingCard> createEraIII() {
        List<BuildingCard> pool = new ArrayList<>();
        pool.add(new BuildingCard("Hunter Endgame", Era.III, 8, 8,
                new EndGameScoringEffect(EndGameScoringType.PER_CHARACTER_TYPE, 3, CharacterType.HUNTER)));
        pool.add(new BuildingCard("Gatherer Endgame", Era.III, 7, 6,
                new EndGameScoringEffect(EndGameScoringType.PER_CHARACTER_TYPE, 4, CharacterType.GATHERER)));
        pool.add(new BuildingCard("Shaman Endgame", Era.III, 7, 4,
                new EndGameScoringEffect(EndGameScoringType.PER_CHARACTER_TYPE, 4, CharacterType.SHAMAN)));
        pool.add(new BuildingCard("Builder Endgame", Era.III, 6, 3,
                new EndGameScoringEffect(EndGameScoringType.PER_CHARACTER_TYPE, 4, CharacterType.BUILDER)));
        pool.add(new BuildingCard("Artist Endgame", Era.III, 7, 4,
                new EndGameScoringEffect(EndGameScoringType.PER_CHARACTER_TYPE, 4, CharacterType.ARTIST)));
        pool.add(new BuildingCard("Inventor Endgame", Era.III, 6, 6,
                new EndGameScoringEffect(EndGameScoringType.PER_CHARACTER_TYPE, 2, CharacterType.INVENTOR)));
        pool.add(new BuildingCard("Extra Top Pick", Era.III, 9, 3,
                new ExtraTopRowPickEffect()));
        pool.add(new BuildingCard("Flat Endgame", Era.III, 10, 25,
                new NoBuildingEffect()));
        return pool;
    }
}
