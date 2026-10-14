package com.crave.service;

import com.crave.dto.MenuOverrideData;
import com.crave.entity.Food;
import com.crave.exception.MenuValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MenuFileServiceTest {

    @TempDir
    Path tempDir;

    private MenuFileService menuFileService;

    @BeforeEach
    void setUp() {
        Path menuFile = tempDir.resolve("menu-overrides.json");
        menuFileService = new MenuFileService(menuFile.toString(), new ObjectMapper());
    }

    @Test
    void testPriceUpdateAndPersistence() {
        menuFileService.updatePrice(1L, 169);

        MenuOverrideData data = menuFileService.loadOverrides();
        assertEquals(169, data.getPriceOverrides().get(1L));
    }

    @Test
    void testInvalidPriceThrowsException() {
        assertThrows(MenuValidationException.class, () -> menuFileService.updatePrice(1L, 0));
        assertThrows(MenuValidationException.class, () -> menuFileService.updatePrice(1L, -50));
    }

    @Test
    void testAvailabilityUpdate() {
        menuFileService.updateAvailability(2L, false);

        MenuOverrideData data = menuFileService.loadOverrides();
        assertFalse(data.getAvailabilityOverrides().get(2L));

        menuFileService.updateAvailability(2L, true);
        data = menuFileService.loadOverrides();
        assertTrue(data.getAvailabilityOverrides().get(2L));
    }

    @Test
    void testAddCustomFoodItem() {
        Food newFood = new Food();
        newFood.setName("Paneer Tikka Roll");
        newFood.setCategory("Snacks");
        newFood.setDescription("Grilled paneer with mint chutney.");
        newFood.setPrice(139);
        newFood.setAvailable(true);

        Food created = menuFileService.addCustomFood(newFood, 9L);
        assertNotNull(created.getId());
        assertTrue(created.getId() > 9L);
        assertEquals("Paneer Tikka Roll", created.getName());
        assertEquals(139, created.getPrice());

        List<Food> customList = menuFileService.getCustomFoods();
        assertEquals(1, customList.size());
        assertEquals("Paneer Tikka Roll", customList.get(0).getName());
    }

    @Test
    void testAddCustomFoodValidation() {
        Food blankName = new Food();
        blankName.setName("");
        blankName.setCategory("Snacks");
        blankName.setPrice(100);
        assertThrows(MenuValidationException.class, () -> menuFileService.addCustomFood(blankName, 9L));

        Food blankCat = new Food();
        blankCat.setName("Burger");
        blankCat.setCategory("");
        blankCat.setPrice(100);
        assertThrows(MenuValidationException.class, () -> menuFileService.addCustomFood(blankCat, 9L));

        Food zeroPrice = new Food();
        zeroPrice.setName("Burger");
        zeroPrice.setCategory("Snacks");
        zeroPrice.setPrice(0);
        assertThrows(MenuValidationException.class, () -> menuFileService.addCustomFood(zeroPrice, 9L));
    }

    @Test
    void testDeleteCustomFoodItem() {
        Food custom = new Food();
        custom.setName("Crispy Momos");
        custom.setCategory("Snacks");
        custom.setPrice(120);
        custom.setAvailable(true);

        Food created = menuFileService.addCustomFood(custom, 9L);
        Long customId = created.getId();
        assertTrue(menuFileService.isCustomFood(customId));

        boolean deleted = menuFileService.deleteFoodItemOrOverride(customId);
        assertTrue(deleted);
        assertFalse(menuFileService.isCustomFood(customId));
        assertEquals(0, menuFileService.getCustomFoods().size());
    }

    @Test
    void testDeleteOverrideRestoresDefault() {
        menuFileService.updatePrice(1L, 250);
        menuFileService.updateAvailability(1L, false);

        MenuOverrideData data = menuFileService.loadOverrides();
        assertTrue(data.getPriceOverrides().containsKey(1L));
        assertTrue(data.getAvailabilityOverrides().containsKey(1L));

        boolean cleared = menuFileService.deleteFoodItemOrOverride(1L);
        assertTrue(cleared);

        MenuOverrideData reloaded = menuFileService.loadOverrides();
        assertFalse(reloaded.getPriceOverrides().containsKey(1L));
        assertFalse(reloaded.getAvailabilityOverrides().containsKey(1L));
    }
}
