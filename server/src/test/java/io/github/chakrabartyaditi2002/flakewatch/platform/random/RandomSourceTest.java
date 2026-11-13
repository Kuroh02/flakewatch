package io.github.chakrabartyaditi2002.flakewatch.platform.random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RandomSourceTest {

    @Test
    @DisplayName("NFR-QUAL-002: SeededRandomSource produces deterministic values for identical seeds")
    void deterministicSequenceForSeededSource() {
        RandomSource source1 = RandomSource.seeded(42L);
        RandomSource source2 = RandomSource.seeded(42L);

        assertThat(source1.nextInt()).isEqualTo(source2.nextInt());
        assertThat(source1.nextInt(100)).isEqualTo(source2.nextInt(100));
        assertThat(source1.nextLong()).isEqualTo(source2.nextLong());
        assertThat(source1.nextLong(1_000L)).isEqualTo(source2.nextLong(1_000L));
        assertThat(source1.nextDouble()).isEqualTo(source2.nextDouble());
        assertThat(source1.nextDouble(0.8, 1.2)).isEqualTo(source2.nextDouble(0.8, 1.2));

        byte[] bytes1 = new byte[32];
        byte[] bytes2 = new byte[32];
        source1.nextBytes(bytes1);
        source2.nextBytes(bytes2);
        assertThat(bytes1).isEqualTo(bytes2);

        UUID uuid1 = source1.nextUuid();
        UUID uuid2 = source2.nextUuid();
        assertThat(uuid1).isEqualTo(uuid2);
    }

    @Test
    @DisplayName("NFR-QUAL-002: DefaultRandomSource generates values within requested bounds")
    void defaultRandomSourceProducesBoundedValues() {
        RandomSource source = new DefaultRandomSource();

        for (int i = 0; i < 50; i++) {
            int randomInt = source.nextInt(10);
            assertThat(randomInt).isBetween(0, 9);

            long randomLong = source.nextLong(100L);
            assertThat(randomLong).isBetween(0L, 99L);

            double randomDouble = source.nextDouble(10.0, 20.0);
            assertThat(randomDouble).isGreaterThanOrEqualTo(10.0).isLessThan(20.0);
        }

        byte[] bytes = new byte[16];
        source.nextBytes(bytes);
        assertThat(bytes).hasSize(16);

        UUID uuid = source.nextUuid();
        assertThat(uuid).isNotNull();
    }

    @Test
    @DisplayName("NFR-QUAL-002: RandomSource rejects invalid bounds")
    void rejectsInvalidBounds() {
        RandomSource source = new DefaultRandomSource();

        assertThatThrownBy(() -> source.nextInt(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> source.nextLong(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> source.nextDouble(5.0, 5.0)).isInstanceOf(IllegalArgumentException.class);
    }
}
