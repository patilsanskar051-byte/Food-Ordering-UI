package com.crave.dto;

import com.crave.entity.Food;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data structure representing persistent menu overrides stored in data/menu-overrides.json.
 * Encapsulates price overrides, availability toggles, and newly added custom items.
 */
public class MenuOverrideData {

    private Map<Long, Integer> priceOverrides = new HashMap<>();
    private Map<Long, Boolean> availabilityOverrides = new HashMap<>();
    private List<Food> customItems = new ArrayList<>();
    private String lastUpdated = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    public MenuOverrideData() {
    }

    public Map<Long, Integer> getPriceOverrides() {
        return priceOverrides;
    }

    public void setPriceOverrides(Map<Long, Integer> priceOverrides) {
        this.priceOverrides = priceOverrides != null ? priceOverrides : new HashMap<>();
    }

    public Map<Long, Boolean> getAvailabilityOverrides() {
        return availabilityOverrides;
    }

    public void setAvailabilityOverrides(Map<Long, Boolean> availabilityOverrides) {
        this.availabilityOverrides = availabilityOverrides != null ? availabilityOverrides : new HashMap<>();
    }

    public List<Food> getCustomItems() {
        return customItems;
    }

    public void setCustomItems(List<Food> customItems) {
        this.customItems = customItems != null ? customItems : new ArrayList<>();
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
