package com.freightfox.dispatch.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HaversineCalculatorTest {

    @Test
    void samePointIsZero() {
        assertThat(HaversineCalculator.distanceKm(28.6139, 77.2090, 28.6139, 77.2090)).isZero();
    }

    @Test
    void connaughtPlaceToNoidaSector18() {
        assertThat(HaversineCalculator.distanceKm(28.6139, 77.2090, 28.5355, 77.3910))
                .isCloseTo(19.8, within(0.1));
    }

    @Test
    void bangaloreToChennai() {
        assertThat(HaversineCalculator.distanceKm(12.9716, 77.5946, 13.0827, 80.2707))
                .isCloseTo(290.2, within(0.5));
    }

    @Test
    void isSymmetric() {
        double ab = HaversineCalculator.distanceKm(12.9716, 77.5946, 13.0827, 80.2707);
        double ba = HaversineCalculator.distanceKm(13.0827, 80.2707, 12.9716, 77.5946);
        assertThat(ab).isEqualTo(ba, within(1e-9));
    }

    @Test
    void oneDegreeOfLatitudeIsAbout111Km() {
        assertThat(HaversineCalculator.distanceKm(0, 0, 1, 0)).isCloseTo(111.19, within(0.05));
    }

    @Test
    void antipodalPointsAreHalfTheCircumference() {
        assertThat(HaversineCalculator.distanceKm(0, 0, 0, 180)).isCloseTo(Math.PI * 6371.0, within(0.01));
    }
}
