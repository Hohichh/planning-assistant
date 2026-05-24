package io.hohichh.planning_assistant;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Requires running PostgreSQL — use Testcontainers for integration tests")
class PlanningAssistantApplicationTests {

	@Test
	void contextLoads() {
	}

}
