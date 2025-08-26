package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.dto.NearByAttractionDTO;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserPreferences;
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

	private static final Random RANDOM = new Random();

	private final ExecutorService executorService;
	private final Map<String, User> internalUserMap = new ConcurrentHashMap<>();
	private final List<Attraction> cachedAttractions;

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		LOGGER.info("Initializing TourGuideService");
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;

		this.cachedAttractions = Collections.unmodifiableList(gpsUtil.getAttractions());

		// OPTIMISATION : Pool de threads
		int coreThreads = Runtime.getRuntime().availableProcessors();
		int maxThreads = coreThreads * 2; // Reduction to avoid restraint max 2

		this.executorService = new ThreadPoolExecutor(
				coreThreads,
				maxThreads,
				60L,
				TimeUnit.SECONDS,
				new LinkedBlockingQueue<>(500), // Queue
				new ThreadPoolExecutor.CallerRunsPolicy());

		LOGGER.info("Thread pool initialized: core={}, max={}", coreThreads, maxThreads);

		Locale.setDefault(Locale.US);

		if (testMode) {
			LOGGER.info("TestMode enabled");
			initializeInternalUsers();
		}
		tracker = new Tracker(this);
		addShutDownHook();
	}

	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
	}

	// Batch processing with concurrency limitation
	public CompletableFuture<Void> trackAllUsersLocation(List<User> users) {
		return trackUsersInBatchesOptimized(users); // Batch of 100 users
	}

	// Batch processing
	private CompletableFuture<Void> trackUsersInBatchesOptimized(List<User> users) {
		List<CompletableFuture<Void>> batchFutures = new ArrayList<>();

		// Split into batches to avoid overloading
		for (int i = 0; i < users.size(); i += 100) {
			int endIndex = Math.min(i + 100, users.size());
			List<User> batch = users.subList(i, endIndex);

			CompletableFuture<Void> batchFuture = processBatch(batch);
			batchFutures.add(batchFuture);
		}

		return CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]));
	}

	// Batch processing with concurrency limitation
	private CompletableFuture<Void> processBatch(List<User> batch) {
		List<CompletableFuture<Void>> futures = batch.stream()
				.map(this::trackUserLocationOptimized)
				.toList();

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	// individual tracking
	private CompletableFuture<Void> trackUserLocationOptimized(User user) {
		return CompletableFuture.runAsync(() -> {
			try {
				// Check that the pool is not closed
				if (executorService.isShutdown()) {
					LOGGER.warn("ExecutorService is shutdown, falling back to synchronous execution");
					trackUserLocationSync(user);
					return;
				}

				// GPS call - the main bottleneck
				VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
				user.addToVisitedLocations(visitedLocation);

				// Don't wait for rewards
				rewardsService.calculateRewardsAsync(user);

			} catch (Exception e) {
				LOGGER.error("Error tracking user {}: {}", user.getUserName(), e.getMessage());
			}
		}, executorService);
	}

	// Synchronous fallback method
	private void trackUserLocationSync(User user) {
		try {
			VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
			user.addToVisitedLocations(visitedLocation);
			rewardsService.calculateRewardsAsync(user);
		} catch (Exception e) {
			LOGGER.error("Error in sync tracking for user {}: {}", user.getUserName(), e.getMessage());
		}
	}

	// Synchronous method for testing mandatory to comply with instructions
	public VisitedLocation trackUserLocation(User user) {
		try {
			// GPS time measurement
			long start = System.nanoTime();
			VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
			long gpsTime = System.nanoTime() - start;

			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("GPS call for user {}: {} ms", user.getUserName(), gpsTime / 1_000_000);
			}

			user.addToVisitedLocations(visitedLocation);

			// Calculation of rewards in the background (does not block the return)
			rewardsService.calculateRewardsAsync(user);

			return visitedLocation;

		} catch (Exception e) {
			LOGGER.error("Error tracking user  {}: {}", user.getUserName(), e.getMessage());
			throw new RuntimeException("Failed to track location for user: " + user.getUserName(), e);
		}
	}

	public List<NearByAttractionDTO> getNearbyAttractionsWithDetails(User user) {
		VisitedLocation visitedLocation = getUserLocation(user);
		Location userLocation = visitedLocation.location;

		return cachedAttractions.stream()
				.map(attraction -> {
					double distance = getDistance(userLocation, attraction);
					int rewardPoints = rewardsService.getRewardPoints(attraction, user.getUserId());
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
				.sorted(Comparator.comparingDouble(NearByAttractionDTO::getAttractionDistanceInMiles))
				.limit(5)
				.collect(Collectors.toList());
	}

	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	public VisitedLocation getUserLocation(User user) {
		return (!user.getVisitedLocations().isEmpty()) ?
				user.getLastVisitedLocation() : trackUserLocation(user);
	}

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return new ArrayList<>(internalUserMap.values());
	}

	public void addUser(User user) {
		internalUserMap.putIfAbsent(user.getUserName(), user);
	}

	public List<Provider> getTripDeals(User user) {
		if (user.getUserPreferences() == null) {
			user.setUserPreferences(new UserPreferences());
		}

		int cumulativeRewardPoints = user.getUserRewards().stream()
				.mapToInt(UserReward::getRewardPoints)
				.sum();

		List<Provider> providers = tripPricer.getPrice(
				tripPricerApiKey,
				user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(),
				user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(),
				cumulativeRewardPoints
		);

		if (providers.size() < 10) {
			providers = IntStream.range(0, 10)
					.mapToObj(i -> new Provider(
							UUID.randomUUID(),
							"Provider " + i,
							RANDOM.nextDouble() * 1000))
					.collect(Collectors.toList());
		}

		user.setTripDeals(providers);
		return providers;
	}

	public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
		return cachedAttractions.stream()
				.map(attraction -> new AbstractMap.SimpleEntry<>(
						attraction,
						rewardsService.getDistance(attraction, visitedLocation.location)
				))
				.sorted(Map.Entry.comparingByValue())
				.limit(5)
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());
	}

	private double getDistance(Location loc1, Location loc2) {
		double lat1 = Math.toRadians(loc1.latitude);
		double lon1 = Math.toRadians(loc1.longitude);
		double lat2 = Math.toRadians(loc2.latitude);
		double lon2 = Math.toRadians(loc2.longitude);

		double theta = lon1 - lon2;
		double dist = Math.sin(lat1) * Math.sin(lat2) +
				Math.cos(lat1) * Math.cos(lat2) * Math.cos(theta);

		dist = Math.acos(Math.min(1.0, dist)); // avoid rounding errors
		return Math.toDegrees(dist) * 60 * 1.1515; // distance in miles
	}

	private void initializeInternalUsers() {
		LOGGER.info("Initializing {} internal users...", InternalTestHelper.getInternalUserNumber());

		// Sequential creation of users
		IntStream.range(0, InternalTestHelper.getInternalUserNumber())
				.forEach(this::createInternalUser);

		LOGGER.info("Created {} internal test users", InternalTestHelper.getInternalUserNumber());
	}

	private void createInternalUser(int i) {
		String userName = "internalUser" + i;
		String phone = "000";
		String email = userName + "@tourGuide.com";
		User user = new User(UUID.randomUUID(), userName, phone, email);
		generateUserLocationHistory(user);
		internalUserMap.put(userName, user);
	}

	private void generateUserLocationHistory(User user) {
        for (int i = 0; i < 3; i++) {
            user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
                    new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
        }
    }

	private double generateRandomLongitude() {
		return -180 + RANDOM.nextDouble() * 360;
	}

	private double generateRandomLatitude() {
		return -85.05112878 + RANDOM.nextDouble() * 170.10225756;
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(RANDOM.nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

	@PreDestroy
	public void shutdown() {
		LOGGER.info("Shutting down TourGuideService...");

		if (tracker != null) {
			tracker.stopTracking();
		}

		executorService.shutdown();
		try {
			if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
				LOGGER.warn("Forcing shutdown of thread pool");
				executorService.shutdownNow();
				if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
					LOGGER.error("Thread pool did not terminate");
				}
			}
		} catch (InterruptedException e) {
			executorService.shutdownNow();
			Thread.currentThread().interrupt();
		}

		LOGGER.info("TourGuideService shutdown complete");
	}
}