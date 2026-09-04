package xyz.sterenn.secondbrain;

import org.springframework.boot.SpringApplication;

public class TestSecondBrainApplication {

    public static void main(String[] args) {
        SpringApplication.from(SecondBrainApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
