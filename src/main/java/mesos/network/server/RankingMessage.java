package mesos.network.server;

import java.util.List;
import mesos.db.ScoreResult;

/**
 * Sent by the server to all clients at the end of a game.
 * Contains the aggregated leaderboard for the number of players in this game:
 * one {@link ScoreResult} per player, ranked by cumulative total score.
 */
public class RankingMessage implements ServerMessage {
    private static final long serialVersionUID = 1L;

    private final List<ScoreResult> entries;

    /**
     * Creates a ranking message.
     *
     * @param entries the leaderboard entries in ranked order
     */
    public RankingMessage(List<ScoreResult> entries) {
        this.entries = entries;
    }

    /** Returns the leaderboard entries. */
    public List<ScoreResult> getEntries() {
        return entries;
    }
}
