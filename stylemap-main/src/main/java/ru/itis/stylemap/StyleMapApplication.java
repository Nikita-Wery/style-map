package ru.itis.stylemap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class StyleMapApplication {

    public static void main(String[] args) {
        SpringApplication.run(StyleMapApplication.class, args);

    }

}
