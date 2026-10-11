package mesos.db;

import java.io.Serializable;

/**
 * Aggregated leaderboard entry: one record per player across all games
 * with the same number of players. Sent inside {@link mesos.network.server.RankingMessage}
 * at the end of each game.
 *
 * Holds the cumulative total score and the number of games played by a given nickname,
 * as produced by a {@code GROUP BY nickname} query on the {@code game_results} table.
 */
public record ScoreResult(
        String nickname,     // player's nickname
        int    totalScore,   // sum of all final scores across games with numPlayers players
        int    numPlayers,   // number of players filter used to build this ranking
        int    gamesPlayed   // number of games contributing to totalScore
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
