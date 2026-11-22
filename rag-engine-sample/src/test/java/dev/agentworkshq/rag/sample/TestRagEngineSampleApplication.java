package dev.agentworkshq.rag.sample;

import org.springframework.boot.SpringApplication;

public class TestRagEngineSampleApplication {

	public static void main(String[] args) {
		SpringApplication.from(RagEngineSampleApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
