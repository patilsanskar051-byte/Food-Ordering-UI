# 🍔 Crave — Food, Made Beautiful

[![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Data JPA](https://img.shields.io/badge/Spring_Data_JPA-Hibernate-59666C?style=for-the-badge&logo=hibernate&logoColor=white)](https://spring.io/projects/spring-data-jpa)
[![Database](https://img.shields.io/badge/Database-H2_(Persistent)-003B57?style=for-the-badge&logo=sqlite&logoColor=white)](https://www.h2database.com/)
[![Frontend](https://img.shields.io/badge/Frontend-HTML5_%7C_CSS3_%7C_Vanilla_JS-E34F26?style=for-the-badge&logo=javascript&logoColor=white)](https://developer.mozilla.org/en-US/)

**Crave** is a modern, responsive full-stack food ordering web application. It features a sleek, dark-mode user interface connected to a robust, layered **Java Spring Boot** backend with persistent **H2 database** storage.

---

## 🌟 Features

### 🍽️ Frontend Experience
- **Interactive Menu:** High-resolution food cards displaying item ratings, pricing in INR (₹), descriptions, and badges (e.g., *BESTSELLER*, *CHEF'S PICK*).
- **Instant Search & Category Filtering:** Real-time search across dish names, categories, and descriptions, plus 1-click filtering by category (*Burgers*, *Pizza*, *Pasta*, *Snacks*, *Drinks*, *Desserts*).
- **Interactive Cart Drawer:** Slide-over cart drawer with quantity increment/decrement, real-time subtotal updates, and zero-delivery-fee calculations.
- **Delivery Destination Selector:** Quick location picker (*Hostel Block B*, *College Food Court*, *Indiranagar*, or custom text input) that saves to your order.
- **User Profile & Session:** Switch between *Guest User* and personalized profiles (e.g., *Aarav Sharma (Student)*, *Priya Patel (Foodie)*) with avatar initials and local session persistence.
- **Order History Drawer:** View placed orders in real-time, including order status (`PLACED`), timestamps, delivery addresses, and itemized receipts.

### ⚙️ Backend & API
- **Layered Architecture:** Clear separation of concerns with Controllers, Services, Repositories, Entities, and DTOs.
- **Server-Side Price Calculation:** Grand totals and item subtotals are calculated securely on the server using database prices (client-sent prices are never trusted).
- **Strict Data Validation:** Enforces positive quantities (≥ 1), non-empty item lists, and verifies food ID existence before persisting orders.
- **Historical Price Snapshotting:** Order items record the price and item name at the exact time of order placement.
- **Auto Database Seeder:** Seeds all 9 signature menu items automatically on initial application startup.
- **File-Based Persistence:** Orders and foods persist across server restarts via a file-based H2 database (`./data/cravedb`).
- **Single-Origin & CORS Support:** Frontend files are hosted directly by Spring Boot, with CORS configured for standalone development servers (e.g., VS Code Live Server).

---

## 🏗️ Technology Stack

| Component | Technology | Description |
| :--- | :--- | :--- |
| **Language** | Java 17+ (LTS) | Core backend language (compatible with Java 17, 21, and 25) |
| **Framework** | Spring Boot 3.2.5 | Modern backend web framework |
| **Web Layer** | Spring Web (MVC / REST) | RESTful API endpoints for foods and orders |
| **Persistence** | Spring Data JPA / Hibernate | Object-Relational Mapping (ORM) and repository layer |
| **Database** | H2 Database | Persistent file-based SQL database (`./data/cravedb`) |
| **Validation** | Jakarta Bean Validation | Request payload validation (`@Valid`, `@NotEmpty`) |
| **Build Tool** | Apache Maven 3.9+ | Build and dependency management |
| **Frontend** | HTML5, CSS3, Vanilla JS | Native DOM manipulation, CSS Variables, Fetch API |

---

## 📂 Project Structure

```text
Food_Ordering_system/
├── pom.xml                                  # Maven dependencies & build config
├── mvnw.cmd / mvnw                          # Maven Wrapper scripts
├── README.md                                # Project documentation
├── .gitignore                               # Git ignore configuration
├── index.html                               # Frontend HTML structure
├── style.css                                # Design system & dark theme styles
├── script.js                                # Frontend logic & API integration
├── data/                                    # Persistent H2 database storage
└── src/
    └── main/
        ├── java/com/crave/
        │   ├── CraveApplication.java       # Spring Boot main class
        │   ├── DataSeeder.java             # Database seeder for 9 menu items
        │   ├── config/
        │   │   └── WebConfig.java           # Cross-Origin (CORS) configuration
        │   ├── controller/
        │   │   ├── FoodController.java      # REST API: /api/foods
        │   │   └── OrderController.java     # REST API: /api/orders
        │   ├── dto/
        │   │   └── OrderRequest.java        # Request validation DTO
        │   ├── entity/
        │   │   ├── Food.java                # Food entity (id, name, price, etc.)
        │   │   ├── Order.java               # Order entity (id, total, status, etc.)
        │   │   └── OrderItem.java           # Order item snapshot entity
        │   ├── repository/
        │   │   ├── FoodRepository.java      # JPA repository with search & filter
        │   │   └── OrderRepository.java     # JPA repository for orders
        │   └── service/
        │       ├── FoodService.java         # Food query & search business logic
        │       └── OrderService.java        # Order calculation & validation logic
        └── resources/
            ├── application.properties       # App configuration & H2 settings
            └── static/                      # Static web assets served by Spring Boot
                ├── index.html
                ├── style.css
                └── script.js
```

---

## 🚀 Getting Started

### 1. Prerequisites
- **Java Development Kit (JDK):** Version 17 or newer ([Download JDK](https://www.oracle.com/java/technologies/downloads/))
- **Apache Maven:** Version 3.8+ (or use the included Maven wrapper)
- **Git:** For version control

Verify your setup:
```bash
java -version
mvn -version
```

### 2. Clone the Repository
```bash
git clone https://github.com/your-username/crave-food-ordering.git
cd crave-food-ordering
```

### 3. Build & Run the Application

Using **Maven**:
```bash
mvn spring-boot:run
```

Or using the **Maven Wrapper (Windows)**:
```bash
mvnw.cmd spring-boot:run
```

Or using the **Maven Wrapper (macOS / Linux)**:
```bash
./mvnw spring-boot:run
```

### 4. Open in Browser
Once you see `Started CraveApplication in ... seconds`:
- 🌐 **Web Application:** [http://localhost:8080/](http://localhost:8080/)
- 🗄️ **H2 Database Console:** [http://localhost:8080/h2-console](http://localhost:8080/h2-console)

---

## 📡 REST API Documentation

### A. Foods API

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/foods` | Returns all menu items |
| `GET` | `/api/foods/{id}` | Returns a single food item by ID |
| `GET` | `/api/foods?category={name}` | Filters food items by category (e.g. `Pizza`, `Burgers`, `Drinks`) |
| `GET` | `/api/foods?search={query}` | Searches items across name, category, or description |

#### Example: `GET /api/foods/1`
```json
{
  "id": 1,
  "name": "Classic Smash Burger",
  "category": "Burgers",
  "description": "Smoky grilled patty, melted cheese & signature sauce.",
  "price": 149,
  "rating": "4.9",
  "badge": "BESTSELLER",
  "image": "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?auto=format&fit=crop&w=600&q=75"
}
```

---

### B. Orders API

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/orders` | Creates a new order with server-calculated totals |
| `GET` | `/api/orders` | Retrieves all placed orders (sorted newest first) |
| `GET` | `/api/orders/{id}` | Retrieves a single order by ID |

#### Example Request: `POST /api/orders`
```json
{
  "customerName": "Aarav Sharma",
  "deliveryAddress": "Hostel Block B, Room 302",
  "items": [
    { "foodId": 1, "quantity": 2 },
    { "foodId": 6, "quantity": 1 }
  ]
}
```

#### Example Response (`201 Created`):
```json
{
  "id": 1,
  "customerName": "Aarav Sharma",
  "deliveryAddress": "Hostel Block B, Room 302",
  "totalAmount": 397,
  "status": "PLACED",
  "createdAt": "2026-10-11T12:08:35.123456",
  "items": [
    {
      "id": 1,
      "foodId": 1,
      "foodName": "Classic Smash Burger",
      "pricePerItem": 149,
      "quantity": 2,
      "subtotal": 298
    },
    {
      "id": 2,
      "foodId": 6,
      "foodName": "Iced Caramel Coffee",
      "pricePerItem": 99,
      "quantity": 1,
      "subtotal": 99
    }
  ]
}
```

---

## 🗄️ Database Inspection (H2 Web Console)

The H2 Web Console allows you to view relational tables and execute SQL queries:

1. Navigate to: [http://localhost:8080/h2-console](http://localhost:8080/h2-console)
2. Enter the following credentials:
   - **JDBC URL:** `jdbc:h2:file:./data/cravedb`
   - **User Name:** `sa`
   - **Password:** *(leave empty)*
3. Click **Connect**.
4. Useful queries:
   ```sql
   SELECT * FROM FOODS;
   SELECT * FROM ORDERS;
   SELECT * FROM ORDER_ITEMS;
   ```

---

## 🛡️ Business Logic & Validation Rules

- **Zero-Trust Pricing:** Client-sent prices are completely ignored. Subtotals and grand totals are calculated server-side directly from database records.
- **Invalid Food IDs:** If any food ID does not exist, the API responds with `400 Bad Request`.
- **Quantity Validation:** Each line item must have a quantity of at least `1`. Orders with `0` or negative quantities are rejected.
- **Empty Order Protection:** Orders with an empty `items` list return `400 Bad Request`.
- **Snapshot Pricing:** An order stores the exact price and item name at checkout time, ensuring that future price updates do not retroactively alter past receipts.

---

## 📦 How to Push to GitHub

To push this project to your GitHub account:

```bash
# 1. Initialize git (if not already done)
git init

# 2. Stage all files (target/ and data/ are automatically excluded by .gitignore)
git add .

# 3. Create your initial commit
git commit -m "feat: complete Crave food ordering application with Java Spring Boot backend"

# 4. Set main branch
git branch -M main

# 5. Link your remote GitHub repository
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPOSITORY.git

# 6. Push code to GitHub
git push -u origin main
```

---

## 📄 License

This project was developed for educational and academic purposes as a full-stack Java demonstration.
Feel free to use and modify it for your learning!
