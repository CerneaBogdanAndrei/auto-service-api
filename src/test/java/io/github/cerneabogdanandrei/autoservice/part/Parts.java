package io.github.cerneabogdanandrei.autoservice.part;

import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;

/**
 * Builds {@link Part} instances for unit tests (the entity has no public constructor).
 */
public final class Parts {

    private Parts() {
    }

    public static Part part(Long id, String code, String unitPrice, int stock) {
        try {
            Constructor<Part> constructor = Part.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Part part = constructor.newInstance();
            ReflectionTestUtils.setField(part, "id", id);
            part.setCode(code);
            part.setName("Part " + code);
            part.setUnitPrice(new BigDecimal(unitPrice));
            part.setStock(stock);
            return part;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
