package mesos.db;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import mesos.player.Player;

/**
 * Handles all MySQL database operations: creating the database if it does not exist,
 * creating the {@code game_results} table if it does not exist, saving end-of-game
 * results, and reading the global leaderboard.
 */
public class ScoreRepository {

    /** Name of the MySQL database managed by this repository. */
    public static final String DB_NAME = "GC43_Mesos_DB";

    private final Connection conn;

    /**
     * Opens a connection to MySQL, creates the {@code GC43_Mesos_DB} database if it
     * does not exist, and then creates the {@code game_results} table if it does not exist.
     *
     * @param mysqlHost MySQL host, optionally with port (e.g. "localhost" or "localhost:3306");
     *                  a full JDBC URL (e.g. "jdbc:mysql://localhost:3306/mesos") is also accepted
     * @param user      MySQL username
     * @param pass      MySQL password
     * @throws SQLException if the connection fails or the schema cannot be initialised
     */
    public ScoreRepository(String mysqlHost, String user, String pass) throws SQLException {
        // mysqlHost accepts "localhost", "localhost:3306", or a full JDBC URL
        String normalized = mysqlHost;
        if (mysqlHost.contains("://")) {
            // Extract the host:port part from the URL, discarding scheme and path
            String afterSlashes = mysqlHost.substring(mysqlHost.indexOf("://") + 3);
            int slashIdx = afterSlashes.indexOf('/');
            normalized = slashIdx >= 0 ? afterSlashes.substring(0, slashIdx) : afterSlashes;
        }

        String host;
        int port;
        if (normalized.contains(":")) {
            String[] parts = normalized.split(":", 2);
            host = parts[0];
            port = Integer.parseInt(parts[1]);
        } else {
            host = normalized;
            port = 3306;
        }

        String baseUrl = "jdbc:mysql://" + host + ":" + port + "/";

        // Step 1: connect without a database name to create the database if absent
        try (Connection root = DriverManager.getConnection(baseUrl, user, pass)) {
            root.createStatement().execute(
                "CREATE DATABASE IF NOT EXISTS `" + DB_NAME + "` "
                + "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            System.out.println("[db] database " + DB_NAME + " verificato/creato");
        }

        // Step 2: open a persistent connection to the specific database
        this.conn = DriverManager.getConnection(baseUrl + DB_NAME, user, pass);
        initSchema();
    }

    /**
     * Reads {@code schema.sql} from the classpath and executes its
     * {@code CREATE TABLE IF NOT EXISTS} statement. Idempotent: safe to call on every
     * server start.
     *
     * @throws SQLException if the schema file is missing or the statement fails
     */
    private void initSchema() throws SQLException {
        try (InputStream is = ScoreRepository.class.getResourceAsStream("/sql/schema.sql")) {
            if (is == null) throw new SQLException("schema.sql not found in classpath");

            String sql = new BufferedReader(new InputStreamReader(is))
                    .lines()
                    .reduce("", (a, b) -> a + "\n" + b);

            try (var stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        } catch (Exception e) {
            throw new SQLException("Failed to init schema: " + e.getMessage(), e);
        }
    }

    /**
     * Persists one row per player in the {@code game_results} table. Called when the
     * game phase transitions to GAME_OVER.
     *
     * @param ranking    players already sorted by score (from {@code game.getRanking()})
     * @param numPlayers number of players in the finished game
     * @throws SQLException if the insert fails
     */
    public void saveResults(List<Player> ranking, int numPlayers) throws SQLException {
        String gameId = java.util.UUID.randomUUID().toString();
        String sql = "INSERT INTO game_results (game_id, nickname, score, num_players) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Player p : ranking) {
                ps.setString(1, gameId);
                ps.setString(2, p.getId());
                ps.setInt(3, p.getPrestigePoints());
                ps.setInt(4, numPlayers);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Returns the aggregated leaderboard for a given player count: one entry per
     * player with the cumulative sum of all their final scores. Ordered by total
     * score descending (highest first = position 1).
     *
     * @param numPlayers filter — only games with this number of players are counted
     * @return list of {@link ScoreResult} ordered highest total score first
     */
    public List<ScoreResult> getRanking(int numPlayers) throws SQLException {
        String sql = "SELECT nickname, SUM(score) AS total_score, "
                   + "num_players, COUNT(*) AS games_played "
                   + "FROM game_results WHERE num_players = ? "
                   + "GROUP BY nickname, num_players ORDER BY total_score DESC";
        List<ScoreResult> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, numPlayers);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new ScoreResult(
                            rs.getString("nickname"),
                            rs.getInt("total_score"),
                            rs.getInt("num_players"),
                            rs.getInt("games_played")
                    ));
                }
            }
        }
        return result;
    }

    /**
     * Closes the database connection. Registered as a shutdown hook in {@code ServerMain}
     * so it is called when the server shuts down.
     */
    public void close() {
        try { conn.close(); } catch (SQLException ignored) {}
    }
}
