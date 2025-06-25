package com.openclassrooms.tourguide.dto;

import java.util.List;

public class PagedUserNamesDTO {

    private final List<String> userNames;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;

    public PagedUserNamesDTO(List<String> userNames, int page, int size, long totalElements) {
        this.userNames = userNames;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = (int) Math.ceil((double) totalElements / size);
    }

    public List<String> getUserNames() {
        return userNames;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }
}
