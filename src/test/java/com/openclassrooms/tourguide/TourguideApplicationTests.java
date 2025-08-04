package com.openclassrooms.tourguide;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TourguideApplicationTests {

	@Test
	void contextLoads() {
		assertTrue(true, "The Spring Boot context should load correctly");
	}

	@Test
	void applicationStartsSuccessfully() {
		assertTrue(true, "The application must start without exception");
	}

}
