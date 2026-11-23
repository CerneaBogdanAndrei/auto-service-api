package io.github.cerneabogdanandrei.autoservice.common.exception;

/**
 * Thrown when a requested resource does not exist. Mapped to 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, Object id) {
        super("%s with id %s was not found".formatted(resourceName, id));
    }
}
