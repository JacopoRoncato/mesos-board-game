package mesos.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import mesos.game.Game;

/**
 * Handles crash-recovery persistence for the game state.
 * Saves the game to disk after each placement phase and deletes the file on normal game-over.
 * Uses an atomic write (write to {@code .tmp}, then rename) to avoid corrupt files on crash.
 */
public class GamePersistence {
    private final Path saveFile;

    /**
     * Creates a persistence helper for the given save file path.
     *
     * @param saveFile path to the save file (created on first save)
     */
    public GamePersistence(Path saveFile) {
        this.saveFile = saveFile;
    }

    /**
     * Atomically saves the game: writes to a {@code .tmp} file then renames.
     * Avoids corrupt files if the JVM crashes during the write.
     *
     * @param game the game to persist
     */
    public void save(Game game) {
        Path tmp = saveFile.resolveSibling(saveFile.getFileName() + ".tmp");
        try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(tmp))) {
            oos.writeObject(game);
            Files.move(tmp, saveFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("[persistence] save failed: " + e.getMessage());
        }
    }

    /**
     * Loads and returns the saved game, or an empty Optional if the file is missing or corrupt.
     *
     * @return the deserialized game, or {@code Optional.empty()}
     */
    public Optional<Game> load() {
        if (!Files.exists(saveFile)) return Optional.empty();
        try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(saveFile))) {
            return Optional.of((Game) ois.readObject());
        } catch (Exception e) {
            System.err.println("[persistence] save file unreadable, starting fresh: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Deletes the save file.
     * Called on normal game-over or when the recovery timeout expires.
     */
    public void delete() {
        try { Files.deleteIfExists(saveFile); } catch (IOException ignored) {}
    }
}
