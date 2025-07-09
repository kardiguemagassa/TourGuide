package com.openclassrooms.tourguide;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.AfterEach;
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

	@AfterEach
	public void tearDown() {
		if (tourGuideService != null) {
			tourGuideService.shutdown();
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
	public void highVolumeTrackLocation(int userCount, int maxTrackTimeSeconds, int maxRewardTimeSeconds) {
		LOGGER.info("======> Start highVolumeTrackLocation with {} users <=======", userCount);
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		InternalTestHelper.setInternalUserNumber(userCount);
		tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		List<User> allUsers = tourGuideService.getAllUsers();

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		// Version optimisée avec CompletableFuture
		CompletableFuture<Void> trackingFuture = tourGuideService.trackAllUsersLocation(allUsers);
		trackingFuture.join(); // Attendre la complétion

		stopWatch.stop();
		tourGuideService.tracker.stopTracking();

		long timeElapsed = TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime());
		LOGGER.info("highVolumeTrackLocation with {} users: Time Elapsed: {} seconds.", userCount, timeElapsed);
		LOGGER.info("Performance: {} ms per user", stopWatch.getTime() / (double) userCount);

		assertTrue(timeElapsed <= maxTrackTimeSeconds, String.format(
				"Doit prendre moins de %d secondes pour %d utilisateurs", maxTrackTimeSeconds, userCount));
	}

	@ParameterizedTest
	@MethodSource("userCountProvider")
	public void highVolumeGetRewards(int userCount, int maxTrackTimeSeconds, int maxRewardTimeSeconds) {
		LOGGER.info("======> Start highVolumeGetRewards with {} users <=======", userCount);
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		InternalTestHelper.setInternalUserNumber(userCount);
		tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		Attraction attraction = gpsUtil.getAttractions().get(0);
		List<User> allUsers = tourGuideService.getAllUsers();

		// Ajouter des locations visitées
		allUsers.forEach(u -> u.addToVisitedLocations(
				new VisitedLocation(u.getUserId(), attraction, new Date())));

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		// Version optimisée avec CompletableFuture
		CompletableFuture<Void> rewardsFuture = rewardsService.calculateRewardsForAllUsers(allUsers);
		rewardsFuture.join();

		stopWatch.stop();
		tourGuideService.tracker.stopTracking();

		// Vérifier que tous les utilisateurs ont des récompenses
		allUsers.forEach(u -> assertFalse(u.getUserRewards().isEmpty()));

		long timeElapsed = TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime());
		LOGGER.info("highVolumeGetRewards with {} users: Time Elapsed: {} seconds.", userCount, timeElapsed);
		LOGGER.info("Performance: {} ms per user", stopWatch.getTime() / (double) userCount);

		assertTrue(timeElapsed <= maxRewardTimeSeconds, String.format(
				"Doit prendre moins de %d secondes pour %d utilisateurs", maxRewardTimeSeconds, userCount));
	}
}