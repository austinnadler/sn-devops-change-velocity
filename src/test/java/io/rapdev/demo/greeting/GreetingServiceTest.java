package io.rapdev.demo.greeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GreetingServiceTest {

    private final GreetingService service = new GreetingService();

    @Test
    void greetsByName() {
        assertThat(service.greet("RWJBH").message()).isEqualTo("Hello, Austin!");
    }

    @Test
    void trimsWhitespace() {
        assertThat(service.greet("  DevOps  ").message()).isEqualTo("Hello, DevOps!");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void defaultsWhenNameMissing(String name) {
        assertThat(service.greet(name).message()).isEqualTo("Hello, World!");
    }

    @Test
    void acceptsNameAtMaxLength() {
        String name = "a".repeat(GreetingService.MAX_NAME_LENGTH);
        assertThat(service.greet(name).message()).isEqualTo("Hello, " + name + "!");
    }

    @Test
    void rejectsNameOverMaxLength() {
        String name = "a".repeat(GreetingService.MAX_NAME_LENGTH + 1);
        assertThatThrownBy(() -> service.greet(name))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50 characters");
    }
}
