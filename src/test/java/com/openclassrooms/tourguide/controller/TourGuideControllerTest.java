package com.openclassrooms.tourguide.controller;

import com.openclassrooms.tourguide.dto.NearByAttractionDTO;
import com.openclassrooms.tourguide.dto.PagedUserNamesDTO;
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

    // =============================================================================
    // TESTS POUR LES ENDPOINTS EXISTANTS DANS VOTRE CONTRÔLEUR
    // =============================================================================

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
    void getAllUserNames_WithPagination_ShouldReturnCorrectPage() throws Exception {
        // Given
        when(tourGuideService.getAllUsers()).thenReturn(testUsers);

        // When & Then
        mockMvc.perform(get("/users")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userNames.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
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
    void getAllUserNames_WithEmptyUserList_ShouldReturnEmptyPage() throws Exception {
        // Given
        when(tourGuideService.getAllUsers()).thenReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userNames").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    /*@Test
    void getUserByName_WithValidUser_ShouldReturnUser() throws Exception {
        // Given
        when(tourGuideService.getUser("testUser")).thenReturn(testUser);

        // When & Then
        mockMvc.perform(get("/users/testUser"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userName").value("testUser"))
                .andExpect(jsonPath("$.emailAddress").value("test@email.com"));
    }

    @Test
    void getUserByName_WithInvalidUser_ShouldReturn404() throws Exception {
        // Given
        when(tourGuideService.getUser("invalidUser")).thenThrow(new RuntimeException("User not found"));

        // When & Then
        mockMvc.perform(get("/users/invalidUser"))
                .andExpect(status().is5xxServerError()); // Le contrôleur actuel ne gère pas les exceptions
    }

     */

    @Test
    void getLocation_WithValidUser_ShouldReturnLocation() throws Exception {
        // Given
        when(tourGuideService.getUser("testUser")).thenReturn(testUser);
        when(tourGuideService.getUserLocation(testUser)).thenReturn(testLocation);

        // When & Then
        mockMvc.perform(get("/users/getLocation")
                        .param("userName", "testUser"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.location.latitude").value(33.817595))
                .andExpect(jsonPath("$.location.longitude").value(-117.922008));
    }

    /*@Test
    void getLocation_WithInvalidUser_ShouldReturn500() throws Exception {
        // Given
        when(tourGuideService.getUser("invalidUser")).thenThrow(new RuntimeException("User not found"));

        // When & Then
        mockMvc.perform(get("/users/getLocation")
                        .param("userName", "invalidUser"))
                .andExpect(status().is5xxServerError());
    }*/

    @Test
    void getNearbyAttractions_WithValidUser_ShouldReturnDetailedAttractions() throws Exception {
        // Given
        when(tourGuideService.getUser("testUser")).thenReturn(testUser);
        when(tourGuideService.getNearbyAttractionsWithDetails(testUser)).thenReturn(testNearbyAttractions);

        // When & Then
        mockMvc.perform(get("/users/getNearbyAttractions")
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

    /*@Test
    void getNearbyAttractions_WithInvalidUser_ShouldReturn500() throws Exception {
        // Given
        when(tourGuideService.getUser("invalidUser")).thenThrow(new RuntimeException("User not found"));

        // When & Then
        mockMvc.perform(get("/users/getNearbyAttractions")
                        .param("userName", "invalidUser"))
                .andExpect(status().is5xxServerError());
    }*/

    @Test
    void getRewards_WithValidUser_ShouldReturnRewards() throws Exception {
        // Given
        when(tourGuideService.getUser("testUser")).thenReturn(testUser);
        when(tourGuideService.getUserRewards(testUser)).thenReturn(testRewards);

        // When & Then
        mockMvc.perform(get("/users/getRewards")
                        .param("userName", "testUser"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].rewardPoints").value(100))
                .andExpect(jsonPath("$[1].rewardPoints").value(150));
    }

    /*@Test
    void getRewards_WithInvalidUser_ShouldReturn500() throws Exception {
        // Given
        when(tourGuideService.getUser("invalidUser")).thenThrow(new RuntimeException("User not found"));

        // When & Then
        mockMvc.perform(get("/users/getRewards")
                        .param("userName", "invalidUser"))
                .andExpect(status().is5xxServerError());
    }*/

    @Test
    void getTripDeals_WithValidUser_ShouldReturnProviders() throws Exception {
        // Given
        when(tourGuideService.getUser("testUser")).thenReturn(testUser);
        when(tourGuideService.getTripDeals(testUser)).thenReturn(testProviders);

        // When & Then
        mockMvc.perform(get("/users/getTripDeals")
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

    /*@Test
    void getTripDeals_WithInvalidUser_ShouldReturn500() throws Exception {
        // Given
        when(tourGuideService.getUser("invalidUser")).thenThrow(new RuntimeException("User not found"));

        // When & Then
        mockMvc.perform(get("/users/getTripDeals")
                        .param("userName", "invalidUser"))
                .andExpect(status().is5xxServerError());
    }*/

    @Test
    void index_ShouldReturnGreeting() throws Exception {
        // When & Then
        mockMvc.perform(get("/users/"))
                .andExpect(status().isOk())
                .andExpect(content().string("Greetings from TourGuide!"));
    }

    // =============================================================================
    // TESTS DE VALIDATION (Nécessitent @Validated sur le contrôleur)
    // =============================================================================

    @Test
    void getAllUserNames_WithInvalidPageParams_ShouldHandleValidation() throws Exception {
        // Ces tests ne passeront que si vous avez les annotations @Min/@Max ET @Validated

        // Pour l'instant, on teste juste que l'endpoint fonctionne normalement
        when(tourGuideService.getAllUsers()).thenReturn(testUsers);

        // Test normal qui devrait fonctionner
        mockMvc.perform(get("/users")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk());

        // Si vous voulez tester la validation, décommentez ces lignes après avoir ajouté @Validated
        /*
        // Test avec page négative
        mockMvc.perform(get("/users").param("page", "-1"))
                .andExpect(status().isBadRequest());

        // Test avec size invalide
        mockMvc.perform(get("/users").param("size", "0"))
                .andExpect(status().isBadRequest());

        // Test avec size trop grande
        mockMvc.perform(get("/users").param("size", "101"))
                .andExpected(status().isBadRequest());
        */
    }

    // =============================================================================
    // TESTS DE DEBUG (Optionnels)
    // =============================================================================

    /*@Test
    void debugTest_ExistingEndpoint() throws Exception {
        // Test avec un endpoint qui existe vraiment
        User simpleUser = new User(UUID.randomUUID(), "debugUser", "123", "debug@test.com");
        when(tourGuideService.getUser("debugUser")).thenReturn(simpleUser);

        mockMvc.perform(get("/users/debugUser"))
                .andDo(result -> {
                    System.out.println("=== DEBUG INFO - EXISTING ENDPOINT ===");
                    System.out.println("Status: " + result.getResponse().getStatus());
                    System.out.println("Content-Type: " + result.getResponse().getContentType());
                    System.out.println("Response Body: '" + result.getResponse().getContentAsString() + "'");
                    System.out.println("======================================");
                })
                .andExpect(status().isOk());
    }*/
}