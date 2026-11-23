package io.github.cerneabogdanandrei.autoservice.vehicle;

import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;

/**
 * Builds {@link Vehicle} instances for unit tests (the entity has no public constructor).
 */
public final class Vehicles {

    private Vehicles() {
    }

    public static Vehicle vehicle(Long id) {
        try {
            Constructor<Vehicle> constructor = Vehicle.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Vehicle vehicle = constructor.newInstance();
            ReflectionTestUtils.setField(vehicle, "id", id);
            vehicle.setVin("WVWZZZ1JZXW%06d".formatted(id));
            vehicle.setLicensePlate("B-%02d-ABC".formatted(id));
            vehicle.setMake("Volkswagen");
            vehicle.setModel("Golf");
            vehicle.setManufactureYear(2019);
            return vehicle;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
