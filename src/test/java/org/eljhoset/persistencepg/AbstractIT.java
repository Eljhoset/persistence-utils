package org.eljhoset.persistencepg;

import org.eljhoset.persistencepg.graphql.repository.DataRepository;
import org.eljhoset.persistencepg.persistence.AccountRepository;
import org.eljhoset.persistencepg.persistence.ConversionAwareUpdatableJdbcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import({TestcontainersConfiguration.class})
public abstract class AbstractIT {
    @Autowired
    protected ConversionAwareUpdatableJdbcClient jdbcClient;
    @Autowired
    protected DataRepository dataRepository;
    @Autowired
    protected AccountRepository accountRepository;
}
