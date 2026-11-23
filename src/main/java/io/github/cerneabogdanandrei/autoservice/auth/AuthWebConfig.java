package io.github.cerneabogdanandrei.autoservice.auth;

import io.github.cerneabogdanandrei.autoservice.common.security.AuthenticatedUser;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration(proxyBeanMethods = false)
public class AuthWebConfig implements WebMvcConfigurer {

    static {
        // AuthenticatedUser is resolved from the token, not sent by clients: hide it from the OpenAPI docs.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(AuthenticatedUser.class);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthenticatedUserArgumentResolver());
    }
}
