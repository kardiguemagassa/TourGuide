package com.openclassrooms.tourguide.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import jakarta.annotation.PreDestroy;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import rewardCentral.RewardCentral;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class RewardsService {

	private static final Logger LOGGER = LoggerFactory.getLogger(RewardsService.class);
	private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;
	private static final int DEFAULT_PROXIMITY_BUFFER = 10;
	public int proximityBuffer = DEFAULT_PROXIMITY_BUFFER;

	private final RewardCentral rewardsCentral;
	private final ExecutorService executorService;

	// Cache des attractions
	private final List<Attraction> attractions;
	// Cache des points de récompense par attraction
	private final Cache<UUID, Integer> attractionRewardCache;

	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.rewardsCentral = rewardCentral;

		StopWatch watch = new StopWatch();
		watch.start();
		LOGGER.info("Initializing RewardsService");

		// Charge toutes les attractions une seule fois au démarrage et les rend immuables
		this.attractions = Collections.unmodifiableList(gpsUtil.getAttractions());

		int availableProcessors = Runtime.getRuntime().availableProcessors();
		this.executorService = Executors.newFixedThreadPool(availableProcessors * 4);

		// Le cache attractionRewardCache stocke les points de récompense associés à chaque attraction
		this.attractionRewardCache = Caffeine.newBuilder()
				.maximumSize(100_000)
				.expireAfterWrite(1, TimeUnit.HOURS)
				.build();

		// Pré-charge les points de récompense pour toutes les attractions dans le cache
		preloadAttractionRewards();

		watch.stop();
		LOGGER.info("RewardsService initialized in {} ms", watch.getTime());
	}

	private void preloadAttractionRewards() {
		LOGGER.info("Preloading attraction rewards for {} attractions", attractions.size());
		attractions.forEach(attraction -> {
			UUID syntheticUserId = UUID.nameUUIDFromBytes(attraction.attractionId.toString().getBytes());
			int points = rewardsCentral.getAttractionRewardPoints(attraction.attractionId, syntheticUserId);
			attractionRewardCache.put(attraction.attractionId, points);
		});
	}

	// Méthode synchrone pour calculer les récompenses
	public void calculateRewards(User user) {
		List<VisitedLocation> userLocations = new ArrayList<>(user.getVisitedLocations());
		Set<UUID> alreadyRewarded = getAlreadyRewardedAttractions(user);

		// Pour chaque attraction, vérifier si elle n'a pas déjà été récompensée
		for (Attraction attraction : attractions) {
			if (!alreadyRewarded.contains(attraction.attractionId)) {
				// Vérifier si l'attraction est proche d'au moins une des locations visitées
				VisitedLocation nearestLocation = findNearestVisitedLocation(attraction, userLocations);
				if (nearestLocation != null) {
					addUserReward(user, nearestLocation, attraction);
					alreadyRewarded.add(attraction.attractionId); // Ajouter à la liste locale
				}
			}
		}
	}

	// Trouve la location visitée la plus proche d'une attraction dans le rayon de proximité
	private VisitedLocation findNearestVisitedLocation(Attraction attraction, List<VisitedLocation> userLocations) {
		VisitedLocation nearestLocation = null;
		double minDistance = Double.MAX_VALUE;

		for (VisitedLocation visitedLocation : userLocations) {
			double distance = getDistance(attraction, visitedLocation.location);
			if (distance <= proximityBuffer && distance < minDistance) {
				minDistance = distance;
				nearestLocation = visitedLocation;
			}
		}

		return nearestLocation;
	}

	// Méthode asynchrone séparée
	public CompletableFuture<Void> calculateRewardsAsync(User user) {
		return CompletableFuture.runAsync(() -> calculateRewards(user), executorService);
	}

	// Vérifier si une attraction est dans le rayon de proximité (utilise le proximityBuffer)
	private boolean isWithinProximity(Attraction attraction, Location location) {
		double distance = getDistance(attraction, location);
		boolean within = distance <= proximityBuffer;
		LOGGER.debug("Checking {}: distance={} miles, buffer={} miles, within={}",
				attraction.attractionName, distance, proximityBuffer, within);
		return within;
	}

	// Calculer la distance en miles entre deux localisations
	public double getDistance(Location loc1, Location loc2) {
		double lat1 = Math.toRadians(loc1.latitude);
		double lon1 = Math.toRadians(loc1.longitude);
		double lat2 = Math.toRadians(loc2.latitude);
		double lon2 = Math.toRadians(loc2.longitude);

		double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
				+ Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

		return STATUTE_MILES_PER_NAUTICAL_MILE * Math.toDegrees(angle) * 60;
	}

	// Retourner l'ensemble des attractionId déjà récompensées
	private Set<UUID> getAlreadyRewardedAttractions(User user) {
		return user.getUserRewards().stream()
				.map(reward -> reward.attraction.attractionId)
				.collect(Collectors.toSet());
	}

	// Ajouter une récompense à l'utilisateur
	private void addUserReward(User user, VisitedLocation visitedLocation, Attraction attraction) {
		int rewardPoints = getRewardPoints(attraction, user.getUserId());
		LOGGER.debug("Adding reward for attraction: {} with {} points", attraction.attractionName, rewardPoints);
		user.addUserReward(new UserReward(visitedLocation, attraction, rewardPoints));
	}

	// Récupérer les points depuis le cache ou les calcule si manquant
	public int getRewardPoints(Attraction attraction, UUID userId) {
		return attractionRewardCache.get(
				attraction.attractionId,
				id -> rewardsCentral.getAttractionRewardPoints(attraction.attractionId, userId)
		);
	}

	// Calculer les récompenses pour tous les utilisateurs en parallèle
	public CompletableFuture<Void> calculateRewardsForAllUsers(List<User> users) {
		List<CompletableFuture<Void>> futures = users.stream()
				.map(this::calculateRewardsAsync)
				.toList();

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	// Gestion de la proximité
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
		LOGGER.debug("Proximity buffer set to: {} miles", proximityBuffer);
	}

	// Méthode spécifique pour les tests (utilise un buffer de 200 par défaut)
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		int defaultAttractionProximity = 200;
		return getDistance(location, attraction) <= defaultAttractionProximity;
	}

	public void setDefaultProximityBuffer() {
		this.proximityBuffer = DEFAULT_PROXIMITY_BUFFER;
	}

	@PreDestroy
	public void shutdown() {
		LOGGER.info("Shutting down RewardsService");
		executorService.shutdown();
		try {
			if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
				executorService.shutdownNow();
			}
		} catch (InterruptedException e) {
			executorService.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
}