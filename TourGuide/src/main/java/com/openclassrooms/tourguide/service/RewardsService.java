package com.openclassrooms.tourguide.service;

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
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

@Service
public class RewardsService {

	private static final Logger LOGGER = LoggerFactory.getLogger(RewardsService.class);
	private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// Configuration des distances
	private final int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int defaultAttractionProximity = 200;

	// Services externes
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;

	// Pool de threads très optimisé pour les performances
	private final ForkJoinPool forkJoinPool;

	// Cache pour optimiser les performances
	private final ConcurrentHashMap<String, Integer> rewardPointsCache = new ConcurrentHashMap<>();
	private volatile List<Attraction> attractionsCache;

	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;

		// Utilisation d'un ForkJoinPool optimisé pour les tâches parallèles intensives
		int parallelism = Runtime.getRuntime().availableProcessors() * 4; // Plus agressif
		this.forkJoinPool = new ForkJoinPool(parallelism);

		LOGGER.info("RewardsService initialized with {} threads", parallelism);
		Locale.setDefault(Locale.FRANCE);

		// Pré-charger le cache des attractions
		getAttractionsWithCache();
	}

	/**
	 * Version hautement optimisée pour les gros volumes
	 */
	public CompletableFuture<Void> calculateRewardsForAllUsers(List<User> users) {
		if (users == null || users.isEmpty()) {
			return CompletableFuture.completedFuture(null);
		}

		return CompletableFuture.runAsync(() -> {
			List<Attraction> attractions = getAttractionsWithCache();

			// Traitement parallèle ultra-optimisé
			users.parallelStream().forEach(user -> {
				calculateRewardsOptimized(user, attractions);
			});
		}, forkJoinPool);
	}

	/**
	 * Version ultra-optimisée du calcul des récompenses
	 */
	private void calculateRewardsOptimized(User user, List<Attraction> attractions) {
		if (user == null || user.getVisitedLocations().isEmpty()) {
			return;
		}

		// Set des attractions déjà récompensées (accès O(1))
		Set<UUID> alreadyRewardedAttractions = user.getUserRewards().stream()
				.map(r -> r.attraction.attractionId)
				.collect(Collectors.toSet());

		// Liste pour collecter les nouvelles récompenses
		List<UserReward> newRewards = new ArrayList<>();

		// Optimisation : traitement en parallèle des locations visitées
		user.getVisitedLocations().parallelStream().forEach(visitedLocation -> {
			for (Attraction attraction : attractions) {
				// Skip si déjà récompensé
				if (alreadyRewardedAttractions.contains(attraction.attractionId)) {
					continue;
				}

				// Calcul de distance optimisé
				if (isNearAttractionOptimized(visitedLocation, attraction)) {
					int rewardPoints = getRewardPointsWithCache(attraction, user);

					synchronized (newRewards) {
						// Vérifier encore une fois pour éviter les doublons
						if (!alreadyRewardedAttractions.contains(attraction.attractionId)) {
							newRewards.add(new UserReward(visitedLocation, attraction, rewardPoints));
							alreadyRewardedAttractions.add(attraction.attractionId);
						}
					}
				}
			}
		});

		// Ajouter toutes les nouvelles récompenses d'un coup
		if (!newRewards.isEmpty()) {
			synchronized (user.getUserRewards()) {
				user.getUserRewards().addAll(newRewards);
			}
		}
	}

	/**
	 * Version synchrone optimisée
	 */
	public void calculateRewards(User user) {
		calculateRewardsOptimized(user, getAttractionsWithCache());
	}

	/**
	 * Version asynchrone
	 */
	public CompletableFuture<Void> calculateRewardsAsync(User user) {
		return CompletableFuture.runAsync(() -> calculateRewards(user), forkJoinPool);
	}

	/**
	 * Calcul des récompenses pour plusieurs utilisateurs
	 */
	public CompletableFuture<Void> calculateRewardsForUsers(List<User> users) {
		return calculateRewardsForAllUsers(users);
	}

	/**
	 * Traitement par batch ultra-optimisé
	 */
	public CompletableFuture<Void> calculateRewardsInBatches(List<User> users, int batchSize) {
		if (users == null || users.isEmpty()) {
			return CompletableFuture.completedFuture(null);
		}

		return CompletableFuture.runAsync(() -> {
			// Traitement parallèle des batches
			IntStream.range(0, (users.size() + batchSize - 1) / batchSize)
					.parallel()
					.forEach(i -> {
						int start = i * batchSize;
						int end = Math.min(start + batchSize, users.size());
						List<User> batch = users.subList(start, end);

						// Traitement parallèle du batch
						batch.parallelStream().forEach(user ->
								calculateRewardsOptimized(user, getAttractionsWithCache()));
					});
		}, forkJoinPool);
	}

	/**
	 * Cache thread-safe ultra-rapide pour les attractions
	 */
	private List<Attraction> getAttractionsWithCache() {
		if (attractionsCache == null) {
			synchronized (this) {
				if (attractionsCache == null) {
					attractionsCache = gpsUtil.getAttractions();
					LOGGER.info("Attractions cache initialized with {} attractions", attractionsCache.size());
				}
			}
		}
		return attractionsCache;
	}

	/**
	 * Cache optimisé pour les points de récompense
	 */
	private int getRewardPointsWithCache(Attraction attraction, User user) {
		String cacheKey = attraction.attractionId + "_" + user.getUserId();
		return rewardPointsCache.computeIfAbsent(cacheKey, k -> {
			try {
				return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
			} catch (Exception e) {
				LOGGER.warn("Error getting reward points for attraction {}, user {}",
						attraction.attractionId, user.getUserId());
				return 0;
			}
		});
	}

	/**
	 * Calcul de proximité ultra-optimisé avec pré-filtre
	 */
	private boolean isNearAttractionOptimized(VisitedLocation visitedLocation, Attraction attraction) {
		Location location = visitedLocation.location;

		// Pré-filtre rapide basé sur les coordonnées
		double latDiff = Math.abs(attraction.latitude - location.latitude);
		double lonDiff = Math.abs(attraction.longitude - location.longitude);

		// Si trop éloigné, pas besoin de calculer la distance exacte
		// Approximation : 1 degré ≈ 69 miles
		if (latDiff > proximityBuffer / 69.0 || lonDiff > proximityBuffer / 69.0) {
			return false;
		}

		// Calcul précis seulement si nécessaire
		return getDistance(attraction, location) <= proximityBuffer;
	}

	/**
	 * Vérifie si une location est dans la proximité d'une attraction
	 */
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) <= defaultAttractionProximity;
	}

	/**
	 * Calcul de distance optimisé
	 */
	public double getDistance(Location loc1, Location loc2) {
		double lat1 = Math.toRadians(loc1.latitude);
		double lon1 = Math.toRadians(loc1.longitude);
		double lat2 = Math.toRadians(loc2.latitude);
		double lon2 = Math.toRadians(loc2.longitude);

		double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
				+ Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

		double nauticalMiles = 60 * Math.toDegrees(angle);
		return STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
	}

	/**
	 * Nettoyage du cache
	 */
	public void clearCache() {
		rewardPointsCache.clear();
		attractionsCache = null;
		LOGGER.info("Rewards cache cleared");
	}

	/**
	 * Statistiques du cache
	 */
	public void logCacheStats() {
		LOGGER.info("Cache stats - Reward points: {}, Attractions cached: {}",
				rewardPointsCache.size(), attractionsCache != null ? attractionsCache.size() : 0);
	}

	// Setters pour la configuration
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}

	public void setDefaultProximityBuffer() {
		this.proximityBuffer = defaultProximityBuffer;
	}

	public void setDefaultAttractionProximity(int defaultAttractionProximity) {
		this.defaultAttractionProximity = defaultAttractionProximity;
	}

	/**
	 * Nettoyage des ressources
	 */
	@PreDestroy
	public void shutdown() {
		LOGGER.info("Shutting down RewardsService");

		forkJoinPool.shutdown();
		try {
			if (!forkJoinPool.awaitTermination(30, TimeUnit.SECONDS)) {
				forkJoinPool.shutdownNow();
			}
		} catch (InterruptedException e) {
			forkJoinPool.shutdownNow();
			Thread.currentThread().interrupt();
		}

		clearCache();
	}
}