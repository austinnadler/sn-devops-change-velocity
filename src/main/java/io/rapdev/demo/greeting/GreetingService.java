package io.rapdev.demo.greeting;

import org.springframework.stereotype.Service;

@Service
public class GreetingService {

    static final String DEFAULT_NAME = "World";
    static final int MAX_NAME_LENGTH = 50;

    public Greeting greet(String name) {
        String resolved = (name == null || name.isBlank()) ? DEFAULT_NAME : name.trim();
        if (resolved.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("name must be " + MAX_NAME_LENGTH + " characters or fewer");
        }
        return new Greeting("Hello, " + resolved + "!");
    }
}
