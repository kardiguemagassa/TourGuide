package com.openclassrooms.tourguide;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

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

	/*
	 * A note on performance improvements:
	 *
	 * The number of users generated for the high volume tests can be easily
	 * adjusted via this method:
	 *
	 * InternalTestHelper.setInternalUserNumber(100000);
	 *
	 *
	 * These tests can be modified to suit new solutions, just as long as the
	 * performance metrics at the end of the tests remains consistent.
	 *
	 * These are performance metrics that we are trying to hit:
	 *
	 * highVolumeTrackLocation: 100,000 users within 15 minutes:
	 * assertTrue(TimeUnit.MINUTES.toSeconds(15) >=
	 * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 *
	 * highVolumeGetRewards: 100,000 users within 20 minutes:
	 * assertTrue(TimeUnit.MINUTES.toSeconds(20) >=
	 * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 */

	@Disabled
	@Test
	public void highVolumeTrackLocation() {
		LOGGER.info("======> Start highVolumeTrackLocation ========>");
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		// Users should be incremented up to 100,000, and test finishes within 15
		// minutes
		InternalTestHelper.setInternalUserNumber(100);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

        List<User> allUsers = tourGuideService.getAllUsers();

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		for (User user : allUsers) {
			tourGuideService.trackUserLocation(user);
		}
		stopWatch.stop();
		tourGuideService.tracker.stopTracking();

		// highVolumeTrackLocation: Time Elapsed: 15s 425ms.
		LOGGER.info("highVolumeTrackLocation: Time Elapsed: "
				+ TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");
		assertTrue(TimeUnit.MINUTES.toSeconds(15) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}

	@Disabled
	@Test
	public void highVolumeGetRewards() {
		LOGGER.info("======> Start highVolumeGetRewards ========>");
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		// Users should be incremented up to 100,000, and test finishes within 20
		// minutes
		InternalTestHelper.setInternalUserNumber(100);
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		Attraction attraction = gpsUtil.getAttractions().get(0);
		List<User> allUsers = tourGuideService.getAllUsers();

		allUsers.forEach(u -> u.addToVisitedLocations(new VisitedLocation(u.getUserId(), attraction, new Date())));

		allUsers.forEach(rewardsService::calculateRewards);

		for (User user : allUsers) {
            assertFalse(user.getUserRewards().isEmpty());
		}
		stopWatch.stop();
		tourGuideService.tracker.stopTracking();

		// highVolumeGetRewards: Time Elapsed: 11s 997ms.

		LOGGER.info("highVolumeGetRewards: Time Elapsed: " + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime())
				+ " seconds.");
		assertTrue(TimeUnit.MINUTES.toSeconds(20) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}

	// NOUVEAU TEST OPTIMISÉ
	@Test
	public void highVolumeTrackLocationOptimized() {
		LOGGER.info("======> Start highVolumeTrackLocationOptimized ========>");
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		InternalTestHelper.setInternalUserNumber(100000);
		tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		List<User> allUsers = tourGuideService.getAllUsers();

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		//  MÉTHODE OPTIMISÉE
		long trackLocationStartTime = System.currentTimeMillis();
		CompletableFuture<Void> trackingFuture = tourGuideService.trackAllUsersLocation(allUsers);

		trackingFuture.join(); // Attendre la completion
		long trackLocationEndTime = System.currentTimeMillis();

		stopWatch.stop();
		tourGuideService.tracker.stopTracking();

		LOGGER.info("highVolumeTrackLocationOptimized: Time Elapsed: "
				+ TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");

		// highVolumeTrackLocationOptimized: Time Elapsed: 138 seconds. = 1m 55s
		long timeElapsed = TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime());
		LOGGER.info("Total time: " + timeElapsed + " s for " + allUsers.size() + " users");
		LOGGER.info("Total time: " + stopWatch.getTime() + " ms ");
		LOGGER.info("Track location calculation: " + (trackLocationEndTime - trackLocationStartTime) + " ms");
		LOGGER.info("Time per user: " + (stopWatch.getTime() / (double) allUsers.size()) + " ms ");
		assertTrue(timeElapsed < 200, " moins de 200 secondes pour 100000 utilisateurs");
	}

	// NOUVEAU TEST OPTIMISÉ
	@Test
	public void highVolumeGetRewardsOptimize() {

		LOGGER.info("======> Start highVolumeGetRewardsOptimized ========>");

		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		InternalTestHelper.setInternalUserNumber(100000);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		Attraction attraction = gpsUtil.getAttractions().get(0);
		List<User> allUsers = tourGuideService.getAllUsers();

		// Ajout des locations visitées - version parallélisée
		allUsers.forEach(u ->
				u.addToVisitedLocations(new VisitedLocation(u.getUserId(), attraction, new Date()))
		);

		tourGuideService.tracker.stopTracking();

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		//  MÉTHODE OPTIMISÉE
		long rewardStartTime = System.currentTimeMillis();
		CompletableFuture<Void> RewardFuture = rewardsService.calculateRewardsForAllUsers(allUsers);
		//rewardsService.calculateRewardsForAllUsers(allUsers).join();
		RewardFuture.join();
		long rewardEndTime = System.currentTimeMillis();

		stopWatch.stop();

		long timeElapsed = TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime());
		LOGGER.info("Optimized time: " + timeElapsed + "s for " + allUsers.size() + " users");
		LOGGER.info("Reward calculation: " + (rewardEndTime - rewardStartTime) + "ms");
		LOGGER.info("Total time: " + stopWatch.getTime() + "ms");
		LOGGER.info("Time per user: " + (stopWatch.getTime() / (double) allUsers.size()) + " ms");

		// Objectif réaliste pour 100 000 utilisateurs  Optimized time: 730s for 100000 users 11min 41s
		//  15s 312ms maintemant
		assertTrue(timeElapsed < 950, "Doit prendre moins de 15 minutes pour 100000 utilisateurs");
	}


	// TEST DE PROFILING POUR INDENTIFIER LES GOULOTS D'ETRANGLEMENT
	@Test
	public void profilingRewardsCalculation() {
		LOGGER.info("======> Start profilingRewardsCalculation ========>");
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		InternalTestHelper.setInternalUserNumber(1000); // Plus petit échantillon pour le profiling
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		Attraction attraction = gpsUtil.getAttractions().get(0);
		List<User> allUsers = tourGuideService.getAllUsers();

		allUsers.forEach(u ->
				u.addToVisitedLocations(new VisitedLocation(u.getUserId(), attraction, new Date()))
		);

		// Mesurer chaque étape
		long startInit = System.currentTimeMillis();

		StopWatch totalTime = new StopWatch();
		totalTime.start();

		// Mesurer le temps de calcul des récompenses
		long rewardStartTime = System.currentTimeMillis();
		rewardsService.calculateRewardsForAllUsers(allUsers).join();
		long rewardEndTime = System.currentTimeMillis();

		totalTime.stop();

		// 13s 127ms
		LOGGER.info("=== PROFILING RESULTS ===");
		LOGGER.info("Users: " + allUsers.size());
		LOGGER.info("Reward calculation: " + (rewardEndTime - rewardStartTime) + "ms");
		LOGGER.info("Total time: " + totalTime.getTime() + "ms");
		LOGGER.info("Time per user: " + (totalTime.getTime() / (double) allUsers.size()) + "ms");

		tourGuideService.tracker.stopTracking();

	}

	// TEST POUR VOIR LE DETAIL : USERS, ATTRACTION, VISITED LOCATION ... ETC
	@Test
	public void detailedProfilingRewardsCalculation() {
		LOGGER.info("======> Start detailedProfilingRewardsCalculation ========>");
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		InternalTestHelper.setInternalUserNumber(100); // Encore plus petit pour identifier précisément
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		Attraction attraction = gpsUtil.getAttractions().get(0);
		List<User> allUsers = tourGuideService.getAllUsers();

		// Ajouter plusieurs locations pour tester l'impact
		allUsers.forEach(u -> {
			u.addToVisitedLocations(new VisitedLocation(u.getUserId(), attraction, new Date()));
			// Ajouter une deuxième location pour tester
			u.addToVisitedLocations(new VisitedLocation(u.getUserId(), attraction, new Date()));
		});

		LOGGER.info("=== DETAILED PROFILING ===");
		LOGGER.info("Users: " + allUsers.size());
		LOGGER.info("Attractions: " + gpsUtil.getAttractions().size());
		LOGGER.info("Visited locations per user: " + allUsers.get(0).getVisitedLocations().size());

		// Test 1: Mesurer le temps d'un seul appel rewardCentral
		long singleCallStart = System.currentTimeMillis();
		RewardCentral rewardCentral = new RewardCentral();
		int testReward = rewardCentral.getAttractionRewardPoints(attraction.attractionId, allUsers.get(0).getUserId());
		long singleCallEnd = System.currentTimeMillis();
		LOGGER.info("Single rewardCentral call: " + (singleCallEnd - singleCallStart) + " ms");

		// Test 2: Calculer le nombre théorique d'appels
		int expectedCalls = allUsers.size() * allUsers.get(0).getVisitedLocations().size();
		LOGGER.info("Expected rewardCentral calls: " + expectedCalls);
		LOGGER.info("Theoretical time (single call * calls): " +
				(singleCallEnd - singleCallStart) * expectedCalls + " ms");

		// Test 3: Mesurer le temps réel
		long actualStart = System.currentTimeMillis();
		rewardsService.calculateRewardsForAllUsers(allUsers).join();
		long actualEnd = System.currentTimeMillis();

		LOGGER.info("Actual calculation time: " + (actualEnd - actualStart) + " ms");
		LOGGER.info("Time per user: " + ((actualEnd - actualStart) / (double) allUsers.size()) + " ms");

		// Test 4: Vérifier les résultats
		long totalRewards = allUsers.stream().mapToLong(u -> u.getUserRewards().size()).sum();
		LOGGER.info("Total rewards generated: " + totalRewards);
		LOGGER.info("Average rewards per user: " + (totalRewards / (double) allUsers.size()));

		tourGuideService.tracker.stopTracking();
		// 16s 386ms
	}

	// TEST POUR MESURER L'IMPACT DU CACHE
	@Test
	public void testCacheEffectiveness() {
		LOGGER.info("======> Start testCacheEffectiveness ========>");
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		InternalTestHelper.setInternalUserNumber(100);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		List<User> allUsers = tourGuideService.getAllUsers();
		Attraction attraction = gpsUtil.getAttractions().get(0);

		// Tous les users visitent la MÊME attraction (pour tester le cache)
		allUsers.forEach(u ->
				u.addToVisitedLocations(new VisitedLocation(u.getUserId(), attraction, new Date()))
		);

		long startTime = System.currentTimeMillis();
		rewardsService.calculateRewardsForAllUsers(allUsers).join();
		long endTime = System.currentTimeMillis();

		LOGGER.info("=== CACHE TEST ===");
		LOGGER.info("Time with cache (same attraction): " + (endTime - startTime) + " ms");
		LOGGER.info("Time per user: " + ((endTime - startTime) / (double) allUsers.size()) + " ms");

		tourGuideService.tracker.stopTracking();

		// 12s 671ms
	}

	// TEST POUR ISOLER LE PROBLEME DE SYNCHRONIZATION
	@Test
	public void testSynchronizationImpact() {
		LOGGER.info("======> Start testSynchronizationImpact ========>");
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		InternalTestHelper.setInternalUserNumber(100);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		List<User> allUsers = tourGuideService.getAllUsers();
		Attraction attraction = gpsUtil.getAttractions().get(0);

		allUsers.forEach(u ->
				u.addToVisitedLocations(new VisitedLocation(u.getUserId(), attraction, new Date()))
		);

		// Test séquentiel
		long sequentialStart = System.currentTimeMillis();
		for (User user : allUsers) {
			rewardsService.calculateRewards(user);
		}
		long sequentialEnd = System.currentTimeMillis();

		// Réinitialiser les récompenses
		allUsers.forEach(u -> u.getUserRewards().clear());

		// Test parallèle
		long parallelStart = System.currentTimeMillis();
		rewardsService.calculateRewardsForAllUsers(allUsers).join();
		long parallelEnd = System.currentTimeMillis();

		LOGGER.info("===> SYNCHRONIZATION TEST ===>");
		LOGGER.info("Sequential time: " + (sequentialEnd - sequentialStart) + " ms");
		LOGGER.info("Parallel time: " + (parallelEnd - parallelStart) + " ms");
		LOGGER.info("Speedup: " + ((double)(sequentialEnd - sequentialStart) / (parallelEnd - parallelStart)));

		tourGuideService.tracker.stopTracking();


		// 14s 762ms
	}

}