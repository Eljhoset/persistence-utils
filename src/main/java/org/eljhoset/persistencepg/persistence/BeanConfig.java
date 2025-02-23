package org.eljhoset.persistencepg.persistence;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class BeanConfig {
    private final ConfigurableConversionService configurableConversionService;

    @PostConstruct
    public void init() {
        configurableConversionService.addConverter(Account.State.class, String.class, Account.State::name);
        configurableConversionService.addConverter(String.class, Account.State.class, Account.State::valueOf);
        configurableConversionService.addConverter(Account.Balance.class, Map.class, balance -> Map.of(
                "balance", balance.value(),
                "currency", balance.currency()
        ));
    }
    @Bean
    public ConversionAwareUpdatableJdbcClient conversionAwareJdbcClient(DataSource dataSource, ConversionService conversionService) {
        JdbcClient jdbcClient = JdbcClient.create(dataSource);
        return new ConversionAwareUpdatableJdbcClient(jdbcClient, conversionService);
    }
}
