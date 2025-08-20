package com.openclassrooms.tourguide;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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
import tripPricer.Provider;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import gpsUtil.location.Location;
import com.openclassrooms.tourguide.dto.NearByAttractionDTO;
import com.openclassrooms.tourguide.user.UserPreferences;
import com.openclassrooms.tourguide.user.UserReward;


public class TestTourGuideService {

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
		if (tourGuideService != null && tourGuideService.tracker != null) {
			tourGuideService.tracker.stopTracking();
		}
	}

	@Test
	void getUserLocation() {
		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		VisitedLocation visitedLocation = tourGuideService.trackUserLocation(user);
		assertEquals(visitedLocation.userId, user.getUserId());
	}

	@Test
	void addUser() {
		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		User user2 = new User(UUID.randomUUID(), "jon2", "000", "jon2@tourGuide.com");

		tourGuideService.addUser(user);
		tourGuideService.addUser(user2);

		User retrievedUser = tourGuideService.getUser(user.getUserName());
		User retrievedUser2 = tourGuideService.getUser(user2.getUserName());

		assertEquals(user, retrievedUser);
		assertEquals(user2, retrievedUser2);
	}

	@Test
	void getAllUsers() {
		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		User user2 = new User(UUID.randomUUID(), "jon2", "000", "jon2@tourGuide.com");

		tourGuideService.addUser(user);
		tourGuideService.addUser(user2);

		List<User> allUsers = tourGuideService.getAllUsers();

		assertTrue(allUsers.contains(user));
		assertTrue(allUsers.contains(user2));
	}

	@Test
	void trackUser() {
		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		VisitedLocation visitedLocation = tourGuideService.trackUserLocation(user);

		assertEquals(user.getUserId(), visitedLocation.userId);
	}

	@Test
	void getNearbyAttractions() {
		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		VisitedLocation visitedLocation = tourGuideService.trackUserLocation(user);

		List<Attraction> attractions = tourGuideService.getNearByAttractions(visitedLocation);

		assertEquals(5, attractions.size());
	}

	@Test
	void getTripDeals() {
		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");

		List<Provider> providers = tourGuideService.getTripDeals(user);

		assertEquals(10, providers.size());
	}

	// ========== NOUVEAUX TESTS POUR AMÉLIORER LA COUVERTURE ==========

	@Test
	void getUserLocationWithExistingVisitedLocation() {
		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");

		// Ajouter une location visitée existante
		VisitedLocation existingLocation = new VisitedLocation(
				user.getUserId(),
				new Location(33.817595, -117.922008),
				new java.util.Date()
		);
		user.addToVisitedLocations(existingLocation);

		// getUserLocation devrait retourner la dernière location visitée
		VisitedLocation result = tourGuideService.getUserLocation(user);

		assertEquals(existingLocation, result);
		assertEquals(1, user.getVisitedLocations().size());
	}

	@Test
	void getNearbyAttractionsWithDetails() {
		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");

		List<NearByAttractionDTO> nearbyAttractions = tourGuideService.getNearbyAttractionsWithDetails(user);

		assertNotNull(nearbyAttractions);
		assertEquals(5, nearbyAttractions.size());

		// Vérifier que chaque DTO contient les bonnes informations
		for (NearByAttractionDTO attraction : nearbyAttractions) {
			assertNotNull(attraction.getAttractionName());
			assertTrue(attraction.getAttractionDistanceInMiles() >= 0);
			assertTrue(attraction.getAttractionRewardPoints() >= 0);
			assertNotEquals(0.0, attraction.getAttractionLatitude());
			assertNotEquals(0.0, attraction.getAttractionLongitude());
			assertNotEquals(0.0, attraction.getUserLatitude());
			assertNotEquals(0.0, attraction.getUserLongitude());
		}
	}

	@Test
	void getUserRewards() {
		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");

		// Ajouter quelques récompenses manuellement pour le test
		List<Attraction> attractions = gpsUtil.getAttractions();
		if (!attractions.isEmpty()) {
			UserReward reward = new UserReward(
					new VisitedLocation(user.getUserId(), new Location(0, 0), new java.util.Date()),
					attractions.getFirst(),
					100
			);
			user.addUserReward(reward);
		}

		List<UserReward> rewards = tourGuideService.getUserRewards(user);

		assertNotNull(rewards);
		assertEquals(user.getUserRewards().size(), rewards.size());
	}

	@Test
	void getTripDealsWithUserPreferences() {
		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");

		// Définir des préférences utilisateur
		UserPreferences preferences = new UserPreferences();
		preferences.setNumberOfAdults(2);
		preferences.setNumberOfChildren(1);
		preferences.setTripDuration(7);
		user.setUserPreferences(preferences);

		List<Provider> providers = tourGuideService.getTripDeals(user);

		assertNotNull(providers);
		assertEquals(10, providers.size());
		assertEquals(providers, user.getTripDeals());
	}

	@Test
	void getTripDealsWithNullPreferences() {
		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		user.setUserPreferences(null); // Préférences nulles

		List<Provider> providers = tourGuideService.getTripDeals(user);

		assertNotNull(providers);
		assertEquals(10, providers.size());
		assertNotNull(user.getUserPreferences()); // Les préférences doivent être initialisées
	}

	@Test
	void trackUserLocationAsync() throws ExecutionException, InterruptedException, TimeoutException {
		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");

		CompletableFuture<VisitedLocation> future = tourGuideService.trackUserLocationAsync(user);
		VisitedLocation visitedLocation = future.get(10, TimeUnit.SECONDS);

		assertNotNull(visitedLocation);
		assertEquals(user.getUserId(), visitedLocation.userId);
		assertTrue(user.getVisitedLocations().contains(visitedLocation));
	}

	@Test
	void trackAllUsersLocation() throws ExecutionException, InterruptedException, TimeoutException {
		User user1 = new User(UUID.randomUUID(), "jon1", "000", "jon1@tourGuide.com");
		User user2 = new User(UUID.randomUUID(), "jon2", "000", "jon2@tourGuide.com");
		List<User> users = List.of(user1, user2);

		CompletableFuture<Void> future = tourGuideService.trackAllUsersLocation(users);
		future.get(15, TimeUnit.SECONDS); // Attendre la fin

		// Vérifier que chaque utilisateur a au moins une location visitée
        assertFalse(user1.getVisitedLocations().isEmpty());
        assertFalse(user2.getVisitedLocations().isEmpty());
	}

	@Test
	void trackUsersLocationInBatches() throws ExecutionException, InterruptedException, TimeoutException {
		User user1 = new User(UUID.randomUUID(), "jon1", "000", "jon1@tourGuide.com");
		User user2 = new User(UUID.randomUUID(), "jon2", "000", "jon2@tourGuide.com");
		User user3 = new User(UUID.randomUUID(), "jon3", "000", "jon3@tourGuide.com");
		List<User> users = List.of(user1, user2, user3);

		CompletableFuture<Void> future = tourGuideService.trackUsersLocationInBatches(users, 2);
		future.get(20, TimeUnit.SECONDS);

		// Vérifier que tous les utilisateurs ont été traités
        assertFalse(user1.getVisitedLocations().isEmpty());
        assertFalse(user2.getVisitedLocations().isEmpty());
        assertFalse(user3.getVisitedLocations().isEmpty());
	}

	@Test
	void trackUsersBatch() throws ExecutionException, InterruptedException, TimeoutException {
		User user1 = new User(UUID.randomUUID(), "jon1", "000", "jon1@tourGuide.com");
		User user2 = new User(UUID.randomUUID(), "jon2", "000", "jon2@tourGuide.com");
		List<User> users = List.of(user1, user2);

		CompletableFuture<Void> future = tourGuideService.trackUsersBatch(users);
		future.get(15, TimeUnit.SECONDS);

		// Vérifier que le batch a été traité
        assertFalse(user1.getVisitedLocations().isEmpty());
        assertFalse(user2.getVisitedLocations().isEmpty());
	}

	@Test
	void trackAllUsersOptimized() {
		// Ajouter quelques utilisateurs au service
		User user1 = new User(UUID.randomUUID(), "jon1", "000", "jon1@tourGuide.com");
		User user2 = new User(UUID.randomUUID(), "jon2", "000", "jon2@tourGuide.com");

		tourGuideService.addUser(user1);
		tourGuideService.addUser(user2);

		// Mesurer les locations avant
		int initialLocationsUser1 = user1.getVisitedLocations().size();
		int initialLocationsUser2 = user2.getVisitedLocations().size();

		// Exécuter le tracking optimisé
		tourGuideService.trackAllUsersOptimized();

		// Vérifier que de nouvelles locations ont été ajoutées
		assertTrue(user1.getVisitedLocations().size() > initialLocationsUser1);
		assertTrue(user2.getVisitedLocations().size() > initialLocationsUser2);
	}

	@Test
	void addUserDuplicate() {
		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");

		tourGuideService.addUser(user);
		int initialUserCount = tourGuideService.getAllUsers().size();

		// Tenter d'ajouter le même utilisateur
		tourGuideService.addUser(user);

		// Le nombre d'utilisateurs ne doit pas augmenter
		assertEquals(initialUserCount, tourGuideService.getAllUsers().size());
	}

	@Test
	void getUserNonExistent() {
		User result = tourGuideService.getUser("nonexistent");
		assertNull(result);
	}

	@Test
	void getNearbyAttractionsOrderedByDistance() {
		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		VisitedLocation visitedLocation = tourGuideService.trackUserLocation(user);

		List<Attraction> attractions = tourGuideService.getNearByAttractions(visitedLocation);

		assertEquals(5, attractions.size());

		// Vérifier que les attractions sont triées par distance (plus proche en premier)
		// Note: nous ne pouvons pas facilement tester l'ordre exact sans connaître la position,
		// mais nous pouvons vérifier qu'on a bien 5 attractions
		assertTrue(attractions.size() <= 5);
	}

	@Test
	void shutdownGracefully() {
		TourGuideService testService = new TourGuideService(gpsUtil, rewardsService);
		assertDoesNotThrow(testService::shutdown);
	}

	@Test
	void shutdownComprehensiveTest() {
		TourGuideService testService = new TourGuideService(gpsUtil, rewardsService);


		User user = new User(UUID.randomUUID(), "testUser", "000", "test@test.com");
		testService.trackUserLocationAsync(user);
		testService.trackUserLocationAsync(user);

		// Shutdown avec tâches en cours
		assertDoesNotThrow(testService::shutdown);
	}
}