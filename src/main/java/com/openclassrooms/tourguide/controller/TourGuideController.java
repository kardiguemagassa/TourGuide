package com.openclassrooms.tourguide.controller;

import java.util.List;
import java.util.stream.Collectors;

import com.openclassrooms.tourguide.dto.NearByAttractionDTO;
import com.openclassrooms.tourguide.dto.PagedUserNamesDTO;
import org.springframework.beans.factory.annotation.Autowired;
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

	@Autowired
	TourGuideService tourGuideService;
	
    @RequestMapping("/")
    public String index() {
        return "Greetings from TourGuide!";
    }
    
    @RequestMapping("/getLocation") 
    public VisitedLocation getLocation(@RequestParam String userName) {
    	return tourGuideService.getUserLocation(getUser(userName));
    }
    
    //  TODO: Change this method to no longer return a List of Attractions.
 	//  Instead: Get the closest five tourist attractions to the user - no matter how far away they are.
 	//  Return a new JSON object that contains:
    	// Name of Tourist attraction, 
        // Tourist attractions lat/long, 
        // The user's location lat/long, 
        // The distance in miles between the user's location and each of the attractions.
        // The reward points for visiting each Attraction.
        //    Note: Attraction reward points can be gathered from RewardsCentral

    // http://localhost:8080/users/getNearbyAttractions?userName=internalUser27
    @RequestMapping("/getNearbyAttractions")
    public List<NearByAttractionDTO> getNearbyAttractions(@RequestParam String userName) {
        User user = getUser(userName);
        return tourGuideService.getNearbyAttractionsWithDetails(user);
    }

    /**
     * Liste paginée et filtrée des noms d'utilisateurs.
     *
     * Exemple :
     * - /users?page=0&size=10 → 10 premiers utilisateurs
     * - /users?page=1&size=5&startsWith=internalUser1 → page 2 filtrée
     *
     */
    // http://localhost:8080/users?page=1&size=5&startsWith=internalUser1
    @GetMapping
    public PagedUserNamesDTO getAllUserNames(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String startsWith) {

        List<String> allUserNames = tourGuideService.getAllUsers()
                .stream()
                .map(User::getUserName)
                .filter(name -> startsWith == null || name.startsWith(startsWith))
                .collect(Collectors.toList());

        int fromIndex = Math.min(page * size, allUserNames.size());
        int toIndex = Math.min(fromIndex + size, allUserNames.size());

        List<String> pagedUserNames = allUserNames.subList(fromIndex, toIndex);

        return new PagedUserNamesDTO(pagedUserNames, page, size, allUserNames.size());
    }


    /**
     * Détail d'un utilisateur
     *
     */
    // http://localhost:8080/users/internalUser1
    @GetMapping("/{userName}")
    public User getUserByName(@PathVariable String userName) {
        return tourGuideService.getUser(userName);
    }



    /*@RequestMapping("/getNearbyAttractions")
    public List<Attraction> getNearbyAttractionss(@RequestParam String userName) {
    	VisitedLocation visitedLocation = tourGuideService.getUserLocation(getUser(userName));
    	return tourGuideService.getNearByAttractions(visitedLocation);
    }*/
    
    @RequestMapping("/getRewards") 
    public List<UserReward> getRewards(@RequestParam String userName) {
    	return tourGuideService.getUserRewards(getUser(userName));
    }
       
    @RequestMapping("/getTripDeals")
    public List<Provider> getTripDeals(@RequestParam String userName) {
    	return tourGuideService.getTripDeals(getUser(userName));
    }
    
    private User getUser(String userName) {
    	return tourGuideService.getUser(userName);
    }
   

}