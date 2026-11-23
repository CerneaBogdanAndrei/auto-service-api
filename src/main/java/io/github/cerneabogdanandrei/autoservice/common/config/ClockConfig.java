package io.github.cerneabogdanandrei.autoservice.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Single source of "now" for the application. Business rules that depend on the current
 * time (e.g. "no appointments in the past") use this bean so tests can control time.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
