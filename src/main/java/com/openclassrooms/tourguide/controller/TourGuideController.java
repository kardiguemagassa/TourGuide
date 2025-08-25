package com.openclassrooms.tourguide.controller;

import java.util.List;
import java.util.stream.Collectors;

import com.openclassrooms.tourguide.dto.NearByAttractionDTO;
import com.openclassrooms.tourguide.dto.PagedUserNamesDTO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;

import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import tripPricer.Provider;

@RestController
@RequestMapping("/users")
public class TourGuideController {

    private final TourGuideService tourGuideService;

    public TourGuideController(TourGuideService tourGuideService) {
        this.tourGuideService = tourGuideService;
    }

    /**
     * ENDPOINTS FOR USERS
     * Paginated and filtered list of usernames
     * localhost:8080/users → all users (page 0, size 10)
     * localhost:8080/users?page=1&size=5 → page 2 with 5 users
     * localhost:8080/users?startsWith=internal → filtered by prefix
     */
    @GetMapping
    public ResponseEntity<PagedUserNamesDTO> getAllUserNames(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String startsWith) {

        List<String> allUserNames = tourGuideService.getAllUsers()
                .stream()
                .map(User::getUserName)
                .filter(name -> startsWith == null || name.startsWith(startsWith))
                .collect(Collectors.toList());

        int fromIndex = Math.min(page * size, allUserNames.size());
        int toIndex = Math.min(fromIndex + size, allUserNames.size());

        List<String> pagedUserNames = allUserNames.subList(fromIndex, toIndex);

        PagedUserNamesDTO result = new PagedUserNamesDTO(pagedUserNames, page, size, allUserNames.size());
        return ResponseEntity.ok(result);
    }

    /**
     * User profile
     * localhost:8080/users/profile?userName=internalUser1
     */
    @GetMapping("/profile")
    public ResponseEntity<User> getUserProfile(@RequestParam String userName) {
        try {
            User user = getUser(userName);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * ENDPOINTS POUR LES LOCATIONS
     * Current position of a user
     * localhost:8080/users/location?userName=internalUser1
     */
    @GetMapping("/location")
    public ResponseEntity<VisitedLocation> getUserLocation(@RequestParam String userName) {
        try {
            User user = getUser(userName);
            VisitedLocation location = tourGuideService.getUserLocation(user);
            return ResponseEntity.ok(location);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * ENDPOINTS FOR ATTRACTIONS
     * Nearby attractions with full details
     * localhost:8080/users/nearby-attractions?userName=internalUser1
     */
    @GetMapping("/nearby-attractions")
    public ResponseEntity<List<NearByAttractionDTO>> getNearbyAttractionsWithDetails(
            @RequestParam String userName) {
        try {
            User user = getUser(userName);
            List<NearByAttractionDTO> attractions = tourGuideService.getNearbyAttractionsWithDetails(user);
            return ResponseEntity.ok(attractions);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Simple nearby attractions
     * localhost:8080/users/attractions?userName=internalUser1
     */
    @GetMapping("/attractions")
    public ResponseEntity<List<Attraction>> getNearbyAttractions(@RequestParam String userName) {
        try {
            User user = getUser(userName);
            VisitedLocation visitedLocation = tourGuideService.getUserLocation(user);
            List<Attraction> attractions = tourGuideService.getNearByAttractions(visitedLocation);
            return ResponseEntity.ok(attractions);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * ENDPOINTS FOR REWARDS AND OFFERS
     * User Rewards
     * localhost:8080/users/rewards?userName=internalUser1
     */
    @GetMapping("/rewards")
    public ResponseEntity<List<UserReward>> getUserRewards(@RequestParam String userName) {
        try {
            User user = getUser(userName);
            List<UserReward> rewards = tourGuideService.getUserRewards(user);
            return ResponseEntity.ok(rewards);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Travel offers for a user
     * localhost:8080/users/trip-deals?userName=internalUser1
     */
    @GetMapping("/trip-deals")
    public ResponseEntity<List<Provider>> getTripDeals(@RequestParam String userName) {
        try {
            User user = getUser(userName);
            List<Provider> deals = tourGuideService.getTripDeals(user);
            return ResponseEntity.ok(deals);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private User getUser(String userName) {
        return tourGuideService.getUser(userName);
    }
}