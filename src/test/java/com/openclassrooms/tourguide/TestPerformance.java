package com.openclassrooms.tourguide;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;

import static org.junit.jupiter.api.Assertions.*;

public class TestPerformance {

	private static final Logger LOGGER = LoggerFactory.getLogger(TestPerformance.class);
	private TourGuideService tourGuideService;
	private GpsUtil gpsUtil;
	private RewardsService rewardsService;

	@BeforeEach
	void setUp() {
		gpsUtil = new GpsUtil();
		rewardsService = new RewardsService(gpsUtil, new RewardCentral());
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

	// Méthode pour fournir les différents nombres d'utilisateurs à tester
	private static Stream<Arguments> userCountProvider() {
		return Stream.of(
				Arguments.of(100, 15, 20),    // 100 users, 15s max pour trackLocation, 20s max pour getRewards
				Arguments.of(1000, 30, 40),   // 1000 users
				Arguments.of(10000, 150, 200), // 10000 users
				Arguments.of(100000, 900, 1200) // 100000 users (15min et 20min en secondes)
		);
	}

	@ParameterizedTest
	@MethodSource("userCountProvider")
	void highVolumeTrackLocation(int userCount, int maxTrackTimeSeconds, int maxRewardTimeSeconds) {
		LOGGER.info("======> Start highVolumeTrackLocation with {} users <=======", userCount);

		// Informations système pour contexte
		logSystemInfo();

		InternalTestHelper.setInternalUserNumber(userCount);
		tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		List<User> allUsers = tourGuideService.getAllUsers();
		assertEquals(userCount, allUsers.size(), "Le nombre d'utilisateurs créés doit correspondre");

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		try {
			// Version optimisée avec CompletableFuture et timeout de sécurité
			CompletableFuture<Void> trackingFuture = tourGuideService.trackAllUsersLocation(allUsers);

			// Timeout de sécurité (2x le temps max attendu)
			trackingFuture.get(maxTrackTimeSeconds * 2L, TimeUnit.SECONDS);

		} catch (TimeoutException e) {
			fail(String.format("Timeout: Le tracking a pris plus de %d secondes", maxTrackTimeSeconds * 2));
		} catch (Exception e) {
			fail("Erreur pendant le tracking: " + e.getMessage());
		} finally {
			stopWatch.stop();
			tourGuideService.tracker.stopTracking();
		}

		// Validation des résultats
		long usersWithLocations = allUsers.stream()
				.mapToLong(user -> user.getVisitedLocations().size())
				.sum();
		assertTrue(usersWithLocations > 0, "Au moins quelques utilisateurs devraient avoir des locations");

		// Métriques de performance
		long timeElapsed = TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime());
		double avgTimePerUser = stopWatch.getTime() / (double) userCount;
		double usersPerSecond = userCount / (double) TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime());

		LOGGER.info("highVolumeTrackLocation with {} users: Time Elapsed: {} seconds.", userCount, timeElapsed);
		LOGGER.info("Performance: {} ms per user", String.format("%.3f", avgTimePerUser));
		LOGGER.info("Throughput: {} users/second", String.format("%.1f", usersPerSecond));

		// Assertion avec message informatif
		assertTrue(timeElapsed <= maxTrackTimeSeconds, String.format(
				"Performance dégradée: %d secondes > %d secondes max pour %d utilisateurs (%.3f ms/user)",
				timeElapsed, maxTrackTimeSeconds, userCount, avgTimePerUser));
	}

	@ParameterizedTest
	@MethodSource("userCountProvider")
	void highVolumeGetRewards(int userCount, int maxTrackTimeSeconds, int maxRewardTimeSeconds) {
		LOGGER.info("======> Start highVolumeGetRewards with {} users <=======", userCount);

		logSystemInfo();

		InternalTestHelper.setInternalUserNumber(userCount);
		tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		Attraction attraction = gpsUtil.getAttractions().get(0);
		List<User> allUsers = tourGuideService.getAllUsers();

		// Ajouter des locations visitées près de l'attraction
		allUsers.forEach(u -> u.addToVisitedLocations(
				new VisitedLocation(u.getUserId(), attraction, new Date())));

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		try {
			// Version optimisée avec CompletableFuture et timeout
			CompletableFuture<Void> rewardsFuture = rewardsService.calculateRewardsForAllUsers(allUsers);
			rewardsFuture.get(maxRewardTimeSeconds * 2L, TimeUnit.SECONDS);

		} catch (TimeoutException e) {
			fail(String.format("Timeout: Le calcul des récompenses a pris plus de %d secondes", maxRewardTimeSeconds * 2));
		} catch (Exception e) {
			fail("Erreur pendant le calcul des récompenses: " + e.getMessage());
		} finally {
			stopWatch.stop();
			tourGuideService.tracker.stopTracking();
		}

		// Validation des résultats
		long usersWithRewards = allUsers.stream()
				.mapToLong(user -> user.getUserRewards().size())
				.sum();

		long usersWithoutRewards = allUsers.stream()
				.mapToLong(user -> user.getUserRewards().isEmpty() ? 1 : 0)
				.sum();

		LOGGER.info("Utilisateurs avec récompenses: {}/{}", allUsers.size() - usersWithoutRewards, allUsers.size());
		assertTrue(usersWithRewards > 0, "Au moins quelques utilisateurs devraient avoir des récompenses");

		// Métriques de performance
		long timeElapsed = TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime());
		double avgTimePerUser = stopWatch.getTime() / (double) userCount;
		double usersPerSecond = userCount / (double) TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime());

		LOGGER.info("highVolumeGetRewards with {} users: Time Elapsed: {} seconds.", userCount, timeElapsed);
		LOGGER.info("Performance: {} ms per user", String.format("%.3f", avgTimePerUser));
		LOGGER.info("Throughput: {} users/second", String.format("%.1f", usersPerSecond));
		LOGGER.info("Total rewards calculated: {}", usersWithRewards);

		assertTrue(timeElapsed <= maxRewardTimeSeconds, String.format(
				"Performance dégradée: %d secondes > %d secondes max pour %d utilisateurs (%.3f ms/user)",
				timeElapsed, maxRewardTimeSeconds, userCount, avgTimePerUser));
	}

	// Test de performance avec surveillance mémoire
	@Test
	void memoryPerformanceTest() {
		LOGGER.info("======> Memory Performance Test <=======");

		Runtime runtime = Runtime.getRuntime();

		// Mesures avant
		long memBefore = runtime.totalMemory() - runtime.freeMemory();
		LOGGER.info("Memory before test: {} MB", memBefore / (1024 * 1024));

		InternalTestHelper.setInternalUserNumber(1000);
		tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		List<User> users = tourGuideService.getAllUsers();

		// Force garbage collection
		System.gc();
		long memAfterCreation = runtime.totalMemory() - runtime.freeMemory();
		LOGGER.info("Memory after user creation: {} MB", memAfterCreation / (1024 * 1024));

		// Exécuter le test
		CompletableFuture<Void> future = tourGuideService.trackAllUsersLocation(users);
		future.join();

		tourGuideService.tracker.stopTracking();

		// Mesures après
		System.gc();
		long memAfter = runtime.totalMemory() - runtime.freeMemory();
		LOGGER.info("Memory after test: {} MB", memAfter / (1024 * 1024));
		LOGGER.info("Memory increase: {} MB", (memAfter - memBefore) / (1024 * 1024));

		// Vérifier qu'on ne dépasse pas une limite raisonnable
		long memoryIncrease = memAfter - memBefore;
		assertTrue(memoryIncrease < 500 * 1024 * 1024, // 500MB max
				"Memory usage should not exceed 500MB for 1000 users");
	}


	private void logSystemInfo() {
		Runtime runtime = Runtime.getRuntime();
		int processors = runtime.availableProcessors();
		long maxMemory = runtime.maxMemory() / (1024 * 1024);
		long totalMemory = runtime.totalMemory() / (1024 * 1024);
		long freeMemory = runtime.freeMemory() / (1024 * 1024);

		LOGGER.info("System Info - Processors: {}, Max Memory: {}MB, Total: {}MB, Free: {}MB",
				processors, maxMemory, totalMemory, freeMemory);
	}
}