package mesos.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerCountTest {

    @Test
    void from_two_returnsTWO() {
        assertEquals(PlayerCount.TWO, PlayerCount.from(2));
    }

    @Test
    void from_three_returnsTHREE() {
        assertEquals(PlayerCount.THREE, PlayerCount.from(3));
    }

    @Test
    void from_four_returnsFOUR() {
        assertEquals(PlayerCount.FOUR, PlayerCount.from(4));
    }

    @Test
    void from_five_returnsFIVE() {
        assertEquals(PlayerCount.FIVE, PlayerCount.from(5));
    }

    @Test
    void from_one_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> PlayerCount.from(1));
    }

    @Test
    void from_zero_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> PlayerCount.from(0));
    }

    @Test
    void from_six_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> PlayerCount.from(6));
    }

    @Test
    void from_negative_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> PlayerCount.from(-1));
    }

    @Test
    void toInt_TWO_returnsTwo() {
        assertEquals(2, PlayerCount.TWO.toInt());
    }

    @Test
    void toInt_THREE_returnsThree() {
        assertEquals(3, PlayerCount.THREE.toInt());
    }

    @Test
    void toInt_FOUR_returnsFour() {
        assertEquals(4, PlayerCount.FOUR.toInt());
    }

    @Test
    void toInt_FIVE_returnsFive() {
        assertEquals(5, PlayerCount.FIVE.toInt());
    }
}
