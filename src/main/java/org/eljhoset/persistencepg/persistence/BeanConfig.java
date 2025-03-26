package org.eljhoset.persistencepg.persistence;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.ConfigurableConversionService;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class BeanConfig {
    private final ConfigurableConversionService configurableConversionService;

    @PostConstruct
    public void init() {
        configurableConversionService.addConverter(State.class, String.class, State::name);
        configurableConversionService.addConverter(String.class, State.class, State::valueOf);
        configurableConversionService.addConverter(Balance.class, Map.class, balance -> Map.of(
                "balance", balance.value(),
                "currency", balance.currency()
        ));
    }
    @Bean
    public ConversionAwareUpdatableJdbcClient conversionAwareJdbcClient(DataSource dataSource, ConversionService conversionService) {
        return new ConversionAwareUpdatableJdbcClient(dataSource, conversionService);
    }
}
