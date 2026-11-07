package ru.itis.stylemap.heaptest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@SpringBootApplication
@EnableScheduling
public class HeapTestApplication {
    public static void main(String[] args) { SpringApplication.run(HeapTestApplication.class, args); }

    @Bean
    HeapEngine heapEngine() {
        return new HeapEngine(new JvmMemory(), new JvmMemory.ByteArrayRetainer(), System::nanoTime);
    }

    @Component
    static class Supervisor {
        private final HeapEngine engine;
        Supervisor(HeapEngine engine) { this.engine = engine; }
        @Scheduled(fixedDelay = 100, initialDelay = 1000)
        void check() { engine.tick(); }
    }
}
