package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.dto.NearByAttractionDTO;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import rewardCentral.RewardCentral;
import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {

	private final Logger LOGGER = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;
	private static final String tripPricerApiKey = "test-server-api-key";
	// calcule les points de récompense que les utilisateurs gagnent lorsqu’ils visitent certains lieux.
	private final RewardCentral rewardCentral = new RewardCentral();

	private final ExecutorService executorService;
    // Collections thread-safe pour éviter les problèmes de concurrence
	private final Map<String, User> internalUserMap = new ConcurrentHashMap<>(); // gère automatiquement la synchronisation

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;

		// Configuration du pool de threads
        int numberOfThreads = Runtime.getRuntime().availableProcessors() * 2;

		this.executorService = new ThreadPoolExecutor(
                numberOfThreads,
				numberOfThreads * 2,
				60L, // temps d’attente avant de tuer un thread inactif
				TimeUnit.SECONDS,
				new LinkedBlockingQueue<>(1000),
				new ThreadPoolExecutor.CallerRunsPolicy()); // Politique de fallback

		LOGGER.info("Thread pool size initialized to {}", numberOfThreads);

		Locale.setDefault(Locale.FRANCE);

		if (testMode) {
			LOGGER.info("TestMode enabled");
			LOGGER.debug("Initializing users");
			initializeInternalUsers();
			LOGGER.debug("Finished initializing users");
		}
		tracker = new Tracker(this); // surveiller la localisation des utilisateurs en conti
		addShutDownHook(); // sauvegarder des données, libérer des ressources
	}

	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
	}

	/**
	 * Méthode optimisée pour le tracking en masse des utilisateurs
	 * Utilise le parallélisme pour traiter plusieurs utilisateurs simultanément
	 */
	public void trackAllUsersOptimized() {
		LOGGER.debug("Begin tracking all users optimized");

		List<User> users = getAllUsers();
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		// Traitement parallèle des utilisateurs
		for (User user : users) {
			CompletableFuture<Void> future = CompletableFuture
					.supplyAsync(() -> {
						// Obtenir la nouvelle location
						VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
						user.addToVisitedLocations(visitedLocation);
						return visitedLocation;
					}, executorService)
					.thenCompose(visitedLocation -> {
						// Calculer les récompenses de manière asynchrone
						return rewardsService.calculateRewardsAsync(user);
					});
			futures.add(future);
		}

		// Attendre que tous les traitements soient terminés
		CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

		try {
			// Timeout pour éviter les blocages infinis
			allFutures.get(30, TimeUnit.SECONDS);
			LOGGER.debug("Finished tracking all users optimized");
		} catch (TimeoutException e) {
			LOGGER.error("Error while tracking all users", e);
			allFutures.cancel(true);
		} catch (Exception e) {
			LOGGER.error("Error while tracking all users", e);

		}
	}

	/**
	 * Méthode pour traiter un lot d'utilisateurs (batch processing)
	 */
	public CompletableFuture<Void> trackUsersBatch(List<User> users) {
		List<CompletableFuture<VisitedLocation>> futures = users.stream()
				.map(this::trackUserLocationAsync)
				.toList();
		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	/**
	 * Version optimisée du tracking d'un utilisateur
	 */
	public CompletableFuture<VisitedLocation> trackUserLocationAsync(User user) {
		return CompletableFuture
				.supplyAsync(() -> {
					VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
					user.addToVisitedLocations(visitedLocation);
					return visitedLocation;
				}, executorService)
				.thenCompose(visitedLocation -> {
					return rewardsService.calculateRewardsAsync(user)
							.thenApply(v -> visitedLocation);
				});
	}



	public List<NearByAttractionDTO> getNearbyAttractionsWithDetails(User user) {
		VisitedLocation visitedLocation = getUserLocation(user);
		Location userLocation = visitedLocation.location;

		return gpsUtil.getAttractions().stream()
				.sorted(Comparator.comparingDouble(attraction ->
						getDistance(userLocation, attraction)))
				.limit(5)
				.map(attraction -> {
					double distance = getDistance(userLocation, attraction);
					int rewardPoints = rewardCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
					return new NearByAttractionDTO(
							attraction.attractionName,
							attraction.latitude,
							attraction.longitude,
							userLocation.latitude,
							userLocation.longitude,
							distance,
							rewardPoints
					);
				})
				.collect(Collectors.toList());
	}

	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	public VisitedLocation getUserLocation(User user) {
		VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation()
				: trackUserLocation(user);
		return visitedLocation;
	}

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return new ArrayList<>(internalUserMap.values());
	}

	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}

	public VisitedLocation trackUserLocation(User user) {
		VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
		user.addToVisitedLocations(visitedLocation);
		rewardsService.calculateRewards(user);
		return visitedLocation;
	}

	/**
	 * Traitement par batch pour éviter la surcharge système
	 */
	public CompletableFuture<Void> trackUsersLocationInBatches(List<User> users, int batchSize) {
		List<CompletableFuture<Void>> batchFutures = new ArrayList<>();

		for (int i = 0; i < users.size(); i += batchSize) {
			int endIndex = Math.min(i + batchSize, users.size());
			List<User> batch = users.subList(i, endIndex);

			CompletableFuture<Void> batchFuture = trackAllUsersLocation(batch);
			batchFutures.add(batchFuture);
		}

		return CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]));
	}

	/**
	 * Version optimisée pour traquer les locations de multiples utilisateurs
	 */
	public CompletableFuture<Void> trackAllUsersLocation(List<User> users) {
		List<CompletableFuture<Void>> futures = users.stream()
				.map(user -> CompletableFuture.runAsync(() ->
						trackUserLocation(user), executorService))
				.toList();

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}


	/**
	 * Méthode optimisée pour obtenir les 5 attractions les plus proches
	 * Utilise le parallélisme pour calculer les distances
	 */
	public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
		List<Attraction> allAttractions = gpsUtil.getAttractions();

		// Calcul parallèle des distances
		return allAttractions.stream()
				.map(attraction -> new AbstractMap.SimpleEntry<>(
						attraction,
						rewardsService.getDistance(attraction, visitedLocation.location)
				))
				.sorted(Map.Entry.comparingByValue())
				.limit(5)
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());
	}

	@PreDestroy
	public void shutdown() {
		LOGGER.info("Shutting down TourGuideService");

		if (tracker != null) {
			tracker.stopTracking();
		}

		// Arrêt propre du pool de threads
		executorService.shutdown();
		try {
			if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
				executorService.shutdownNow();
				if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
					LOGGER.error("Thread pool did not terminate");
				}
			}
		} catch (InterruptedException e) {
			executorService.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}



	// la distance entre deux points
	private double getDistance(Location loc1, Location loc2) {
		double lat1 = loc1.latitude;
		double lon1 = loc1.longitude;
		double lat2 = loc2.latitude;
		double lon2 = loc2.longitude;

		double theta = lon1 - lon2;
		double dist = Math.sin(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) +
				Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(theta));
		dist = Math.acos(dist);
		dist = Math.toDegrees(dist);
		dist = dist * 60 * 1.1515; // miles
		return dist;
	}

	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);

			internalUserMap.put(userName, user);
		});
		LOGGER.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}

	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i -> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
					new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

}
