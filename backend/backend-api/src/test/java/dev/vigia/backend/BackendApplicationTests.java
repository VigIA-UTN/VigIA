package dev.vigia.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

@SpringBootTest

class BackendApplicationTests {

    @MockBean
    private ConnectionFactory connectionFactory;

	@Test
	void contextLoads() {
	}

}
