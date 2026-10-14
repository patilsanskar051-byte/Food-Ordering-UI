package com.crave;

import com.crave.entity.Food;
import com.crave.repository.FoodRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the database with the nine food items from the original frontend.
 * Only inserts data if the foods table is empty (safe to restart).
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final FoodRepository foodRepository;

    public DataSeeder(FoodRepository foodRepository) {
        this.foodRepository = foodRepository;
    }

    @Override
    public void run(String... args) {
        // Only seed if the database is empty
        if (foodRepository.count() > 0) {
            System.out.println("Database already has food items. Skipping seed.");
            return;
        }

        System.out.println("Seeding database with food items...");

        foodRepository.save(new Food(
            1L, "Classic Smash Burger", "Burgers",
            "Smoky grilled patty, melted cheese & signature sauce.",
            149, "4.9", "BESTSELLER",
            "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?auto=format&fit=crop&w=600&q=75"
        ));

        foodRepository.save(new Food(
            2L, "Margherita Pizza", "Pizza",
            "Golden crust, fresh basil & bubbling mozzarella.",
            199, "4.8", "FAN FAVOURITE",
            "https://images.unsplash.com/photo-1579751626657-72bc17010498?auto=format&fit=crop&w=600&q=75"
        ));

        foodRepository.save(new Food(
            3L, "Creamy Alfredo Pasta", "Pasta",
            "Silky parmesan sauce with herbs and fresh cream.",
            179, "4.8", "CHEF'S PICK",
            "https://images.unsplash.com/photo-1473093295043-cdd812d0e601?auto=format&fit=crop&w=600&q=75"
        ));

        foodRepository.save(new Food(
            4L, "Loaded French Fries", "Snacks",
            "Crispy golden fries with our special seasoning.",
            89, "4.7", "QUICK BITE",
            "https://images.unsplash.com/photo-1573080496219-bb080dd4f877?auto=format&fit=crop&w=600&q=75"
        ));

        foodRepository.save(new Food(
            5L, "Fresh Garden Salad", "Snacks",
            "Crunchy greens, cherry tomatoes & house dressing.",
            129, "4.6", "FRESH PICK",
            "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?auto=format&fit=crop&w=600&q=75"
        ));

        foodRepository.save(new Food(
            6L, "Iced Caramel Coffee", "Drinks",
            "Chilled espresso, creamy milk & caramel drizzle.",
            99, "4.9", "TRENDING",
            "https://images.unsplash.com/photo-1461023058943-07fcbe16d735?auto=format&fit=crop&w=600&q=75"
        ));

        foodRepository.save(new Food(
            7L, "Chocolate Lava Cake", "Desserts",
            "Warm chocolate cake with a gooey centre.",
            129, "4.9", "SWEET TREAT",
            "https://images.unsplash.com/photo-1578985545062-69928b1d9587?auto=format&fit=crop&w=600&q=75"
        ));

        foodRepository.save(new Food(
            8L, "Mexican Veggie Pizza", "Pizza",
            "Bell peppers, sweet corn, jalapeños & cheese.",
            229, "4.7", "SPICY PICK",
            "https://images.unsplash.com/photo-1513104890138-7c749659a591?auto=format&fit=crop&w=600&q=75"
        ));

        foodRepository.save(new Food(
            9L, "Crispy Club Sandwich", "Snacks",
            "Toasted bread, fresh veggies & creamy filling.",
            119, "4.6", "LIGHT BITE",
            "https://images.unsplash.com/photo-1528735602780-2552fd46c7af?auto=format&fit=crop&w=600&q=75"
        ));

        System.out.println("Seeded " + foodRepository.count() + " food items.");
    }
}
