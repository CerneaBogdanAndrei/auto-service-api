package io.github.cerneabogdanandrei.autoservice.common.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BaseEntityTest {

    static class Widget extends BaseEntity {
    }

    static class Gadget extends BaseEntity {
    }

    private static <T extends BaseEntity> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, BaseEntity.class, "id", id, Long.class);
        return entity;
    }

    @Test
    void entitiesWithSameIdAndTypeAreEqual() {
        assertThat(withId(new Widget(), 1L)).isEqualTo(withId(new Widget(), 1L));
    }

    @Test
    void entitiesWithDifferentIdsAreNotEqual() {
        assertThat(withId(new Widget(), 1L)).isNotEqualTo(withId(new Widget(), 2L));
    }

    @Test
    void entitiesOfDifferentTypesAreNotEqualEvenWithSameId() {
        assertThat(withId(new Widget(), 1L)).isNotEqualTo(withId(new Gadget(), 1L));
    }

    @Test
    void transientEntitiesAreOnlyEqualToThemselves() {
        Widget widget = new Widget();

        assertThat(widget).isEqualTo(widget).isNotEqualTo(new Widget()).isNotEqualTo(null);
    }

    @Test
    void hashCodeIsStableWhenIdIsAssignedAfterInsertion() {
        Widget widget = new Widget();
        Set<Widget> set = new HashSet<>(Set.of(widget));

        withId(widget, 7L);

        assertThat(set).contains(widget);
    }
}
