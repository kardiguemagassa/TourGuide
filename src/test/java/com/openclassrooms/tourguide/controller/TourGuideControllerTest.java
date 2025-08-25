package com.openclassrooms.tourguide.controller;

import com.openclassrooms.tourguide.dto.NearByAttractionDTO;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserPreferences;
import com.openclassrooms.tourguide.user.UserReward;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tripPricer.Provider;

import java.util.*;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TourGuideController.class)
class TourGuideControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TourGuideService tourGuideService;

    private User testUser;
    private List<User> testUsers;
    private VisitedLocation testLocation;
    private List<NearByAttractionDTO> testNearbyAttractions;
    private List<Attraction> testAttractions;
    private List<UserReward> testRewards;
    private List<Provider> testProviders;

    @BeforeEach
    void setUp() {
        // Setup test data
        testUser = new User(UUID.randomUUID(), "testUser", "123456789", "test@email.com");
        testUser.setUserPreferences(new UserPreferences());

        // a rental visited for the error
        VisitedLocation initialLocation = new VisitedLocation(
                testUser.getUserId(),
                new Location(33.817595, -117.922008),
                new Date()
        );
        testUser.addToVisitedLocations(initialLocation);

        User user2 = new User(UUID.randomUUID(), "internalUser1", "987654321", "internal1@email.com");
        User user3 = new User(UUID.randomUUID(), "internalUser2", "111222333", "internal2@email.com");
        testUsers = Arrays.asList(testUser, user2, user3);

        testLocation = new VisitedLocation(testUser.getUserId(), new Location(33.817595, -117.922008), new Date());

        // Setup nearby attractions DTO
        testNearbyAttractions = Arrays.asList(
                new NearByAttractionDTO(
                        "Disneyland",
                        33.817595, -117.922008,
                        33.817595, -117.922008,
                        0.0, 100
                ),
                new NearByAttractionDTO(
                        "Universal Studios",
                        34.138, -118.353,
                        33.817595, -117.922008,
                        25.5, 150
                )
        );

        // Setup simple attractions
        testAttractions = Arrays.asList(
                new Attraction("Disneyland", "Anaheim", "CA", 33.817595, -117.922008),
                new Attraction("Universal Studios", "Hollywood", "CA", 34.138, -118.353)
        );

        // Setup rewards
        testRewards = Arrays.asList(
                new UserReward(testLocation, new Attraction("Disneyland", "Anaheim", "CA", 33.817595, -117.922008), 100),
                new UserReward(testLocation, new Attraction("Universal Studios", "Hollywood", "CA", 34.138, -118.353), 150)
        );

        // Setup providers
        testProviders = Arrays.asList(
                new Provider(UUID.randomUUID(), "Provider1", 500.0),
                new Provider(UUID.randomUUID(), "Provider2", 750.0)
        );
    }

    // Test for @GetMapping (without parameters) - getAllUserNames()
    @Test
    void getAllUserNames_WithDefaultParams_ShouldReturnPagedUsers() throws Exception {
        // Given
        when(tourGuideService.getAllUsers()).thenReturn(testUsers);

        // When & Then
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userNames").isArray())
                .andExpect(jsonPath("$.userNames.length()").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void getAllUserNames_WithFilter_ShouldReturnFilteredUsers() throws Exception {
        // Given
        when(tourGuideService.getAllUsers()).thenReturn(testUsers);

        // When & Then
        mockMvc.perform(get("/users")
                        .param("startsWith", "internal"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userNames.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getUserProfile_Simple_ShouldWork() throws Exception {
        // Given
        when(tourGuideService.getUser("testUser")).thenReturn(testUser);

        // When & Then
        mockMvc.perform(get("/users/profile")
                        .param("userName", "testUser"))
                .andExpect(status().isOk())
                .andDo(result -> System.out.println("Response: " + result.getResponse().getContentAsString()));
    }

    @Test
    void getUserProfile_WithInvalidUser_ShouldReturnNotFound() throws Exception {
        // Given
        when(tourGuideService.getUser("invalidUser")).thenThrow(new RuntimeException("User not found"));

        // When & Then
        mockMvc.perform(get("/users/profile")
                        .param("userName", "invalidUser"))
                .andExpect(status().isNotFound());
    }

    // Tests for @GetMapping("/location") - getUserLocation()
    @Test
    void getUserLocation_WithValidUser_ShouldReturnLocation() throws Exception {
        // Given
        when(tourGuideService.getUser("testUser")).thenReturn(testUser);
        when(tourGuideService.getUserLocation(testUser)).thenReturn(testLocation);

        // When & Then
        mockMvc.perform(get("/users/location")
                        .param("userName", "testUser"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.location.latitude").value(33.817595))
                .andExpect(jsonPath("$.location.longitude").value(-117.922008));
    }

    @Test
    void getUserLocation_WithInvalidUser_ShouldReturnNotFound() throws Exception {
        // Given
        when(tourGuideService.getUser("invalidUser")).thenThrow(new RuntimeException("User not found"));

        // When & Then
        mockMvc.perform(get("/users/location")
                        .param("userName", "invalidUser"))
                .andExpect(status().isNotFound());
    }

    // Tests for @GetMapping("/nearby-attractions") - getNearbyAttractionsWithDetails()
    @Test
    void getNearbyAttractionsWithDetails_WithValidUser_ShouldReturnDetailedAttractions() throws Exception {
        // Given
        when(tourGuideService.getUser("testUser")).thenReturn(testUser);
        when(tourGuideService.getNearbyAttractionsWithDetails(testUser)).thenReturn(testNearbyAttractions);

        // When & Then
        mockMvc.perform(get("/users/nearby-attractions")
                        .param("userName", "testUser"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].attractionName").value("Disneyland"))
                .andExpect(jsonPath("$[0].attractionLatitude").value(33.817595))
                .andExpect(jsonPath("$[0].attractionLongitude").value(-117.922008))
                .andExpect(jsonPath("$[0].attractionRewardPoints").value(100));
    }

    @Test
    void getNearbyAttractionsWithDetails_WithInvalidUser_ShouldReturnNotFound() throws Exception {
        // Given
        when(tourGuideService.getUser("invalidUser")).thenThrow(new RuntimeException("User not found"));

        // When & Then
        mockMvc.perform(get("/users/nearby-attractions")
                        .param("userName", "invalidUser"))
                .andExpect(status().isNotFound());
    }

    // Tests for @GetMapping("/attractions") - getNearbyAttractions()
    @Test
    void getNearbyAttractions_WithValidUser_ShouldReturnSimpleAttractions() throws Exception {
        // Given
        when(tourGuideService.getUser("testUser")).thenReturn(testUser);
        when(tourGuideService.getUserLocation(testUser)).thenReturn(testLocation);
        when(tourGuideService.getNearByAttractions(testLocation)).thenReturn(testAttractions);

        // When & Then
        mockMvc.perform(get("/users/attractions")
                        .param("userName", "testUser"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].attractionName").value("Disneyland"))
                .andExpect(jsonPath("$[1].attractionName").value("Universal Studios"));
    }

    @Test
    void getNearbyAttractions_WithInvalidUser_ShouldReturnNotFound() throws Exception {
        // Given
        when(tourGuideService.getUser("invalidUser")).thenThrow(new RuntimeException("User not found"));

        // When & Then
        mockMvc.perform(get("/users/attractions")
                        .param("userName", "invalidUser"))
                .andExpect(status().isNotFound());
    }

    // Tests for @GetMapping("/rewards") - getUserRewards()
    @Test
    void getUserRewards_WithValidUser_ShouldReturnRewards() throws Exception {
        // Given
        when(tourGuideService.getUser("testUser")).thenReturn(testUser);
        when(tourGuideService.getUserRewards(testUser)).thenReturn(testRewards);

        // When & Then
        mockMvc.perform(get("/users/rewards")
                        .param("userName", "testUser"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].rewardPoints").value(100))
                .andExpect(jsonPath("$[1].rewardPoints").value(150));
    }

    @Test
    void getUserRewards_WithInvalidUser_ShouldReturnNotFound() throws Exception {
        // Given
        when(tourGuideService.getUser("invalidUser")).thenThrow(new RuntimeException("User not found"));

        // When & Then
        mockMvc.perform(get("/users/rewards")
                        .param("userName", "invalidUser"))
                .andExpect(status().isNotFound());
    }


    // Tests for @GetMapping("/trip-deals") - getTripDeals()
    @Test
    void getTripDeals_WithValidUser_ShouldReturnProviders() throws Exception {
        // Given
        when(tourGuideService.getUser("testUser")).thenReturn(testUser);
        when(tourGuideService.getTripDeals(testUser)).thenReturn(testProviders);

        // When & Then
        mockMvc.perform(get("/users/trip-deals")
                        .param("userName", "testUser"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Provider1"))
                .andExpect(jsonPath("$[0].price").value(500.0))
                .andExpect(jsonPath("$[1].name").value("Provider2"))
                .andExpect(jsonPath("$[1].price").value(750.0));
    }

    @Test
    void getTripDeals_WithInvalidUser_ShouldReturnNotFound() throws Exception {
        // Given
        when(tourGuideService.getUser("invalidUser")).thenThrow(new RuntimeException("User not found"));

        // When & Then
        mockMvc.perform(get("/users/trip-deals")
                        .param("userName", "invalidUser"))
                .andExpect(status().isNotFound());
    }
}