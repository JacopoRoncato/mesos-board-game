package mesos.game;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mesos.board.Board;
import mesos.board.RowRefillResult;
import mesos.cards.events.EventCard;
import mesos.enums.Era;
import mesos.enums.GamePhase;
import mesos.enums.PlayerCount;
import mesos.offer.OfferTile;
import mesos.player.Player;
import mesos.player.Totem;

/**
 * Top-level orchestrator: owns the Board, the players, the round counter
 * and the {@link GamePhase} state machine. Fires PropertyChangeEvents for
 * PHASE / ERA / ROUND so the (Virtual) View can mirror state remotely.
 */
public class Game implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int MAX_ROUNDS = 10;

    private final PlayerCount playerCount;
    private final List<Player> players;
    private final Board board;

    private int currentRound;
    private Era currentEra;
    private GamePhase phase;
    private boolean deckExhaustedFlag = false;
    private transient PropertyChangeListener listener;
    private final Set<String> finishedThisRound = new HashSet<>();
    private int placementTurnIndex = 0;
    private List<Player> executionOrder = new ArrayList<>();
    private int executionTurnIndex = 0;
    private String extraPickPendingId = null;

    /**
     * Creates a new game for the given players.
     * The phase is {@link GamePhase#SETUP}; call {@link #startGame()} to proceed.
     *
     * @param playerCount the number of players
     * @param players     the list of players (order will be randomised during setup)
     */
    public Game(PlayerCount playerCount, List<Player> players) {
        this.playerCount = playerCount;
        this.players = new ArrayList<>(players);
        this.currentRound = 1;
        this.currentEra = Era.I;
        this.phase = GamePhase.SETUP;
        this.board = new Board();
    }

    /**
     * Registers the listener that receives a {@link PropertyChangeEvent} after every
     * state mutation. Also propagates the listener to all players.
     *
     * @param listener the listener to register
     */
    public void setListener(PropertyChangeListener listener) {
        this.listener = listener;
        for (Player p : players) {
            p.setListener(listener);
        }
    }

    private void fire() {
        if (listener != null)
            listener.propertyChange(new PropertyChangeEvent(this, "update", null, null));
    }

    private void setPhase(GamePhase newPhase) {
        GamePhase old = this.phase;
        this.phase = newPhase;
        if (old != newPhase) fire();
    }

    private void setCurrentEra(Era newEra) {
        Era old = this.currentEra;
        this.currentEra = newEra;
        if (old != newEra) fire();
    }

    private void setCurrentRound(int newRound) {
        int old = this.currentRound;
        this.currentRound = newRound;
        if (old != newRound) fire();
    }

    // ---- setup ----

    /**
     * Builds decks, configures the board, randomises the initial turn order,
     * reveals the initial rows, and distributes initial food.
     * Transitions the phase to {@link GamePhase#PLACEMENT}.
     */
    public void setup() {
        board.buildTribeDeck(playerCount);
        board.buildBuildingDecks(playerCount);
        board.configureOfferTrack(playerCount);
        board.configureTurnOrderTile(playerCount);
        initializeTurnOrder();
        board.revealInitialRows(playerCount);
        board.revealInitialBuildings(playerCount);
        assignInitialFood();
        placementTurnIndex = 0;
        setPhase(GamePhase.PLACEMENT);
    }

    private void initializeTurnOrder() {
        List<Player> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);
        for (Player player : shuffled) {
            board.getTurnOrderTile().addTotem(player.getTotem(), player);
        }
    }


    /**
     * Distributes initial food to players based on their position in the turn order.
     * The first player receives 2 food, positions 2–3 receive 3, and later positions receive 4.
     */
    public void assignInitialFood() {
        List<Player> order = board.getTurnOrderTile().determineNewOrder();
        if (order.isEmpty()) {
            order = new ArrayList<>(players);
        }
        for (int i = 0; i < order.size(); i++) {
            int food = (i == 0) ? 2 : (i <= 2 ? 3 : 4);
            order.get(i).gainFood(food);
        }
    }

    /**
     * Starts the game. Calls {@link #setup()} if the phase is still {@link GamePhase#SETUP};
     * idempotent if setup has already run.
     */
    public void startGame() {
        if (phase == GamePhase.SETUP) {
            setup();
        }
    }

    // ---- placement ----

    /**
     * Moves the player's totem from the Turn Order Tile to the chosen Offer Tile
     * and records the selection. Returns true on success; false if the tile is
     * unknown, occupied, or null. Single Model entry-point so the listener
     * mechanism (and any future per-totem events) can hang off this method.
     *
     * @param player the player placing their totem
     * @param tile   the offer tile to place on
     * @return {@code true} if placement succeeded
     */
    public boolean placeTotem(Player player, OfferTile tile) {
        if (player == null) return false;
        if (!board.getOfferTrack().placeTotem(player, tile)) {
            return false;
        }
        board.getTurnOrderTile().clearTotem(player.getTotem());
        player.chooseOfferTile(tile);
        placementTurnIndex++;
        fire();
        boolean allPlaced = players.stream().allMatch(p -> p.getSelectedOfferTile() != null);
        if (allPlaced) actionPhase();
        return true;
    }

    // ---- action resolution ----

    /**
     * Transitions to {@link GamePhase#ACTION_RESOLUTION} and records each player's
     * selected tile in resolution order (A → G).
     */
    public void actionPhase() {
        finishedThisRound.clear();
        executionOrder = new ArrayList<>();
        executionTurnIndex = 0;
        for (OfferTile tile : board.getOfferTrack().getTilesInResolutionOrder()) {
            if (tile.getOccupiedBy() == null) continue;
            Player activePlayer = findPlayerByTotem(tile.getOccupiedBy());
            if (activePlayer != null) {
                executionOrder.add(activePlayer);
            }
        }
        setPhase(GamePhase.ACTION_RESOLUTION);
    }

    /** Signals that the given player is waiting for their B020 extra top-row pick. */
    public void setExtraPickPending(Player player) {
        extraPickPendingId = player.getId();
        fire();
    }

    /** Returns the id of the player currently awaiting an extra pick, or {@code null}. */
    public String getExtraPickPendingId() { return extraPickPendingId; }

    /**
     * Marks the given player as having finished their action for this round.
     * When all players are done, automatically triggers {@link #endOfRound()}.
     *
     * @param player the player who completed their action
     */
    public void markPlayerDone(Player player) {
        finishedThisRound.add(player.getId());
        executionTurnIndex++;
        extraPickPendingId = null;
        // broadcast after every execute: ensures clients see updated rows
        // and tile="-" even for players that gain no food/PP (no implicit fire)
        fire();
        if (finishedThisRound.size() >= players.size()) {
            finishedThisRound.clear();
            endOfRound();
        }
    }

    /**
     * Resolves the end of a round: notifies buildings, then either resolves the final round
     * (when the deck is exhausted or round 10 is complete) or a normal round.
     */
    public void endOfRound() {
        setPhase(GamePhase.END_OF_ROUND);

        // Notify buildings (applies to both normal and final rounds)
        for (Player player : players) {
            player.getTribe().notifyBuildingsOnEndOfActionPhase(player, board);
        }

        boolean isFinalRound = deckExhaustedFlag || currentRound >= MAX_ROUNDS;
        if (isFinalRound) {
            resolveFinalRound();
        } else {
            resolveNormalRound();
        }
    }

    private void resolveNormalRound() {
        // Resolve bottom-row events only, then refill
        List<EventCard> events = board.sortEventsForResolution(board.getActiveRoundEvents());
        for (EventCard event : events) {
            event.resolveEvent(players);
        }
        RowRefillResult result = board.refillTopRow(playerCount, currentEra);
        handleRefillResult(result);
        if (phase != GamePhase.GAME_OVER) {
            setCurrentRound(currentRound + 1);
            for (Player p : players) p.clearSelectedOfferTile();
            List<Player> newOrder = board.getTurnOrderTile().determineNewOrder();
            if (!newOrder.isEmpty()) {
                players.clear();
                players.addAll(newOrder);
            }
            placementTurnIndex = 0;
            setPhase(GamePhase.PLACEMENT);
        }
    }

    private void resolveFinalRound() {
        // Collect ALL events from BOTH rows, sort together (Sustenance always
        // last), resolve once. No row discard/move/refill.
        List<EventCard> allEvents = board.sortEventsForResolution(board.getAllVisibleEvents());
        for (EventCard event : allEvents) {
            event.resolveEvent(players);
        }
        triggerEndGame();
    }

    /**
     * Applies the result of a row refill: handles era transitions and sets the
     * deck-exhausted flag if applicable.
     *
     * @param result the result returned by {@link Board#refillTopRow}
     */
    public void handleRefillResult(RowRefillResult result) {
        if (result == null) return;
        if (result.isEraTransitionTriggered()) {
            setCurrentEra(result.getNewEra());
            board.applyEraTransition(currentEra);
        }
        if (result.isDeckExhausted()) {
            // Set flag so NEXT round's endOfRound() uses the final-round path
            deckExhaustedFlag = true;
        }
    }

    /**
     * Transitions to final scoring, calculates scores, and sets the phase to
     * {@link GamePhase#GAME_OVER}.
     */
    public void triggerEndGame() {
        setPhase(GamePhase.FINAL_SCORING);
        // Events already resolved by the final-round path in endOfRound()
        calculateFinalScore();
        setPhase(GamePhase.GAME_OVER);
    }

    /** Applies all end-game scoring bonuses to every player. */
    public void calculateFinalScore() {
        ScoreCalculator.awardFinalPrestige(players);
    }

    /**
     * Returns players ordered by tie-broken final score: PP desc, then Food
     * desc (rules §16). Does NOT mutate {@link #getPlayers()}.
     */
    public List<Player> getRanking() {
        List<Player> ranked = new ArrayList<>(players);
        ranked.sort(Comparator.comparingInt(Player::getPrestigePoints)
                .thenComparingInt(Player::getFood)
                .reversed());
        return ranked;
    }

    private Player findPlayerByTotem(Totem totem) {
        return players.stream()
                .filter(player -> player.getTotem().equals(totem))
                .findFirst()
                .orElse(null);
    }

    // ---- getters ----

    /** Returns the current round number (1-based). */
    public int getCurrentRound()    { return currentRound; }

    /**
     * Returns the player whose turn it is to place their totem,
     * or {@code null} if all players have already placed.
     */
    public Player getCurrentPlacementPlayer() {
        return placementTurnIndex < players.size() ? players.get(placementTurnIndex) : null;
    }

    /** Returns the player whose turn it is to execute their action, or {@code null} if all have executed. */
    public Player getCurrentExecutionPlayer() {
        return executionTurnIndex < executionOrder.size() ? executionOrder.get(executionTurnIndex) : null;
    }

    /** Returns the current era. */
    public Era getCurrentEra()      { return currentEra; }

    /** Returns the maximum number of rounds in a game (always 10). */
    public int getMaxRounds()       { return MAX_ROUNDS; }

    /** Returns the player count for this game session. */
    public PlayerCount getPlayerCount() { return playerCount; }

    /** Returns the current game phase. */
    public GamePhase getPhase()     { return phase; }

    /** Returns the board. */
    public Board getBoard()         { return board; }

    /** Returns a defensive copy of the player list in current turn order. */
    public List<Player> getPlayers() {
        return new ArrayList<>(players);
    }
}
