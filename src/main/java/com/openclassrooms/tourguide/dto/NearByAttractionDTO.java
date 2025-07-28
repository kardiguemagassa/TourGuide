package com.openclassrooms.tourguide.dto;

public class NearByAttractionDTO {

    private String attractionName;
    private double attractionLatitude;
    private double attractionLongitude;
    private double userLatitude;
    private double userLongitude;
    private double attractionDistanceInMiles;
    private int attractionRewardPoints;

    public NearByAttractionDTO(String attractionName, double attractionLatitude, double attractionLongitude, double userLatitude,
                               double userLongitude, double attractionDistanceInMiles, int attractionRewardPoints) {
        this.attractionName = attractionName;
        this.attractionLatitude = attractionLatitude;
        this.attractionLongitude = attractionLongitude;
        this.userLatitude = userLatitude;
        this.userLongitude = userLongitude;
        this.attractionDistanceInMiles = attractionDistanceInMiles;
        this.attractionRewardPoints = attractionRewardPoints;
    }

    public String getAttractionName() {
        return attractionName;
    }

    public void setAttractionName(String attractionName) {
        this.attractionName = attractionName;
    }

    public int getAttractionRewardPoints() {
        return attractionRewardPoints;
    }

    public void setAttractionRewardPoints(int attractionRewardPoints) {
        this.attractionRewardPoints = attractionRewardPoints;
    }

    public double getAttractionLatitude() {
        return attractionLatitude;
    }

    public void setAttractionLatitude(double attractionLatitude) {
        this.attractionLatitude = attractionLatitude;
    }

    public double getAttractionLongitude() {
        return attractionLongitude;
    }

    public void setAttractionLongitude(double attractionLongitude) {
        this.attractionLongitude = attractionLongitude;
    }

    public double getUserLatitude() {
        return userLatitude;
    }

    public void setUserLatitude(double userLatitude) {
        this.userLatitude = userLatitude;
    }

    public double getUserLongitude() {
        return userLongitude;
    }

    public void setUserLongitude(double userLongitude) {
        this.userLongitude = userLongitude;
    }

    public double getAttractionDistanceInMiles() {
        return attractionDistanceInMiles;
    }

    public void setAttractionDistanceInMiles(double attractionDistanceInMiles) {
        this.attractionDistanceInMiles = attractionDistanceInMiles;
    }
}
