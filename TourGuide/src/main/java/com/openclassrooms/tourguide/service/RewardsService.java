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
	private int proximityBuffer = DEFAULT_PROXIMITY_BUFFER;

	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	private final ExecutorService executorService;

	// Cache des attractions
	private final List<Attraction> attractions;
	// Cache des points de récompense par attraction
	private final Cache<UUID, Integer> attractionRewardCache;


	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;

		StopWatch watch = new StopWatch();
		watch.start();
		LOGGER.info("Initializing RewardsService");

		// Charge toutes les attractions une seule fois au démarrage et les rend immuables.
		this.attractions = Collections.unmodifiableList(gpsUtil.getAttractions());

		int availableProcessors = Runtime.getRuntime().availableProcessors();
		this.executorService = Executors.newFixedThreadPool(availableProcessors * 4);

		this.attractionRewardCache = Caffeine.newBuilder() // eviter les appels répétés à RewardCentral
				.maximumSize(100_000)
				.expireAfterWrite(1, TimeUnit.HOURS)
				.build();

		//Pré-charge les points de récompense pour toutes les attractions dans le cache.
		preloadAttractionRewards();

		// Arrêter le chronomètre et le temps de démarrage.
		watch.stop();
		LOGGER.info("RewardsService initialized in {} ms", watch.getTime());
	}

	private void preloadAttractionRewards() {
		LOGGER.info("Preloading attraction rewards for {} attractions", attractions.size());
		attractions.forEach(attraction -> {
			UUID syntheticUserId = UUID.nameUUIDFromBytes(attraction.attractionId.toString().getBytes());
			// Récupérer les points
			int points = rewardsCentral.getAttractionRewardPoints(attraction.attractionId, syntheticUserId);
			//Stocke dans le cache.
			attractionRewardCache.put(attraction.attractionId, points);
		});
	}

	// Calcule asynchrone des récompenses pour un utilisateur.
	public CompletableFuture<Void> calculateRewards(User user) {
		return CompletableFuture.runAsync(() -> {
			//Récuperer les visites de l’utilisateur.
			List<VisitedLocation> userLocations = new ArrayList<>(user.getVisitedLocations());
			//Évite de recalculer les attractions déjà récompensées. Set est fait pour ça
			Set<UUID> alreadyRewarded = getAlreadyRewardedAttractions(user);

			// Parcourt les localisations et traite chaque visite pour voir si une récompense peut être ajoutée.
			userLocations.forEach(visitedLocation ->
					processVisitedLocation(user, visitedLocation, alreadyRewarded)
			);
		}, executorService);
	}

	public CompletableFuture<Void> calculateRewardsAsync(User user) {
		return CompletableFuture.runAsync(() -> calculateRewards(user), executorService);
	}

	// Retourner l’ensemble des attractionId déjà récompensées.
	private Set<UUID> getAlreadyRewardedAttractions(User user) {
		return user.getUserRewards().stream()
				.map(reward -> reward.attraction.attractionId)
				.collect(Collectors.toSet());
	}

	// 1 Ignorer les attractions déjà récompensées., 2 Vérifier la proximité., 3 Ajouter la récompense si proche.
	private void processVisitedLocation(User user, VisitedLocation visitedLocation, Set<UUID> alreadyRewarded) {
		attractions.stream()
				.filter(attraction -> !alreadyRewarded.contains(attraction.attractionId))
				.filter(attraction -> isWithinProximity(attraction, visitedLocation.location))
				.forEach(attraction -> addUserReward(user, visitedLocation, attraction));
	}

	// Vérifier si une attraction est dans le rayon de proximité.
	private boolean isWithinProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) <= proximityBuffer;
	}

	// Récuperer les points et ajoute une récompense à l'utilisateur.
	private void addUserReward(User user, VisitedLocation visitedLocation, Attraction attraction) {
		int rewardPoints = getRewardPoints(attraction, user.getUserId());
		user.addUserReward(new UserReward(visitedLocation, attraction, rewardPoints));
	}

	// Récuperer les points depuis le cache ou les calcule si manquant
	private int getRewardPoints(Attraction attraction, UUID userId) {
		return attractionRewardCache.get(
				attraction.attractionId,
				id -> rewardsCentral.getAttractionRewardPoints(attraction.attractionId, userId)
		);
	}

	// Lancer le calcul de récompenses pour tous les utilisateurs en parallèle.
	public CompletableFuture<Void> calculateRewardsForAllUsers(List<User> users) {
		List<CompletableFuture<Void>> futures = users.stream()
				.map(this::calculateRewards)
				.toList();

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
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

	// Gestion de la proximité
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}

	// MÉTHODES SPÉCIFIQUES POUR LES TESTS // ancienne methode
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
	}
}