package io.github.chakrabartyaditi2002.flakewatch.platform.random;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RandomConfig {

    @Bean
    @ConditionalOnMissingBean
    public RandomSource randomSource() {
        return new DefaultRandomSource();
    }
}
