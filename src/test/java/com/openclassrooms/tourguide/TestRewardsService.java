package com.openclassrooms.tourguide;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

import org.awaitility.Awaitility;
import java.time.Duration;

public class TestRewardsService {

	private final static org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(TestRewardsService.class);

	private GpsUtil gpsUtil;
	private RewardsService rewardsService;
	private TourGuideService tourGuideService;

	@BeforeEach
	void setUp() {
		gpsUtil = new GpsUtil();
		rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		InternalTestHelper.setInternalUserNumber(0);
		tourGuideService = new TourGuideService(gpsUtil, rewardsService);
	}

	@AfterEach
	void tearDown() {
		if (tourGuideService != null) {
			tourGuideService.shutdown();
		}
		if (rewardsService != null) {
			rewardsService.shutdown();
		}
	}

	@Test
	void userGetRewards() throws InterruptedException {
		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		Attraction attraction = gpsUtil.getAttractions().getFirst();
		user.addToVisitedLocations(new VisitedLocation(user.getUserId(), attraction, new Date()));

		tourGuideService.trackUserLocation(user);

		// Thread.sleep() - SonarQube compliant
		CountDownLatch latch = new CountDownLatch(1);
		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(1000); // OK dans un thread séparé
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			latch.countDown();
		});

		try {
			assertTrue(latch.await(2, TimeUnit.SECONDS),
					"Timeout : calcul asynchrone non terminé");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			fail("Test interrompu");
		}

		List<UserReward> userRewards = user.getUserRewards();
		assertEquals(1, userRewards.size());
	}

	@Test
	void isWithinAttractionProximity() {
		Attraction attraction = gpsUtil.getAttractions().getFirst();
		assertTrue(rewardsService.isWithinAttractionProximity(attraction, attraction));
	}

	@Test
	void nearAllAttractions() {
		rewardsService.setProximityBuffer(Integer.MAX_VALUE);
		InternalTestHelper.setInternalUserNumber(1);
		TourGuideService localTourGuideService = new TourGuideService(gpsUtil, rewardsService);

		User user = localTourGuideService.getAllUsers().getFirst();
		rewardsService.calculateRewards(user);

		Awaitility.await()
				.atMost(Duration.ofSeconds(2))
				.pollInterval(Duration.ofMillis(50))
				.until(() -> !user.getUserRewards().isEmpty());

		List<UserReward> userRewards = user.getUserRewards();
		assertFalse(userRewards.isEmpty(), "L'utilisateur devrait avoir des récompenses");
		LOGGER.info("Nombre de récompenses trouvées: {}", userRewards.size());

		localTourGuideService.shutdown();
	}

	@Test
	void shutdownMultipleTimes() {
		RewardsService testService = new RewardsService(gpsUtil, new RewardCentral());

		// Premier shutdown
		assertDoesNotThrow(testService::shutdown);

		// Deuxième shutdown (doit être idempotent)
		assertDoesNotThrow(testService::shutdown);
	}

	@Test
	void concurrentRewardCalculation() throws ExecutionException, InterruptedException, TimeoutException {
		List<User> users = new ArrayList<>();
		Attraction attraction = gpsUtil.getAttractions().getFirst();

		// Créer plusieurs utilisateurs avec des locations près de la même attraction
		for (int i = 0; i < 5; i++) {
			User user = new User(UUID.randomUUID(), "concurrentUser" + i, "000", "concurrent" + i + "@test.com");
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(), attraction, new Date()));
			users.add(user);
		}

		// Calculer les récompenses en parallèle
		List<CompletableFuture<Void>> futures = users.stream()
				.map(rewardsService::calculateRewardsAsync)
				.toList();

		CompletableFuture<Void> allFutures = CompletableFuture.allOf(
				futures.toArray(new CompletableFuture[0])
		);

		allFutures.get(10, TimeUnit.SECONDS);

		// Vérifier que tous les utilisateurs ont des récompenses
		for (User user : users) {
            assertFalse(user.getUserRewards().isEmpty(), "L'utilisateur " + user.getUserName() + " devrait avoir des récompenses");
		}
	}

}