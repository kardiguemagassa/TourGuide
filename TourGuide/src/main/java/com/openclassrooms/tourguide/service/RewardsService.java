package com.openclassrooms.tourguide.service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

	// pool de thread = 0
	@Value("${rewards.thread.pool.size:0}")
	private int threadPoolSize;

	private static final int BATCH_SIZE = 100; // Taille des lots pour le traitement parallèle
	private final ForkJoinPool forkJoinPool;

	private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// distance (en miles) autour d’une attraction pour dire qu’un utilisateur est proche
	// proximity in miles
	private final int defaultProximityBuffer = 10; //

	// tractionProximityRange = zone plus large pour déterminer si on prend en compte l’attraction (moins utilisée ici)
	private int proximityBuffer = defaultProximityBuffer;

	//private final int attractionProximityRange = 200;
	private final ThreadLocal<Integer> attractionProximityRange = ThreadLocal.withInitial(() -> 200);

	// GpsUtil → pour obtenir la liste des attractions.
	private final GpsUtil gpsUtil;

	//RewardCentral → pour obtenir le nombre de points à attribuer.
	private final RewardCentral rewardsCentral;

	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;

		// Déterminer la taille effective du pool de threads
		// j'ai 12 processeurs logiques  CPUs: 12 ==> effectivePoolSize = 12 * 2 = 24 => 24 threads sont créés
		int effectivePoolSize = threadPoolSize > 0 ? threadPoolSize : Runtime.getRuntime().availableProcessors() * 2;

		// Log de la taille choisie pour le pool
		LOGGER.info("Thread pool size initialized to {}", effectivePoolSize);

		/*  thread pool conçu pour les tâches récursives et parallélisables, Traite le lots de données en parallèle
			 à travers des arbres ou des graphes: ici on parcours n users, et chasque user a n position pour chaque
		     position, evaluer des attraction en lots
			 Initialisation du ForkJoinPool avec la même logique (optionnel) ==> meme numbre de thread 24
		 */
		this.forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors() * 2);
	}

	// changer la distance minimale pour qu’une attraction soit considérée comme "proche".
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}

	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

	// Ajouter en variable de classe
	private final ConcurrentMap<UUID, Lock> userLocks = new ConcurrentHashMap<>();

	public synchronized void calculateRewards(User user) {
		List<Attraction> attractions = gpsUtil.getAttractions();
		Set<String> rewardedAttractions = user.getUserRewards().stream()
				.map(r -> r.attraction.attractionName)
				.collect(Collectors.toSet());

		List<UserReward> newRewards = new ArrayList<>();

		// Créer une copie défensive de la liste des locations visitées
		// pour éviter ConcurrentModificationException
		List<VisitedLocation> visitedLocationsCopy = new ArrayList<>(user.getVisitedLocations());

		//Même si d'autres threads modifient la liste originale,
		// notre copie reste intacte pendant l'itération
		for (VisitedLocation location : visitedLocationsCopy) {
			for (Attraction attraction : attractions) {
				if (!rewardedAttractions.contains(attraction.attractionName)
						&& nearAttraction(location, attraction)) {
					newRewards.add(new UserReward(
							location,
							attraction,
							getRewardPoints(attraction, user))
					);
					rewardedAttractions.add(attraction.attractionName);
				}
			}
		}

		// Synchroniser l'ajout des nouvelles récompenses
		synchronized (user.getUserRewards()) {
			user.getUserRewards().addAll(newRewards);
		}
	}

	/*
	 * Récupère les lieux visités par l’utilisateur.
	 * Récupère toutes les attractions disponibles dans l’application.
	 * Pour chaque lieu visité et chaque attraction :
	 * On vérifie si l’utilisateur n’a pas déjà reçu une récompense pour cette attraction.
	 * Si l’utilisateur était proche de l’attraction :
	 * On lui ajoute une récompense (UserReward) avec les points récupérés depuis RewardCentral.
	 * @param user
	 */
	public void calculateRewardss(User user) {

		//List<VisitedLocation> userLocations = user.getVisitedLocations();

		// Copie défensive des lieux visités au lieu de lieu reéle comme c'était avant au dessus
		List<VisitedLocation> userLocations = new ArrayList<>(user.getVisitedLocations());

		/*Récupère la liste de toutes les attractions connues via gpsUtil,
		elles est utilisée pour vérifier si l’utilisateur a visité une attraction proche*/
		List<Attraction> attractions = gpsUtil.getAttractions();

		// Set concurrent pour les attractions déjà récompensées
		//Set<String> alreadyRewardedAttractions = new ConcurrentHashMap<>().newKeySet();
		Set<String> alreadyRewardedAttractions = ConcurrentHashMap.newKeySet();

		synchronized (user.getUserRewards()) {
			user.getUserRewards().parallelStream()
					.forEach(reward -> alreadyRewardedAttractions.add(reward.attraction.attractionName));
		}

		// Liste concurrente pour les nouvelles récompenses ( Sécurité des threads (thread safety)
		List<UserReward> newRewards = new CopyOnWriteArrayList<>();

		// Traitement parallèle avec ForkJoinPool
		forkJoinPool.submit(() ->
				userLocations.parallelStream().forEach(visitedLocation ->
						processAttractionsForLocation(visitedLocation, attractions, alreadyRewardedAttractions, newRewards, user)
				)
		).join();

		// Ajout thread-safe des nouvelles récompenses
		synchronized (user.getUserRewards()) {
			user.getUserRewards().addAll(newRewards);
		}
	}

	private void processAttractionsForLocation(VisitedLocation visitedLocation,
											   List<Attraction> attractions,
											   Set<String> alreadyRewardedAttractions,
											   List<UserReward> newRewards,
											   User user) {
		// Traitement par lots pour optimiser la localité des données
		IntStream.range(0, (attractions.size() + BATCH_SIZE - 1) / BATCH_SIZE)
				.parallel()
				.mapToObj(i -> attractions.subList(i * BATCH_SIZE,
						Math.min(attractions.size(), (i + 1) * BATCH_SIZE)))
				.forEach(batch -> processAttractionBatch(visitedLocation, batch,
						alreadyRewardedAttractions, newRewards, user));
	}

	private void processAttractionBatch(VisitedLocation visitedLocation,
										List<Attraction> batch,
										Set<String> alreadyRewardedAttractions,
										List<UserReward> newRewards,
										User user) {
		for (Attraction attraction : batch) {
			if (!alreadyRewardedAttractions.contains(attraction.attractionName)
					&& nearAttraction(visitedLocation, attraction)) {

				int rewardPoints = getRewardPoints(attraction, user);
				newRewards.add(new UserReward(visitedLocation, attraction, rewardPoints));
				alreadyRewardedAttractions.add(attraction.attractionName);
			}
		}
	}

	// Méthode pour fermer le pool proprement
	@PreDestroy
	public void shutdown() {
		forkJoinPool.shutdown();
		try {
			if (!forkJoinPool.awaitTermination(10, TimeUnit.SECONDS)) {
				forkJoinPool.shutdownNow();
			}
		} catch (InterruptedException e) {
			forkJoinPool.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}


	// Vérifie si une position est à moins de 200 miles d’une attraction
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		//return !(getDistance(attraction, location) > attractionProximityRange);
		return !(getDistance(attraction, location) > attractionProximityRange.get());
	}

	// Vérifie si un utilisateur était à moins de 10 miles de l’attraction ou une autre valeur définie
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return !(getDistance(attraction, visitedLocation.location) > proximityBuffer);
	}

	//Utilise un service externe (RewardCentral) pour connaître les points de récompense pour une attraction.
	private int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}

	// Calcule la distance en miles entre deux lieux à partir de leurs coordonnées GPS (latitude et longitude),
	// en utilisant la formule de la distance orthodromique (grand cercle).
	public double getDistance(Location loc1, Location loc2) {
		double lat1 = Math.toRadians(loc1.latitude);
		double lon1 = Math.toRadians(loc1.longitude);
		double lat2 = Math.toRadians(loc2.latitude);
		double lon2 = Math.toRadians(loc2.longitude);

		double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
				+ Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

		double nauticalMiles = 60 * Math.toDegrees(angle);
		double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
		return statuteMiles;
	}

}
