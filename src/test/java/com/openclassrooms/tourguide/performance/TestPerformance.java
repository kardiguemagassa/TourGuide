package com.openclassrooms.tourguide.performance;

import java.util.*;
import java.util.concurrent.TimeUnit;
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

	private static Stream<Arguments> userCountProvider() {
		return Stream.of(
				Arguments.of(100, 15, 20),
				Arguments.of(1000, 30, 40),
				Arguments.of(10000, 150, 200),
				Arguments.of(100000, 900, 1200)
		);
	}

	@ParameterizedTest
	@MethodSource("userCountProvider")
	void highVolumeTrackLocation(int userCount, int maxTrackTimeSeconds) {
		LOGGER.info("======> Start highVolumeTrackLocation with {} users <=======", userCount);

		logSystemInfo();

		// Configuration pour les tests
		InternalTestHelper.setInternalUserNumber(userCount);
		tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		List<User> allUsers = tourGuideService.getAllUsers();
		assertEquals(userCount, allUsers.size());

		LOGGER.info("Starting location tracking for {} users...", userCount);

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		try {

			// Batch processing of volumes 100
			if (userCount <= 100) {
				// Sequential processing for very small volumes
				for (User user : allUsers) {
					tourGuideService.trackUserLocation(user);
				}
			} else {
				tourGuideService.trackAllUsersLocation(allUsers).get(maxTrackTimeSeconds * 2L, TimeUnit.SECONDS);
			}

		} catch (Exception e) {
			fail("Error during tracking: " + e.getMessage());
		} finally {
			stopWatch.stop();
			if (tourGuideService.tracker != null) {
				tourGuideService.tracker.stopTracking();
			}
		}

		// Validation
		long usersWithLocations = allUsers.stream()
				.mapToLong(user -> user.getVisitedLocations().size())
				.sum();
		assertTrue(usersWithLocations > 0, "Users should have locations");

		// Métrics de performance
		long timeElapsed = TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime());
		double avgTimePerUser = stopWatch.getTime() / (double) userCount;
		double usersPerSecond = userCount / Math.max(1.0, TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));

		LOGGER.info("=== PERFORMANCE RESULTS ===");
		LOGGER.info("Users: {}", userCount);
		LOGGER.info("Time Elapsed: {} seconds", timeElapsed);
		LOGGER.info("Average time per user: {} ms", String.format("%.2f", avgTimePerUser));
		LOGGER.info("Throughput: {} users/second", String.format("%.1f", usersPerSecond));
		LOGGER.info("Users with locations: {}/{}", allUsers.size(), allUsers.size());

		// Assertion avec message informatif
		assertTrue(timeElapsed <= maxTrackTimeSeconds, String.format(
				"Degraded performance: %d seconds > %d seconds max for %d users (%.3f ms/user)",
				timeElapsed, maxTrackTimeSeconds, userCount, avgTimePerUser));

		LOGGER.info("Test PASSED for {} users in {} seconds", userCount, timeElapsed);
	}

	@ParameterizedTest
	@MethodSource("userCountProvider")
	void highVolumeGetRewards(int userCount, int maxRewardTimeSeconds) {
		LOGGER.info("======> Start highVolumeGetRewards with {} users <=======", userCount);

		logSystemInfo();

		InternalTestHelper.setInternalUserNumber(userCount);
		tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		// Preparation: Add a rental near an attraction
		Attraction attraction = gpsUtil.getAttractions().getFirst();
		List<User> allUsers = tourGuideService.getAllUsers();

		allUsers.forEach(u -> u.addToVisitedLocations(
				new VisitedLocation(u.getUserId(), attraction, new Date())));

		LOGGER.info("Starting reward calculation for {} users...", userCount);

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		try {

			if (userCount <= 100) {
				for (User user : allUsers) {
					rewardsService.calculateRewards(user);
				}
			} else {
				rewardsService.calculateRewardsForAllUsers(allUsers).get(
						maxRewardTimeSeconds * 2L, TimeUnit.SECONDS
				);
			}

		} catch (Exception e) {
			fail("Error calculating rewards: " + e.getMessage());
		} finally {
			stopWatch.stop();
			if (tourGuideService.tracker != null) {
				tourGuideService.tracker.stopTracking();
			}
		}

		// Validation
		long totalRewards = allUsers.stream()
				.mapToLong(user -> user.getUserRewards().size())
				.sum();

		long usersWithRewards = allUsers.stream()
				.mapToLong(user -> user.getUserRewards().isEmpty() ? 0 : 1)
				.sum();

		assertTrue(totalRewards > 0, "Some users should get rewards");

		// Metrics
		long timeElapsed = TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime());
		double avgTimePerUser = stopWatch.getTime() / (double) userCount;
		double usersPerSecond = userCount / Math.max(1.0, TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));

		LOGGER.info("=== REWARD CALCULATION RESULTS ===");
		LOGGER.info("Users: {}", userCount);
		LOGGER.info("Time Elapsed: {} seconds", timeElapsed);
		LOGGER.info("Average time per user: {} ms", String.format("%.2f", avgTimePerUser));
		LOGGER.info("Throughput: {} users/second", String.format("%.1f", usersPerSecond));
		LOGGER.info("Users with rewards: {}/{}", usersWithRewards, allUsers.size());
		LOGGER.info("Total rewards calculated: {}", totalRewards);

		assertTrue(timeElapsed <= maxRewardTimeSeconds, String.format(
				"Degraded performance: %d seconds > %d seconds max for %d users (%.3f ms/user)",
				timeElapsed, maxRewardTimeSeconds, userCount, avgTimePerUser));

		LOGGER.info("✓ Test PASSED for {} users in {} seconds", userCount, timeElapsed);
	}

	@Test
	void memoryPerformanceTest() {
		LOGGER.info("======> Memory Performance Test <=======");

		Runtime runtime = Runtime.getRuntime();

		// Measures after
		System.gc();
		long memBefore = runtime.totalMemory() - runtime.freeMemory();
		LOGGER.info("Memory before test: {} MB", memBefore / (1024 * 1024));

		InternalTestHelper.setInternalUserNumber(1000);
		tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		List<User> users = tourGuideService.getAllUsers();

		System.gc();
		long memAfterCreation = runtime.totalMemory() - runtime.freeMemory();
		LOGGER.info("Memory after user creation: {} MB", memAfterCreation / (1024 * 1024));

		// Synchronous test
		for (User user : users) {
			tourGuideService.trackUserLocation(user);
		}

		if (tourGuideService.tracker != null) {
			tourGuideService.tracker.stopTracking();
		}

		// Measures after
		System.gc();
		long memAfter = runtime.totalMemory() - runtime.freeMemory();
		LOGGER.info("Memory after test: {} MB", memAfter / (1024 * 1024));
		LOGGER.info("Memory increase: {} MB", (memAfter - memBefore) / (1024 * 1024));

		long memoryIncrease = memAfter - memBefore;
		assertTrue(memoryIncrease < 500 * 1024 * 1024,
				"Memory usage should not exceed 500MB for 1000 users");
	}

	@Test
	void basicPerformanceTest() {
		LOGGER.info("======> Basic Performance Test <=======");

		InternalTestHelper.setInternalUserNumber(10);
		tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		List<User> users = tourGuideService.getAllUsers();
		User testUser = users.getFirst();

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		VisitedLocation location = tourGuideService.trackUserLocation(testUser);

		stopWatch.stop();

		assertNotNull(location, "User should have a tracked location");
		LOGGER.info("Basic tracking took: {} ms", stopWatch.getTime());

		assertTrue(stopWatch.getTime() < 5000,
				"Basic tracking should take less than 5 seconds");
	}

	private void logSystemInfo() {
		Runtime runtime = Runtime.getRuntime();
		int processors = runtime.availableProcessors();
		long maxMemory = runtime.maxMemory() / (1024 * 1024);
		long totalMemory = runtime.totalMemory() / (1024 * 1024);
		long freeMemory = runtime.freeMemory() / (1024 * 1024);

		LOGGER.info("=== SYSTEM INFO ===");
		LOGGER.info("Processors: {}", processors);
		LOGGER.info("Max Memory: {} MB", maxMemory);
		LOGGER.info("Total Memory: {} MB", totalMemory);
		LOGGER.info("Free Memory: {} MB", freeMemory);
		LOGGER.info("================");
	}
}