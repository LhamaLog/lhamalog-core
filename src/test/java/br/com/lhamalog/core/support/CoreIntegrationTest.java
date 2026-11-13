package br.com.lhamalog.core.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import br.com.lhamalog.core.TestcontainersConfiguration;

/**
 * Full application test with Testcontainers (fixed tags) and the Clerk test wiring. Using the same annotation
 * everywhere keeps a single cached application context.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, ClerkTestConfiguration.class })
public @interface CoreIntegrationTest {

}
