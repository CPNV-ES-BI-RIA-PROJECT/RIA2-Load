package com.load;

import com.load.config.DotenvInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoadApplicationTest {

    @Test
    void shouldBootstrapSpringApplicationWithDotenvInitializer() {
        /*
         * Feature: Application bootstrap
         * Scenario: Starting the application with the expected Spring Boot configuration
         * Given the load application is launched from its main entry point
         * When Spring Boot starts building the application
         * Then the application registers itself as a source and adds the dotenv initializer before startup continues
         */
        AtomicReference<SpringApplication> capturedApplication = new AtomicReference<>();
        AbortStartup expectedFailure = new AbortStartup();

        AbortStartup actualFailure = assertThrows(
                AbortStartup.class,
                () -> SpringApplication.withHook(
                        application -> new SpringApplicationRunListener() {
                            @Override
                            public void starting(ConfigurableBootstrapContext bootstrapContext) {
                                capturedApplication.set(application);
                                throw expectedFailure;
                            }
                        },
                        () -> LoadApplication.main(new String[]{"--spring.main.banner-mode=off"})
                )
        );

        SpringApplication application = capturedApplication.get();

        assertSame(expectedFailure, actualFailure);
        assertTrue(application.getAllSources().contains(LoadApplication.class));
        assertTrue(application.getInitializers().stream().anyMatch(DotenvInitializer.class::isInstance));
    }

    private static final class AbortStartup extends RuntimeException {
    }
}
