package org.eljhoset.persistencepg;

import org.springframework.boot.SpringApplication;

public class TestPersistencePgApplication {

    public static void main(String[] args) {
        SpringApplication.from(PersistencePgApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
