package mesos.network.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.List;
import mesos.network.server.SnapshotMessage.PlayerSnapshot;
import mesos.network.server.SnapshotMessage.TribeCardRef;
import org.junit.jupiter.api.Test;

class SnapshotMessageTribeTest {

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T obj) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ObjectOutputStream o = new ObjectOutputStream(b)) {
            o.writeObject(obj);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(b.toByteArray()))) {
            return (T) in.readObject();
        }
    }

    @Test
    void playerSnapshotTribeSurvivesSerialization() throws Exception {
        List<TribeCardRef> tribe = Arrays.asList(
                new TribeCardRef("CHARACTER", "Hunter-I-0", "HUNTER"),
                new TribeCardRef("BUILDING", "Pair Food", null));
        PlayerSnapshot ps = new PlayerSnapshot("alice", 5, 3, "B", "RED", tribe);

        PlayerSnapshot back = roundTrip(ps);

        assertEquals(2, back.getTribe().size());
        assertEquals("CHARACTER", back.getTribe().get(0).getKind());
        assertEquals("Hunter-I-0", back.getTribe().get(0).getName());
        assertEquals("HUNTER", back.getTribe().get(0).getType());
        assertEquals("BUILDING", back.getTribe().get(1).getKind());
        assertNull(back.getTribe().get(1).getType());
    }

    @Test
    void nullTribeBecomesEmptyList() {
        PlayerSnapshot ps = new PlayerSnapshot("bob", 0, 0, "-", "BLUE", null);
        assertNotNull(ps.getTribe());
        assertTrue(ps.getTribe().isEmpty());
    }
}
