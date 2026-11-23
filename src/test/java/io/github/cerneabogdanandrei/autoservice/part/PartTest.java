package io.github.cerneabogdanandrei.autoservice.part;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartTest {

    @Test
    void takeAndReturnStock() {
        Part part = Parts.part(1L, "FLT-001", "49.90", 5);

        part.takeFromStock(5);
        assertThat(part.getStock()).isZero();

        part.returnToStock(2);
        assertThat(part.getStock()).isEqualTo(2);
    }

    @Test
    void stockCannotGoNegative() {
        Part part = Parts.part(1L, "FLT-001", "49.90", 1);

        assertThatThrownBy(() -> part.takeFromStock(2)).isInstanceOf(IllegalArgumentException.class);
        assertThat(part.getStock()).isEqualTo(1);
    }

    @Test
    void quantitiesMustBePositive() {
        Part part = Parts.part(1L, "FLT-001", "49.90", 1);

        assertThatThrownBy(() -> part.takeFromStock(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> part.returnToStock(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
