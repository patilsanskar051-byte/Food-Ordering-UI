// API Base URL - empty string if served directly by Spring Boot (port 8080), else defaults to http://localhost:8080
const API_BASE = (window.location.protocol.startsWith("http") && window.location.port === "8080") ? "" : "http://localhost:8080";

// Food data loaded from the Java backend API (incorporates database items + data/menu-overrides.json)
let foods = [];

/**
 * Fetch all food items from the backend and render them.
 */
async function loadFoods() {
    try {
        const response = await fetch(`${API_BASE}/api/foods`);
        if (!response.ok) throw new Error("Failed to load menu");
        foods = await response.json();
        renderFoods();
    } catch (error) {
        console.error("Error loading foods:", error);
        showToast("Could not load menu. Please refresh the page.");
    }
}

const cart = new Map();
let activeCategory = "All";
let toastTimer;

const foodGrid = document.getElementById("food-grid");
const searchInput = document.getElementById("food-search");
const emptyState = document.getElementById("empty-state");
const toast = document.getElementById("toast");
const toastMessage = document.getElementById("toast-message");

function escapeHtml(str) {
    if (!str) return "";
    return String(str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

/**
 * Renders food cards into the menu grid with rich visuals,
 * badges, stock status, ratings, and smooth animations.
 * Respects availability overrides from data/menu-overrides.json.
 */
function renderFoods() {
    const query = (searchInput.value || "").trim().toLowerCase();
    const sortSelect = document.getElementById("food-sort");
    const sortMode = sortSelect ? sortSelect.value : "default";
    const clearBtn = document.getElementById("food-search-clear");
    const resultsCountElem = document.getElementById("menu-results-count");

    // Toggle clear search button visibility
    if (clearBtn) {
        clearBtn.classList.toggle("hidden", query.length === 0);
    }

    let filteredFoods = foods.filter(food => {
        const matchesCategory =
            activeCategory === "All" || (food.category && food.category.toLowerCase() === activeCategory.toLowerCase());

        const matchesSearch =
            (food.name && food.name.toLowerCase().includes(query)) ||
            (food.category && food.category.toLowerCase().includes(query)) ||
            (food.description && food.description.toLowerCase().includes(query));

        return matchesCategory && matchesSearch;
    });

    // Apply sorting
    if (sortMode === "price-asc") {
        filteredFoods.sort((a, b) => a.price - b.price);
    } else if (sortMode === "price-desc") {
        filteredFoods.sort((a, b) => b.price - a.price);
    } else if (sortMode === "rating-desc") {
        filteredFoods.sort((a, b) => parseFloat(b.rating || 0) - parseFloat(a.rating || 0));
    } else if (sortMode === "name-asc") {
        filteredFoods.sort((a, b) => a.name.localeCompare(b.name));
    }

    // Update results count indicator
    if (resultsCountElem) {
        if (query && activeCategory !== "All") {
            resultsCountElem.textContent = `Showing ${filteredFoods.length} ${filteredFoods.length === 1 ? 'match' : 'matches'} in ${activeCategory}`;
        } else if (query) {
            resultsCountElem.textContent = `Showing ${filteredFoods.length} ${filteredFoods.length === 1 ? 'match' : 'matches'} for "${query}"`;
        } else if (activeCategory !== "All") {
            resultsCountElem.textContent = `Showing ${filteredFoods.length} ${activeCategory} ${filteredFoods.length === 1 ? 'dish' : 'dishes'}`;
        } else {
            resultsCountElem.textContent = `Showing all ${filteredFoods.length} handcrafted dishes`;
        }
    }

    foodGrid.innerHTML = filteredFoods.map((food, index) => {
        const isOutOfStock = food.available === false;
        const safeName = escapeHtml(food.name);
        const safeDesc = escapeHtml(food.description);
        const safeCategory = escapeHtml(food.category);
        const safeBadge = escapeHtml(food.badge || "RECOMMENDED");
        const safeRating = escapeHtml(food.rating || "4.8");
        const isCustom = food.id > 9;

        return `
        <article class="food-card ${isOutOfStock ? 'out-of-stock' : ''}" data-food-id="${food.id}" style="animation-delay:${Math.min(index * 45, 450)}ms">
            <div class="food-image">
                <img
                    src="${food.image}"
                    alt="${safeName}"
                    loading="${index < 3 ? "eager" : "lazy"}"
                    decoding="async"
                    onerror="this.onerror=null; this.src='https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=600&q=75';"
                >
                <div class="food-image-overlay"></div>
                <span class="food-badge ${isOutOfStock ? 'badge-out-of-stock' : (isCustom ? 'badge-custom' : '')}">
                    ${isOutOfStock ? 'OUT OF STOCK' : safeBadge}
                </span>
                <span class="food-category-pill">${safeCategory}</span>
            </div>

            <div class="food-content">
                <div class="food-name-row">
                    <h3 class="food-name">${safeName}</h3>
                    <span class="food-rating" title="Rating: ${safeRating} out of 5">★ ${safeRating}</span>
                </div>

                <p class="food-description">${safeDesc}</p>

                <div class="food-card-bottom">
                    <div class="food-price-wrap">
                        <span class="food-price-label">Price</span>
                        <span class="food-price">₹${food.price}</span>
                    </div>
                    ${isOutOfStock ? `
                        <button class="add-button out-of-stock-btn" disabled aria-disabled="true">
                            <span>✕</span> Sold Out
                        </button>
                    ` : `
                        <button class="add-button" data-add="${food.id}" aria-label="Add ${safeName} to cart">
                            <span class="btn-icon">+</span> <span class="btn-text">Add</span>
                        </button>
                    `}
                </div>
            </div>
        </article>
        `;
    }).join("");

    emptyState.classList.toggle("hidden", filteredFoods.length !== 0);
    foodGrid.classList.toggle("hidden", filteredFoods.length === 0);
}

function selectCategory(category) {
    activeCategory = category;

    document.querySelectorAll(".category-chip").forEach(button => {
        button.classList.toggle(
            "active",
            button.dataset.category === category
        );
    });

    renderFoods();
}

function addToCart(foodId, buttonElem) {
    const food = foods.find(item => item.id === foodId);
    if (!food) return;

    if (food.available === false) {
        showToast(`${food.name} is currently out of stock.`);
        return;
    }

    const current = cart.get(foodId) || 0;
    cart.set(foodId, current + 1);

    updateCart();

    // Button micro-interaction: show temporary "✓ Added" state
    if (buttonElem && !buttonElem.disabled) {
        buttonElem.classList.add("added-feedback");
        const origHtml = buttonElem.innerHTML;
        buttonElem.innerHTML = `<span class="btn-icon">✓</span> <span class="btn-text">Added</span>`;
        setTimeout(() => {
            buttonElem.classList.remove("added-feedback");
            buttonElem.innerHTML = origHtml;
        }, 900);
    }

    // Subtle cart bump feedback on header cart icon and sidebar count
    const cartBtn = document.getElementById("header-cart");
    if (cartBtn) {
        cartBtn.classList.remove("bump");
        void cartBtn.offsetWidth;
        cartBtn.classList.add("bump");
        setTimeout(() => cartBtn.classList.remove("bump"), 400);
    }

    const countElem = document.getElementById("sidebar-cart-count");
    if (countElem) {
        countElem.classList.remove("bump");
        void countElem.offsetWidth;
        countElem.classList.add("bump");
        setTimeout(() => countElem.classList.remove("bump"), 400);
    }

    showToast(`${food.name} added to your cart`);
}

function updateCart() {
    let quantity = 0;

    cart.forEach(count => {
        quantity += count;
    });

    const countElem = document.getElementById("sidebar-cart-count");
    if (countElem) countElem.textContent = quantity;

    const cartBtn = document.getElementById("header-cart");
    if (cartBtn) {
        cartBtn.setAttribute("aria-label", `Open cart, ${quantity} items`);
    }
}

function showToast(message) {
    toastMessage.textContent = message;
    toast.classList.add("show");

    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
        toast.classList.remove("show");
    }, 2400);
}

/**
 * Lightweight, hardware-accelerated confetti burst on verified successful order.
 * Respects prefers-reduced-motion.
 */
function launchConfetti() {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
        return;
    }
    const canvas = document.createElement("canvas");
    canvas.id = "crave-confetti-canvas";
    canvas.style.cssText = "position:fixed;inset:0;width:100%;height:100%;z-index:9999;pointer-events:none;";
    document.body.appendChild(canvas);

    const ctx = canvas.getContext("2d");
    let width = (canvas.width = window.innerWidth);
    let height = (canvas.height = window.innerHeight);

    const colors = ["#FF2D8D", "#FF65AD", "#FFC66D", "#F8F8FC", "#4ADE80"];
    const pieces = Array.from({ length: 70 }, () => ({
        x: width * (0.35 + Math.random() * 0.3),
        y: height * 0.35,
        vx: (Math.random() - 0.5) * 16,
        vy: (Math.random() - 0.85) * 14 - 3,
        size: Math.random() * 8 + 4,
        color: colors[Math.floor(Math.random() * colors.length)],
        rotation: Math.random() * 360,
        rSpeed: (Math.random() - 0.5) * 10,
        opacity: 1
    }));

    let frameId;
    const startTime = performance.now();

    function draw(now) {
        const elapsed = now - startTime;
        if (elapsed > 2600) {
            cancelAnimationFrame(frameId);
            canvas.remove();
            return;
        }

        ctx.clearRect(0, 0, width, height);

        pieces.forEach(p => {
            p.x += p.vx;
            p.y += p.vy;
            p.vy += 0.38; // gravity
            p.rotation += p.rSpeed;
            if (elapsed > 1700) {
                p.opacity = Math.max(0, 1 - (elapsed - 1700) / 900);
            }

            ctx.save();
            ctx.translate(p.x, p.y);
            ctx.rotate((p.rotation * Math.PI) / 180);
            ctx.globalAlpha = p.opacity;
            ctx.fillStyle = p.color;
            ctx.fillRect(-p.size / 2, -p.size / 2, p.size, p.size * 1.35);
            ctx.restore();
        });

        frameId = requestAnimationFrame(draw);
    }

    frameId = requestAnimationFrame(draw);
}

function showCart() {
    const overlay = document.createElement("div");
    overlay.className = "cart-overlay";

    const closeCart = () => {
        overlay.remove();
        document.body.style.overflow = "";
    };

    if (cart.size === 0) {
        overlay.innerHTML = `
            <section class="cart-drawer" role="dialog" aria-modal="true" aria-labelledby="cart-title">
                <div class="cart-header">
                    <div>
                        <span class="section-kicker">YOUR CRAVINGS</span>
                        <h2 id="cart-title">Your Cart <span>(0)</span></h2>
                    </div>
                    <button class="cart-close" data-close-cart aria-label="Close cart">×</button>
                </div>
                <div class="cart-empty-view">
                    <div class="empty-illustration">🛍️</div>
                    <h3 style="font-family:'Outfit',sans-serif; font-size:22px; color:var(--text); margin-top:6px;">Your cart is empty</h3>
                    <p style="color:var(--muted); font-size:13px; max-width:300px; line-height:1.6;">Good food is waiting for you! Explore our chef-crafted menu and pick your favourites.</p>
                    <button class="primary-button" id="cart-empty-explore" style="margin-top:10px; padding:12px 24px; font-size:13px;">
                        Explore Menu <span>→</span>
                    </button>
                </div>
            </section>
        `;
        document.body.appendChild(overlay);
        document.body.style.overflow = "hidden";

        overlay.addEventListener("click", event => {
            if (event.target === overlay || event.target.closest("[data-close-cart]")) {
                closeCart();
                return;
            }
            if (event.target.closest("#cart-empty-explore")) {
                closeCart();
                navigateTo("menu");
            }
        });

        const escapeHandler = event => {
            if (event.key === "Escape") {
                closeCart();
                document.removeEventListener("keydown", escapeHandler);
            }
        };
        document.addEventListener("keydown", escapeHandler);
        return;
    }

    let total = 0;
    let quantity = 0;

    const rows = [...cart.entries()].map(([id, count]) => {
        const food = foods.find(item => item.id === id);
        if (!food) return "";
        const subtotal = food.price * count;

        total += subtotal;
        quantity += count;

        return `
            <div class="cart-row">
                <img src="${food.image}" alt="${food.name}" class="cart-item-thumb" onerror="this.onerror=null; this.src='https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=600&q=75';">
                <div class="cart-row-info">
                    <strong class="cart-item-title">${food.name}</strong>
                    <span class="cart-item-price">₹${food.price} each</span>
                </div>
                <div class="cart-quantity">
                    <button data-cart-action="decrease" data-id="${id}" aria-label="Decrease ${food.name}">−</button>
                    <span class="cart-count">${count}</span>
                    <button data-cart-action="increase" data-id="${id}" aria-label="Increase ${food.name}">+</button>
                </div>
                <div class="cart-row-right">
                    <strong class="cart-subtotal">₹${subtotal}</strong>
                    <button class="cart-remove-btn" data-cart-action="remove" data-id="${id}" title="Remove item" aria-label="Remove ${food.name}">✕</button>
                </div>
            </div>
        `;
    }).join("");

    overlay.innerHTML = `
        <section class="cart-drawer" role="dialog" aria-modal="true" aria-labelledby="cart-title">
            <div class="cart-header">
                <div>
                    <span class="section-kicker">YOUR CRAVINGS</span>
                    <h2 id="cart-title">Your Cart <span>(${quantity} ${quantity === 1 ? 'item' : 'items'})</span></h2>
                </div>
                <button class="cart-close" data-close-cart aria-label="Close cart">×</button>
            </div>

            <!-- Animated Checkout Stepper -->
            <div class="checkout-stepper">
                <div class="step active">
                    <span class="step-num">1</span>
                    <span class="step-label">Items</span>
                </div>
                <div class="step-line active"></div>
                <div class="step active">
                    <span class="step-num">2</span>
                    <span class="step-label">Delivery</span>
                </div>
                <div class="step-line"></div>
                <div class="step">
                    <span class="step-num">3</span>
                    <span class="step-label">Confirmation</span>
                </div>
            </div>

            <!-- Food-themed banner -->
            <div class="cart-promo-banner">
                <span class="promo-icon">✨</span>
                <div class="promo-text">
                    <strong>Freshly prepared for you</strong>
                    <small>Chef-curated ingredients • Delivered fast & hot</small>
                </div>
                <span class="promo-badge">FREE DELIVERY</span>
            </div>

            <div class="cart-items">${rows}</div>

            <!-- Delivery Details Card -->
            <div class="cart-delivery-card">
                <div class="delivery-card-header">
                    <span style="display:flex; align-items:center; gap:6px;">
                        <span style="color:var(--accent);">📍</span>
                        <span>Delivery Destination</span>
                    </span>
                    <button type="button" id="cart-quick-location-btn" style="background:transparent; border:0; color:var(--accent-light); font-size:11.5px; font-weight:600; cursor:pointer;">
                        Change preset <span>⌄</span>
                    </button>
                </div>
                <div class="checkout-input-group" id="group-checkout-name">
                    <label for="checkout-cust-name">Customer Name *</label>
                    <input type="text" id="checkout-cust-name" value="${currentUser.name || 'Guest User'}" required placeholder="Your full name" class="admin-input-control">
                    <span class="checkout-field-error" id="name-error">Please enter a valid customer name.</span>
                </div>
                <div class="checkout-input-group" id="group-checkout-addr">
                    <label for="checkout-cust-addr">Delivery Address *</label>
                    <input type="text" id="checkout-cust-addr" value="${currentLocation === 'Choose your location' ? '' : currentLocation}" required placeholder="Delivery location / hostel / room" class="admin-input-control">
                    <span class="checkout-field-error" id="addr-error">Please provide your delivery address.</span>
                </div>
            </div>

            <!-- Order Summary -->
            <div class="cart-summary">
                <div class="summary-line"><span>Items Subtotal</span><strong>₹${total}</strong></div>
                <div class="summary-line"><span>Delivery Fee</span><strong class="free-delivery-badge">FREE</strong></div>
                <div class="summary-line"><span>Packaging & Taxes</span><strong style="color:var(--muted); font-weight:500;">Included</strong></div>
                <div class="cart-grand-total">
                    <div>
                        <span>To Pay</span>
                        <small style="display:block; font-size:10.5px; color:var(--muted); font-weight:400;">Inclusive of all taxes</small>
                    </div>
                    <strong>₹${total}</strong>
                </div>
                <button class="checkout-button" data-checkout>
                    <span>Place Order (₹${total})</span>
                    <span style="font-size:16px;">→</span>
                </button>
                <p>Calculated server-side. Plain-text receipt generated automatically upon order placement.</p>
            </div>
        </section>
    `;

    document.body.appendChild(overlay);
    document.body.style.overflow = "hidden";

    overlay.addEventListener("click", event => {
        if (event.target === overlay || event.target.closest("[data-close-cart]")) {
            closeCart();
            return;
        }

        if (event.target.closest("#cart-quick-location-btn")) {
            showLocationSelector();
            return;
        }

        const actionButton = event.target.closest("[data-cart-action]");
        if (actionButton) {
            const id = Number(actionButton.dataset.id);
            const count = cart.get(id) || 0;

            if (actionButton.dataset.cartAction === "remove") {
                cart.delete(id);
            } else if (actionButton.dataset.cartAction === "increase") {
                cart.set(id, count + 1);
            } else if (count <= 1) {
                cart.delete(id);
            } else {
                cart.set(id, count - 1);
            }

            closeCart();
            updateCart();
            showCart();
            return;
        }

        const checkoutBtn = event.target.closest("[data-checkout]");
        if (checkoutBtn) {
            if (checkoutBtn.disabled) return;

            const nameInput = overlay.querySelector("#checkout-cust-name");
            const addrInput = overlay.querySelector("#checkout-cust-addr");
            const groupName = overlay.querySelector("#group-checkout-name");
            const groupAddr = overlay.querySelector("#group-checkout-addr");

            let hasError = false;
            const custName = nameInput ? nameInput.value.trim() : "";
            const custAddr = addrInput ? addrInput.value.trim() : "";

            if (!custName) {
                if (groupName) groupName.classList.add("has-error");
                hasError = true;
            } else if (groupName) {
                groupName.classList.remove("has-error");
            }

            if (!custAddr) {
                if (groupAddr) groupAddr.classList.add("has-error");
                hasError = true;
            } else if (groupAddr) {
                groupAddr.classList.remove("has-error");
            }

            if (hasError) {
                showToast("Please fill in required checkout details.");
                return;
            }

            // Sync with global user profile and location
            if (currentUser && custName !== currentUser.name) {
                currentUser.name = custName;
                setUser(currentUser);
            }
            if (custAddr && custAddr !== currentLocation) {
                setLocation(custAddr);
            }

            // Prevent duplicate submissions immediately
            checkoutBtn.disabled = true;
            const originalHtml = checkoutBtn.innerHTML;
            checkoutBtn.innerHTML = `<span class="button-spinner"></span> Placing order & generating receipt...`;

            // Build order request from cart
            const orderItems = [...cart.entries()].map(([foodId, qty]) => ({
                foodId: foodId,
                quantity: qty
            }));

            fetch(`${API_BASE}/api/orders`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    items: orderItems,
                    customerName: custName,
                    deliveryAddress: custAddr
                })
            })
            .then(async response => {
                if (!response.ok) {
                    const errData = await response.json().catch(() => ({}));
                    throw new Error(errData.message || errData.error || "Order placement failed");
                }
                return response.json();
            })
            .then(order => {
                cart.clear();
                updateCart();
                closeCart();
                showOrderConfirmation(order);
            })
            .catch(error => {
                console.error("Order error:", error);
                checkoutBtn.disabled = false;
                checkoutBtn.innerHTML = originalHtml;
                showToast(`Failed: ${error.message}`);
            });
        }
    });

    const escapeHandler = event => {
        if (event.key === "Escape") {
            closeCart();
            document.removeEventListener("keydown", escapeHandler);
        }
    };

    document.addEventListener("keydown", escapeHandler);
}

/**
 * Feature 1: Order Confirmation Dialog with Download Receipt button
 */
function showOrderConfirmation(order) {
    // Trigger confetti upon verified order creation
    launchConfetti();

    const overlay = document.createElement("div");
    overlay.className = "cart-overlay";

    overlay.innerHTML = `
        <section class="cart-drawer" role="dialog" aria-modal="true" style="max-width:480px; width:94%;">
            <div class="cart-header">
                <div>
                    <span class="section-kicker" style="color:var(--green);">ORDER PLACED SUCCESSFULLY</span>
                    <h2>Order Confirmed! <span>#${order.id}</span></h2>
                </div>
                <button class="cart-close" data-close-drawer>×</button>
            </div>

            <!-- Stepper all completed -->
            <div class="checkout-stepper" style="margin-top:14px;">
                <div class="step completed">
                    <span class="step-num">✓</span>
                    <span class="step-label">Items</span>
                </div>
                <div class="step-line completed"></div>
                <div class="step completed">
                    <span class="step-num">✓</span>
                    <span class="step-label">Delivery</span>
                </div>
                <div class="step-line completed"></div>
                <div class="step completed">
                    <span class="step-num">✓</span>
                    <span class="step-label">Confirmed</span>
                </div>
            </div>

            <div class="cart-items" style="display:flex; flex-direction:column; gap:16px; padding:16px 0;">
                <div style="background:var(--surface); border:1px solid var(--border); border-radius:16px; padding:24px 20px; text-align:center; display:grid; gap:10px; box-shadow:0 8px 30px rgba(0,0,0,0.45);">
                    <div class="success-checkmark-wrapper">
                        <svg class="checkmark-svg" viewBox="0 0 52 52">
                            <circle class="checkmark-circle" cx="26" cy="26" r="23" fill="none"/>
                            <path class="checkmark-check" fill="none" d="M14.1 27.2l7.1 7.2 16.7-16.8"/>
                        </svg>
                    </div>
                    <strong style="font-size:18px; color:var(--text); font-family:'Outfit',sans-serif;">Thank you, ${order.customerName}!</strong>
                    <p style="color:var(--muted); font-size:12.5px; line-height:1.5;">Your order has been recorded into persistent history, and an official plain-text receipt was generated.</p>
                    <div style="font-size:28px; font-weight:800; color:var(--accent-light); margin-top:4px; font-family:'Outfit',sans-serif;">₹${order.totalAmount}</div>
                    <div style="padding:8px 12px; background:var(--surface-elevated); border:1px solid var(--border); border-radius:10px; font-size:11.5px; color:var(--muted); display:inline-flex; align-items:center; gap:6px; margin:0 auto;">
                        <span>📍</span>
                        <span>Delivering to: <strong style="color:var(--text); font-weight:600;">${order.deliveryAddress}</strong></span>
                    </div>
                </div>

                <div style="display:grid; gap:10px; margin-top:4px;">
                    <a href="${API_BASE}/api/orders/${order.id}/receipt" download="receipt-order-${order.id}.txt" class="checkout-button" style="text-decoration:none; justify-content:center; gap:8px;">
                        <span>📥 Download Receipt (.txt)</span>
                    </a>
                    <button class="add-button" id="confirm-view-receipt-btn" style="padding:13px; border-radius:11px; justify-content:center; border:1px solid var(--border); background:var(--surface-elevated); color:var(--text);">
                        <span>👁 View Receipt Preview</span>
                    </button>
                    <button class="add-button" id="confirm-view-orders-btn" style="padding:13px; border-radius:11px; justify-content:center; border:1px solid var(--border); color:var(--muted); background:transparent;">
                        <span>◷ View in Order History</span>
                    </button>
                </div>
            </div>
        </section>
    `;

    document.body.appendChild(overlay);
    document.body.style.overflow = "hidden";

    const closeDialog = () => {
        overlay.remove();
        document.body.style.overflow = "";
    };

    overlay.addEventListener("click", event => {
        if (event.target === overlay || event.target.closest("[data-close-drawer]")) {
            closeDialog();
            return;
        }

        if (event.target.closest("#confirm-view-receipt-btn")) {
            closeDialog();
            showReceiptPreview(order.id);
            return;
        }

        if (event.target.closest("#confirm-view-orders-btn")) {
            closeDialog();
            showOrders();
            return;
        }
    });

    const escapeHandler = event => {
        if (event.key === "Escape") {
            closeDialog();
            document.removeEventListener("keydown", escapeHandler);
        }
    };
    document.addEventListener("keydown", escapeHandler);
}

/**
 * Feature 1: Receipt preview modal displaying plain-text receipt content from backend.
 */
async function showReceiptPreview(orderId) {
    const overlay = document.createElement("div");
    overlay.className = "cart-overlay";

    overlay.innerHTML = `
        <section class="cart-drawer" role="dialog" aria-modal="true" aria-labelledby="receipt-preview-title" style="max-width:560px; width:92%;">
            <div class="cart-header">
                <div>
                    <span class="section-kicker" style="color:var(--accent-light);">OFFICIAL RECEIPT</span>
                    <h2 id="receipt-preview-title">Order Receipt <span>#${orderId}</span></h2>
                </div>
                <button class="cart-close" data-close-drawer aria-label="Close receipt modal">×</button>
            </div>

            <div class="cart-items" style="display:flex; flex-direction:column; gap:14px; padding:18px 0;">
                <div id="receipt-text-container" style="color:var(--muted); font-size:12px; min-height:120px; display:flex; align-items:center; justify-content:center;">
                    <span class="button-spinner" style="margin-right:8px;"></span> Loading receipt from backend...
                </div>

                <div style="display:flex; gap:10px; margin-top:8px;">
                    <a id="download-receipt-link" href="${API_BASE}/api/orders/${orderId}/receipt" download="receipt-order-${orderId}.txt" class="checkout-button" style="flex:1; text-decoration:none; justify-content:center; gap:6px;">
                        <span>📥 Download .TXT</span>
                    </a>
                    <button id="copy-receipt-btn" class="add-button" style="padding:12px 18px; border-radius:10px; border:1px solid var(--border); background:var(--surface-elevated); color:var(--text); justify-content:center;">
                        <span>📋 Copy Text</span>
                    </button>
                </div>
            </div>
        </section>
    `;

    document.body.appendChild(overlay);
    document.body.style.overflow = "hidden";

    const closeDialog = () => {
        overlay.remove();
        document.body.style.overflow = "";
    };

    overlay.addEventListener("click", event => {
        if (event.target === overlay || event.target.closest("[data-close-drawer]")) {
            closeDialog();
            return;
        }

        if (event.target.closest("#copy-receipt-btn")) {
            const codeElem = overlay.querySelector(".receipt-code-block");
            const copyBtn = overlay.querySelector("#copy-receipt-btn");
            if (codeElem) {
                navigator.clipboard.writeText(codeElem.textContent).then(() => {
                    showToast("Receipt text copied to clipboard!");
                    if (copyBtn) {
                        const original = copyBtn.innerHTML;
                        copyBtn.innerHTML = `<span>✓ Copied!</span>`;
                        setTimeout(() => { copyBtn.innerHTML = original; }, 2000);
                    }
                }).catch(() => {
                    showToast("Could not access clipboard");
                });
            }
        }

        if (event.target.closest("#receipt-retry-btn")) {
            loadReceipt();
        }
    });

    const escapeHandler = event => {
        if (event.key === "Escape") {
            closeDialog();
            document.removeEventListener("keydown", escapeHandler);
        }
    };
    document.addEventListener("keydown", escapeHandler);

    async function loadReceipt() {
        const container = overlay.querySelector("#receipt-text-container");
        container.innerHTML = `<div style="display:flex; align-items:center; gap:8px;"><span class="button-spinner"></span> Loading receipt from backend...</div>`;
        try {
            const response = await fetch(`${API_BASE}/api/orders/${orderId}/receipt/preview`);
            if (!response.ok) {
                const errData = await response.json().catch(() => ({}));
                throw new Error(errData.message || `HTTP ${response.status}: Receipt not found on disk`);
            }
            const data = await response.json();
            container.innerHTML = `<pre class="receipt-code-block">${data.content}</pre>`;
        } catch (err) {
            container.innerHTML = `
                <div style="background:#15151D; border:1px dashed var(--danger-border); border-radius:12px; padding:20px; text-align:center; width:100%;">
                    <p style="color:var(--danger); font-size:13px; font-weight:600;">Receipt Unavailable</p>
                    <p style="color:var(--muted); font-size:12px; margin-top:4px;">${err.message}</p>
                    <button id="receipt-retry-btn" class="add-button" style="margin-top:12px; padding:8px 16px; border-radius:8px; background:var(--surface-elevated); color:var(--text); justify-content:center; display:inline-flex;">
                        ↻ Retry
                    </button>
                </div>
            `;
        }
    }

    loadReceipt();
}

/**
 * Location selection drawer
 */
function showLocationSelector() {
    const locations = [
        { label: "Hostel Block B, Room 302", desc: "Campus Residence" },
        { label: "College Campus, Food Court", desc: "Central Student Hub" },
        { label: "Indiranagar, 100ft Road", desc: "Bengaluru, Karnataka" },
        { label: "EcoWorld Tech Park, Tower 4", desc: "Bellandur Outer Ring Rd" }
    ];

    const overlay = document.createElement("div");
    overlay.className = "cart-overlay";

    overlay.innerHTML = `
        <section class="cart-drawer" role="dialog" aria-modal="true" aria-labelledby="location-title">
            <div class="cart-header">
                <div>
                    <span class="section-kicker">DELIVERY DESTINATION</span>
                    <h2 id="location-title">Choose location</h2>
                </div>
                <button class="cart-close" data-close-drawer aria-label="Close">×</button>
            </div>

            <div class="cart-items" style="display:flex; flex-direction:column; gap:12px; padding:20px 0;">
                <p style="color:var(--muted); font-size:12px;">Select a preset address or type your delivery address:</p>
                ${locations.map(loc => `
                    <div class="cart-row" data-pick-location="${loc.label}" style="cursor:pointer; padding:14px; border:1px solid ${currentLocation === loc.label ? 'var(--accent)' : 'var(--border)'}; border-radius:12px; background:var(--surface); display:flex; justify-content:space-between; align-items:center; transition:border-color .2s;">
                        <div>
                            <strong style="font-size:13px; color:var(--text); display:block;">⌖ ${loc.label}</strong>
                            <span style="font-size:11px; color:var(--muted);">${loc.desc}</span>
                        </div>
                        ${currentLocation === loc.label ? '<span style="color:var(--accent); font-size:16px; font-weight:bold;">✓</span>' : '<span style="color:var(--muted); font-size:12px;">Select</span>'}
                    </div>
                `).join("")}

                <div style="margin-top:10px; display:grid; gap:8px;">
                    <label style="color:var(--muted); font-size:11px;">Or enter custom location:</label>
                    <input type="text" id="custom-location-input" placeholder="e.g. Room 405, Block C..." value="${currentLocation === 'Choose your location' ? '' : currentLocation}" class="admin-input-control" style="font-size:13px;">
                    <button class="checkout-button" id="save-custom-location" style="justify-content:center; margin-top:5px;">
                        Set delivery location <span>✓</span>
                    </button>
                </div>
            </div>
        </section>
    `;

    document.body.appendChild(overlay);
    document.body.style.overflow = "hidden";

    const closeDrawer = () => {
        overlay.remove();
        document.body.style.overflow = "";
    };

    overlay.addEventListener("click", event => {
        if (event.target === overlay || event.target.closest("[data-close-drawer]")) {
            closeDrawer();
            return;
        }

        const pickBtn = event.target.closest("[data-pick-location]");
        if (pickBtn) {
            setLocation(pickBtn.dataset.pickLocation);
            closeDrawer();
            return;
        }

        if (event.target.closest("#save-custom-location")) {
            const val = document.getElementById("custom-location-input").value.trim();
            if (val) {
                setLocation(val);
                closeDrawer();
            } else {
                showToast("Please enter an address.");
            }
        }
    });

    const escapeHandler = event => {
        if (event.key === "Escape") {
            closeDrawer();
            document.removeEventListener("keydown", escapeHandler);
        }
    };
    document.addEventListener("keydown", escapeHandler);
}

/**
 * User Profile and Login drawer
 */
function showProfile() {
    const overlay = document.createElement("div");
    overlay.className = "cart-overlay";

    const isLoggedIn = currentUser && currentUser.loggedIn;

    overlay.innerHTML = `
        <section class="cart-drawer" role="dialog" aria-modal="true" aria-labelledby="profile-title">
            <div class="cart-header">
                <div>
                    <span class="section-kicker">YOUR ACCOUNT</span>
                    <h2 id="profile-title">${isLoggedIn ? "My Profile" : "User Login"}</h2>
                </div>
                <button class="cart-close" data-close-drawer aria-label="Close">×</button>
            </div>

            <div class="cart-items" style="display:flex; flex-direction:column; gap:16px; padding:20px 0;">
                <div style="display:flex; align-items:center; gap:15px; padding:18px; border:1px solid var(--border); border-radius:14px; background:var(--surface);">
                    <div class="avatar" style="width:52px; height:52px; font-size:22px; background:rgba(255,45,141,0.16); color:var(--accent-light); border:1px solid rgba(255,45,141,0.3); overflow:hidden; display:flex; align-items:center; justify-content:center;">
                        ${currentUser.pictureUrl ? `<img src="${escapeHtml(currentUser.pictureUrl)}" alt="${escapeHtml(currentUser.name)}" style="width:100%; height:100%; object-fit:cover;">` : (currentUser.name ? currentUser.name.charAt(0).toUpperCase() : 'C')}
                    </div>
                    <div style="display:grid; gap:4px; min-width:0;">
                        <strong style="font-size:15px; color:var(--text); white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${escapeHtml(currentUser.name)}</strong>
                        <span style="font-size:12px; color:var(--muted); white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${escapeHtml(currentUser.email || 'Crave Member')}</span>
                        <div style="display:flex; gap:6px; margin-top:2px; align-items:center; flex-wrap:wrap;">
                            <span class="food-badge" style="background:rgba(255,45,141,0.12); color:var(--accent-light); border-color:rgba(255,45,141,0.28);">
                                ${currentUser.authProvider === 'GOOGLE' ? 'GOOGLE SIGN-IN' : 'EMAIL ACCOUNT'}
                            </span>
                            ${currentUser.role === 'ADMIN' ? '<span class="food-badge" style="background:rgba(16,185,129,0.12); color:#10B981; border-color:rgba(16,185,129,0.3);">ADMIN</span>' : ''}
                        </div>
                    </div>
                </div>

                <div style="display:grid; gap:10px; margin-top:10px;">
                    <button class="checkout-button" id="profile-view-orders" style="justify-content:center;">
                        View My Orders <span>◷</span>
                    </button>
                    <button class="add-button" id="profile-logout-action" style="padding:12px; border-radius:10px; justify-content:center; color:var(--accent-light); border:1px solid rgba(255,45,141,0.3); background:rgba(255,45,141,0.08);">
                        Sign Out of Crave <span>⎋</span>
                    </button>
                </div>
            </div>
        </section>
    `;

    document.body.appendChild(overlay);
    document.body.style.overflow = "hidden";

    const closeDrawer = () => {
        overlay.remove();
        document.body.style.overflow = "";
    };

    overlay.addEventListener("click", event => {
        if (event.target === overlay || event.target.closest("[data-close-drawer]")) {
            closeDrawer();
            return;
        }

        if (event.target.closest("#profile-view-orders")) {
            closeDrawer();
            showOrders();
            return;
        }

        if (event.target.closest("#profile-logout-action") || event.target.closest("#profile-logout")) {
            closeDrawer();
            logoutUser();
            return;
        }
    });

    const form = overlay.querySelector("#login-form");
    if (form) {
        form.addEventListener("submit", e => {
            e.preventDefault();
            const name = document.getElementById("login-name").value.trim();
            const contact = document.getElementById("login-contact").value.trim();
            if (name) {
                setUser({ name, role: "Food enthusiast", email: contact || "user@crave.demo", loggedIn: true });
                closeDrawer();
                showToast(`Welcome, ${name}!`);
            }
        });
    }

    const escapeHandler = event => {
        if (event.key === "Escape") {
            closeDrawer();
            document.removeEventListener("keydown", escapeHandler);
        }
    };
    document.addEventListener("keydown", escapeHandler);
}

/**
 * Feature 2: Premium Order History Experience with Dual Tabs (H2 Database vs File History .jsonl),
 * real-time search, multi-field sorting, accurate metrics, expandable timelines, and Feature 1 receipt integration.
 */
async function showOrders(initialTab = "db") {
    let currentTab = initialTab;
    let currentSearch = "";
    let currentSort = "newest";
    let rawRecords = [];
    const expandedOrders = new Set();

    const overlay = document.createElement("div");
    overlay.className = "cart-overlay";

    overlay.innerHTML = `
        <section class="cart-drawer orders-drawer" role="dialog" aria-modal="true" aria-labelledby="orders-title">
            <div class="cart-header">
                <div class="orders-page-header">
                    <span class="section-kicker" style="color:var(--accent-light);">YOUR ORDER ARCHIVE</span>
                    <h2 id="orders-title">My Orders <span style="font-size:18px; color:var(--muted); font-weight:400;">(${currentTab === 'db' ? 'H2 DB' : 'JSONL'})</span></h2>
                </div>
                <button class="cart-close" data-close-drawer aria-label="Close orders modal">×</button>
            </div>

            <!-- Segmented Control Tabs -->
            <div class="tab-pill-container" role="tablist" style="margin-top:10px;">
                <button class="tab-pill ${currentTab === 'db' ? 'active' : ''}" data-tab="db" role="tab" aria-selected="${currentTab === 'db'}">
                    🗄️ Database Orders (H2)
                </button>
                <button class="tab-pill ${currentTab === 'file' ? 'active' : ''}" data-tab="file" role="tab" aria-selected="${currentTab === 'file'}">
                    📁 File History (.jsonl)
                </button>
            </div>

            <!-- Metrics Summary Bar -->
            <div class="orders-metrics-row" id="orders-metrics">
                <div class="order-metric-card">
                    <span class="order-metric-label">Total Orders</span>
                    <span class="order-metric-value" id="metric-total-orders">--</span>
                    <span class="order-metric-sub">Recorded in history</span>
                </div>
                <div class="order-metric-card">
                    <span class="order-metric-label">Total Spent</span>
                    <span class="order-metric-value accent" id="metric-total-spent">--</span>
                    <span class="order-metric-sub">All-time order total</span>
                </div>
                <div class="order-metric-card">
                    <span class="order-metric-label">Storage Layer</span>
                    <span class="order-metric-value" id="metric-source" style="font-size:14px; margin-top:2px;">${currentTab === 'db' ? 'H2 Database' : 'order-history.jsonl'}</span>
                    <span class="order-metric-sub">${currentTab === 'db' ? 'Relational JPA store' : 'Persistent JSONL file'}</span>
                </div>
            </div>

            <!-- Optional File History Notice Banner -->
            <div id="orders-file-banner" class="orders-source-banner" style="display:${currentTab === 'file' ? 'flex' : 'none'};">
                <span style="font-size:18px;">📁</span>
                <span>Records loaded directly from persistent <strong>data/order-history.jsonl</strong>. Fully preserved across application and database restarts.</span>
            </div>

            <!-- Search and Sorting Toolbar -->
            <div class="orders-toolbar">
                <div class="orders-search-wrapper">
                    <span class="orders-search-icon">🔍</span>
                    <input type="text" id="orders-search-input" class="orders-search-input" placeholder="Search by order #, item name, address..." value="" aria-label="Search orders">
                    <button id="orders-search-clear" class="orders-search-clear" title="Clear search">✕</button>
                </div>
                <select id="orders-sort-select" class="orders-sort-select" aria-label="Sort orders">
                    <option value="newest">Sort: Newest first</option>
                    <option value="oldest">Sort: Oldest first</option>
                    <option value="price_high">Total: High to Low</option>
                    <option value="price_low">Total: Low to High</option>
                </select>
            </div>

            <div class="orders-count-indicator" id="orders-count-indicator">
                <span>Loading order archive...</span>
            </div>

            <div class="cart-items" id="orders-container" style="display:flex; flex-direction:column; gap:14px; padding-bottom:20px;">
                <!-- Skeleton loader initially -->
                <div class="order-skeleton-card">
                    <div style="display:flex; justify-content:space-between; align-items:center;">
                        <div class="skeleton-shimmer" style="width:140px; height:20px;"></div>
                        <div class="skeleton-shimmer" style="width:70px; height:22px; border-radius:8px;"></div>
                    </div>
                    <div class="skeleton-shimmer" style="width:100%; height:64px; border-radius:10px;"></div>
                    <div style="display:flex; justify-content:space-between; align-items:center;">
                        <div class="skeleton-shimmer" style="width:90px; height:18px;"></div>
                        <div class="skeleton-shimmer" style="width:120px; height:34px; border-radius:9px;"></div>
                    </div>
                </div>
                <div class="order-skeleton-card">
                    <div style="display:flex; justify-content:space-between; align-items:center;">
                        <div class="skeleton-shimmer" style="width:140px; height:20px;"></div>
                        <div class="skeleton-shimmer" style="width:70px; height:22px; border-radius:8px;"></div>
                    </div>
                    <div class="skeleton-shimmer" style="width:100%; height:64px; border-radius:10px;"></div>
                    <div style="display:flex; justify-content:space-between; align-items:center;">
                        <div class="skeleton-shimmer" style="width:90px; height:18px;"></div>
                        <div class="skeleton-shimmer" style="width:120px; height:34px; border-radius:9px;"></div>
                    </div>
                </div>
            </div>
        </section>
    `;

    document.body.appendChild(overlay);
    document.body.style.overflow = "hidden";

    const closeDrawer = () => {
        overlay.remove();
        document.body.style.overflow = "";
    };

    const container = overlay.querySelector("#orders-container");
    const countIndicator = overlay.querySelector("#orders-count-indicator");
    const searchInput = overlay.querySelector("#orders-search-input");
    const searchClear = overlay.querySelector("#orders-search-clear");
    const sortSelect = overlay.querySelector("#orders-sort-select");
    const fileBanner = overlay.querySelector("#orders-file-banner");
    const metricOrders = overlay.querySelector("#metric-total-orders");
    const metricSpent = overlay.querySelector("#metric-total-spent");
    const metricSource = overlay.querySelector("#metric-source");
    const ordersTitle = overlay.querySelector("#orders-title");

    function formatOrderDate(dateStr) {
        if (!dateStr) return "Recently";
        try {
            const d = new Date(dateStr);
            if (isNaN(d.getTime())) return dateStr;
            return d.toLocaleDateString("en-IN", {
                day: "numeric",
                month: "short",
                year: "numeric"
            }) + " • " + d.toLocaleTimeString("en-IN", {
                hour: "2-digit",
                minute: "2-digit"
            });
        } catch {
            return dateStr;
        }
    }

    function getFilteredAndSortedRecords() {
        let list = [...rawRecords];

        // Search filtering
        if (currentSearch && currentSearch.trim()) {
            const q = currentSearch.trim().toLowerCase();
            list = list.filter(r => {
                const orderId = String(r.id || r.orderId || "");
                const custName = String(r.customerName || "").toLowerCase();
                const address = String(r.deliveryAddress || "").toLowerCase();
                const status = String(r.status || "").toLowerCase();
                const items = r.items || [];
                const itemMatch = items.some(it => String(it.foodName || "").toLowerCase().includes(q));
                const dateMatch = r.createdAt && formatOrderDate(r.createdAt).toLowerCase().includes(q);

                return orderId.includes(q) ||
                       custName.includes(q) ||
                       address.includes(q) ||
                       status.includes(q) ||
                       itemMatch ||
                       dateMatch;
            });
        }

        // Sorting
        if (currentSort === "newest") {
            list.sort((a, b) => {
                const tA = a.createdAt ? new Date(a.createdAt).getTime() : (a.id || a.orderId || 0);
                const tB = b.createdAt ? new Date(b.createdAt).getTime() : (b.id || b.orderId || 0);
                return tB - tA;
            });
        } else if (currentSort === "oldest") {
            list.sort((a, b) => {
                const tA = a.createdAt ? new Date(a.createdAt).getTime() : (a.id || a.orderId || 0);
                const tB = b.createdAt ? new Date(b.createdAt).getTime() : (b.id || b.orderId || 0);
                return tA - tB;
            });
        } else if (currentSort === "price_high") {
            list.sort((a, b) => (Number(b.totalAmount) || 0) - (Number(a.totalAmount) || 0));
        } else if (currentSort === "price_low") {
            list.sort((a, b) => (Number(a.totalAmount) || 0) - (Number(b.totalAmount) || 0));
        }

        return list;
    }

    function renderOrderCards() {
        const displayed = getFilteredAndSortedRecords();

        // Update count indicator
        if (rawRecords.length === 0) {
            countIndicator.innerHTML = `<span>No orders found in storage</span>`;
        } else if (currentSearch) {
            countIndicator.innerHTML = `
                <span>Showing <b>${displayed.length}</b> of <b>${rawRecords.length}</b> orders</span>
                <span style="color:var(--accent-light); font-size:11px;">Filtered by "${currentSearch}"</span>
            `;
        } else {
            countIndicator.innerHTML = `
                <span>Showing all <b>${displayed.length}</b> orders</span>
                <span>Sorted by ${sortSelect.options[sortSelect.selectedIndex].text.replace("Sort: ", "").replace("Total: ", "")}</span>
            `;
        }

        // Empty state for empty store
        if (rawRecords.length === 0) {
            container.innerHTML = `
                <div class="orders-empty-card">
                    <div class="orders-empty-icon">🛍️</div>
                    <h3>No orders yet</h3>
                    <p>There are no orders recorded in ${currentTab === 'db' ? 'the H2 database' : 'data/order-history.jsonl'} yet. Explore our delicious menu to place your first order!</p>
                    <button class="checkout-button" id="orders-empty-explore-btn" style="margin-top:8px; padding:12px 24px; font-size:13px;">
                        Explore Menu <span>↗</span>
                    </button>
                </div>
            `;
            return;
        }

        // Empty state for no search matches
        if (displayed.length === 0) {
            container.innerHTML = `
                <div class="orders-empty-card">
                    <div class="orders-empty-icon">🔍</div>
                    <h3>No matching orders found</h3>
                    <p>No orders matched your search query <b>"${currentSearch}"</b>. Try searching for a different food item, order ID, or delivery address.</p>
                    <button class="add-button" id="orders-reset-search-btn" style="margin-top:6px; padding:10px 18px; border-radius:10px; border:1px solid var(--border); color:var(--text); background:var(--surface-elevated);">
                        Clear Search Filter
                    </button>
                </div>
            `;
            return;
        }

        // Render card items
        container.innerHTML = displayed.map((record, index) => {
            const orderId = record.id || record.orderId;
            const dateFormatted = formatOrderDate(record.createdAt);
            const itemsList = record.items || [];
            const isExpanded = expandedOrders.has(orderId);

            const itemsHtml = itemsList.map(item => {
                const matchedFood = (typeof foods !== "undefined" && Array.isArray(foods))
                    ? (foods.find(f => f.id === item.foodId) || foods.find(f => f.name === item.foodName))
                    : null;
                const imgUrl = matchedFood && matchedFood.image
                    ? matchedFood.image
                    : "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=200&q=70";

                const unitPrice = item.pricePerItem || (item.quantity > 0 ? Math.round(item.subtotal / item.quantity) : item.subtotal);

                return `
                    <div class="order-item-line">
                        <img src="${imgUrl}" alt="${item.foodName}" class="order-item-img" loading="lazy" onerror="this.src='https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=200&q=70'; this.onerror=null;">
                        <div class="order-item-details">
                            <div style="min-width:0;">
                                <div class="order-item-name">${item.foodName}</div>
                                <div class="order-item-meta">₹${unitPrice} × ${item.quantity} ${item.quantity > 1 ? 'items' : 'item'}</div>
                            </div>
                            <div class="order-item-subtotal">₹${item.subtotal}</div>
                        </div>
                    </div>
                `;
            }).join("");

            return `
                <article class="order-card" style="animation-delay:${index * 50}ms;" data-order-id="${orderId}">
                    <!-- Card Top Bar -->
                    <div class="order-card-top">
                        <div>
                            <div class="order-id-group">
                                <span class="order-id-chip">Order <span>#${orderId}</span></span>
                                <span class="order-status-badge placed">${record.status || 'PLACED'}</span>
                            </div>
                            <div class="order-date-text">Placed on ${dateFormatted}</div>
                        </div>
                        <div style="text-align:right;">
                            <div class="order-card-total-amount">₹${record.totalAmount}</div>
                            <span style="font-size:10.5px; color:var(--green); font-weight:700;">FREE DELIVERY</span>
                        </div>
                    </div>

                    <!-- Ordered Items Preview Strip -->
                    <div class="order-items-preview">
                        ${itemsHtml}
                    </div>

                    <!-- Meta Information (Address & Customer) -->
                    <div class="order-card-meta-row">
                        ${record.deliveryAddress ? `
                            <div class="order-meta-item">
                                <span style="color:var(--accent-light);">📍</span>
                                <span>Delivering to: <strong>${record.deliveryAddress}</strong></span>
                            </div>
                        ` : ""}
                        ${record.customerName ? `
                            <div class="order-meta-item">
                                <span style="color:var(--accent-light);">👤</span>
                                <span>Customer: <strong>${record.customerName}</strong></span>
                            </div>
                        ` : ""}
                    </div>

                    <!-- Expandable Tasteful Timeline -->
                    ${isExpanded ? `
                        <div class="order-timeline-wrapper">
                            <div style="display:flex; justify-content:space-between; align-items:center; border-bottom:1px solid var(--border); padding-bottom:8px;">
                                <strong style="font-size:12px; color:var(--text); text-transform:uppercase; letter-spacing:0.5px;">Order Lifecycle Summary</strong>
                                <span style="font-size:11px; color:var(--green); font-weight:700;">VERIFIED RECORD</span>
                            </div>
                            <div class="order-timeline-step completed">
                                <div class="order-timeline-icon">✓</div>
                                <div class="order-timeline-info">
                                    <div class="order-timeline-title">Order Placed & Persisted</div>
                                    <div class="order-timeline-desc">${dateFormatted} • Stored in ${currentTab === 'db' ? 'H2 Relational Database' : 'data/order-history.jsonl'}</div>
                                </div>
                            </div>
                            <div class="order-timeline-step completed">
                                <div class="order-timeline-icon">✓</div>
                                <div class="order-timeline-info">
                                    <div class="order-timeline-title">Plain-Text Receipt Generated</div>
                                    <div class="order-timeline-desc">receipt-order-${orderId}.txt saved in data/receipts/</div>
                                </div>
                            </div>
                            <div class="order-timeline-step completed">
                                <div class="order-timeline-icon">✓</div>
                                <div class="order-timeline-info">
                                    <div class="order-timeline-title">Order Status: ${record.status || 'PLACED'}</div>
                                    <div class="order-timeline-desc">Payment settled • Fresh preparation queued</div>
                                </div>
                            </div>
                        </div>
                    ` : ""}

                    <!-- Card Actions -->
                    <div class="order-card-actions">
                        <a href="${API_BASE}/api/orders/${orderId}/receipt" download="receipt-order-${orderId}.txt" class="order-btn-primary" aria-label="Download receipt for order #${orderId}">
                            <span>📥 Download Receipt (.txt)</span>
                        </a>
                        <button class="order-btn-secondary" data-preview-receipt="${orderId}" aria-label="View receipt preview for order #${orderId}">
                            <span>👁 View Receipt</span>
                        </button>
                        <button class="order-btn-ghost" data-toggle-details="${orderId}" aria-expanded="${isExpanded}">
                            <span>${isExpanded ? 'Hide Details ▴' : 'Details ▾'}</span>
                        </button>
                    </div>
                </article>
            `;
        }).join("");
    }

    async function loadTabRecords(tab) {
        currentTab = tab;
        ordersTitle.innerHTML = `My Orders <span style="font-size:18px; color:var(--muted); font-weight:400;">(${tab === 'db' ? 'H2 DB' : 'JSONL'})</span>`;

        // Update tab pill states
        overlay.querySelectorAll(".tab-pill").forEach(btn => {
            const isActive = btn.dataset.tab === tab;
            btn.classList.toggle("active", isActive);
            btn.setAttribute("aria-selected", isActive);
        });

        // Update file banner visibility
        fileBanner.style.display = tab === "file" ? "flex" : "none";
        metricSource.textContent = tab === "db" ? "H2 Database" : "order-history.jsonl";

        // Show skeletons while fetching
        container.innerHTML = `
            <div class="order-skeleton-card">
                <div style="display:flex; justify-content:space-between; align-items:center;">
                    <div class="skeleton-shimmer" style="width:140px; height:20px;"></div>
                    <div class="skeleton-shimmer" style="width:70px; height:22px; border-radius:8px;"></div>
                </div>
                <div class="skeleton-shimmer" style="width:100%; height:64px; border-radius:10px;"></div>
                <div style="display:flex; justify-content:space-between; align-items:center;">
                    <div class="skeleton-shimmer" style="width:90px; height:18px;"></div>
                    <div class="skeleton-shimmer" style="width:120px; height:34px; border-radius:9px;"></div>
                </div>
            </div>
            <div class="order-skeleton-card">
                <div style="display:flex; justify-content:space-between; align-items:center;">
                    <div class="skeleton-shimmer" style="width:140px; height:20px;"></div>
                    <div class="skeleton-shimmer" style="width:70px; height:22px; border-radius:8px;"></div>
                </div>
                <div class="skeleton-shimmer" style="width:100%; height:64px; border-radius:10px;"></div>
                <div style="display:flex; justify-content:space-between; align-items:center;">
                    <div class="skeleton-shimmer" style="width:90px; height:18px;"></div>
                    <div class="skeleton-shimmer" style="width:120px; height:34px; border-radius:9px;"></div>
                </div>
            </div>
        `;
        countIndicator.innerHTML = `<span>Fetching ${tab === 'db' ? 'database' : 'file'} records...</span>`;

        try {
            const url = tab === "db" ? `${API_BASE}/api/orders` : `${API_BASE}/api/orders/history/file`;
            const response = await fetch(url);
            if (!response.ok) throw new Error(`HTTP ${response.status}: Failed to load orders from server`);
            const data = await response.json();
            rawRecords = Array.isArray(data) ? data : [];

            // Calculate genuine metrics from actual loaded records
            metricOrders.textContent = `${rawRecords.length} ${rawRecords.length === 1 ? 'Order' : 'Orders'}`;
            const totalSpent = rawRecords.reduce((sum, r) => sum + (Number(r.totalAmount) || 0), 0);
            metricSpent.textContent = `₹${totalSpent}`;

            renderOrderCards();

        } catch (err) {
            console.error("Order load error:", err);
            metricOrders.textContent = "Error";
            metricSpent.textContent = "Error";
            countIndicator.innerHTML = `<span style="color:var(--danger);">Error loading records</span>`;
            container.innerHTML = `
                <div class="orders-error-card">
                    <div class="orders-empty-icon" style="background:rgba(255,77,109,0.12); border-color:rgba(255,77,109,0.3); color:var(--danger);">⚠️</div>
                    <h3>Unable to load orders</h3>
                    <p>We encountered an issue fetching records from the server: <b>${err.message}</b>. Please ensure the backend is running and try again.</p>
                    <button class="checkout-button" id="orders-retry-fetch-btn" style="margin-top:8px; padding:12px 24px; font-size:13px;">
                        ↻ Retry Loading
                    </button>
                </div>
            `;
        }
    }

    // Search input handler
    searchInput.addEventListener("input", () => {
        currentSearch = searchInput.value;
        searchClear.style.display = currentSearch ? "block" : "none";
        renderOrderCards();
    });

    // Clear search handler
    searchClear.addEventListener("click", () => {
        searchInput.value = "";
        currentSearch = "";
        searchClear.style.display = "none";
        renderOrderCards();
        searchInput.focus();
    });

    // Sort select handler
    sortSelect.addEventListener("change", () => {
        currentSort = sortSelect.value;
        renderOrderCards();
    });

    // Delegated click handler
    overlay.addEventListener("click", event => {
        if (event.target === overlay || event.target.closest("[data-close-drawer]")) {
            closeDrawer();
            return;
        }

        const tabBtn = event.target.closest("[data-tab]");
        if (tabBtn) {
            const newTab = tabBtn.dataset.tab;
            if (newTab !== currentTab) {
                loadTabRecords(newTab);
            }
            return;
        }

        const previewBtn = event.target.closest("[data-preview-receipt]");
        if (previewBtn) {
            const orderId = previewBtn.dataset.previewReceipt;
            showReceiptPreview(orderId);
            return;
        }

        const toggleBtn = event.target.closest("[data-toggle-details]");
        if (toggleBtn) {
            const orderId = Number(toggleBtn.dataset.toggleDetails) || toggleBtn.dataset.toggleDetails;
            if (expandedOrders.has(orderId)) {
                expandedOrders.delete(orderId);
            } else {
                expandedOrders.add(orderId);
            }
            renderOrderCards();
            return;
        }

        if (event.target.closest("#orders-empty-explore-btn")) {
            closeDrawer();
            const foodSection = document.getElementById("food-section");
            if (foodSection) {
                foodSection.scrollIntoView({ behavior: "smooth", block: "start" });
            }
            return;
        }

        if (event.target.closest("#orders-reset-search-btn")) {
            searchInput.value = "";
            currentSearch = "";
            searchClear.style.display = "none";
            renderOrderCards();
            return;
        }

        if (event.target.closest("#orders-retry-fetch-btn")) {
            loadTabRecords(currentTab);
            return;
        }
    });

    const escapeHandler = event => {
        if (event.key === "Escape") {
            closeDrawer();
            document.removeEventListener("keydown", escapeHandler);
        }
    };
    document.addEventListener("keydown", escapeHandler);

    // Initial load
    loadTabRecords(currentTab);
}

/**
 * Feature 3: Premium Admin Menu Management Portal with Passkey Protection
 * and File-Backed Menu Overrides (data/menu-overrides.json).
 */
function showAdminPortal() {
    const adminToken = sessionStorage.getItem("crave_admin_token");

    if (!adminToken) {
        showAdminAuthModal();
    } else {
        showAdminDashboard(adminToken);
    }
}

/**
 * Displays the Admin Authentication Modal.
 */
function showAdminAuthModal() {
    const overlay = document.createElement("div");
    overlay.className = "cart-overlay";

    overlay.innerHTML = `
        <section class="cart-drawer" role="dialog" aria-modal="true" aria-labelledby="admin-auth-title" style="max-width:440px; width:92%;">
            <div class="cart-header">
                <div>
                    <span class="section-kicker" style="color:var(--accent);">RESTRICTED ACCESS</span>
                    <h2 id="admin-auth-title">Admin Portal Unlock</h2>
                </div>
                <button class="cart-close" data-close-drawer aria-label="Close">×</button>
            </div>

            <div class="cart-items" style="display:flex; flex-direction:column; gap:16px; padding:20px 0;">
                <div style="padding:14px; background:#111118; border:1px solid var(--border); border-radius:12px; font-size:11.5px; color:var(--muted); line-height:1.5;">
                    🔐 Administrative actions (price modifications, availability toggles, custom food items, and resets) are protected via token authorization and written atomically to <b>data/menu-overrides.json</b>.
                </div>

                <form id="admin-auth-form" style="display:grid; gap:14px;">
                    <div class="checkout-input-group" id="group-admin-key">
                        <label for="admin-passkey-input" style="font-size:11.5px; color:var(--text); font-weight:600;">Admin Secret Passkey *</label>
                        <input type="password" id="admin-passkey-input" value="" required placeholder="Enter admin secret key" class="admin-input-control" autocomplete="current-password" autofocus>
                        <small style="font-size:10.5px; color:var(--muted); margin-top:2px;">Configured via <code style="color:var(--accent-light); font-weight:700;">crave.admin.secret</code> application property.</small>
                    </div>

                    <div id="admin-auth-error" style="color:var(--danger); font-size:11.5px; background:var(--danger-bg); border:1px solid var(--danger-border); border-radius:8px; padding:8px 12px; display:none;"></div>

                    <button type="submit" id="admin-auth-submit-btn" class="checkout-button" style="justify-content:center; margin-top:4px;">
                        <span>Verify & Unlock Portal →</span>
                    </button>
                </form>
            </div>
        </section>
    `;

    document.body.appendChild(overlay);
    document.body.style.overflow = "hidden";

    const closeDialog = () => {
        overlay.remove();
        document.body.style.overflow = "";
    };

    overlay.addEventListener("click", event => {
        if (event.target === overlay || event.target.closest("[data-close-drawer]")) {
            closeDialog();
        }
    });

    const escapeHandler = event => {
        if (event.key === "Escape") {
            closeDialog();
            document.removeEventListener("keydown", escapeHandler);
        }
    };
    document.addEventListener("keydown", escapeHandler);

    const form = overlay.querySelector("#admin-auth-form");
    const submitBtn = overlay.querySelector("#admin-auth-submit-btn");

    form.addEventListener("submit", async e => {
        e.preventDefault();
        const inputKey = document.getElementById("admin-passkey-input").value.trim();
        const errElem = document.getElementById("admin-auth-error");

        if (!inputKey) {
            errElem.textContent = "Please enter the admin passkey.";
            errElem.style.display = "block";
            return;
        }

        submitBtn.disabled = true;
        const originalHtml = submitBtn.innerHTML;
        submitBtn.innerHTML = `<span class="button-spinner"></span> Verifying passkey...`;
        errElem.style.display = "none";

        try {
            const response = await fetch(`${API_BASE}/api/admin/verify`, {
                method: "POST",
                headers: { "X-Admin-Token": inputKey }
            });

            if (!response.ok) {
                submitBtn.disabled = false;
                submitBtn.innerHTML = originalHtml;
                errElem.textContent = "Access Denied: Invalid admin passkey (403 Forbidden).";
                errElem.style.display = "block";
                return;
            }

            sessionStorage.setItem("crave_admin_token", inputKey);
            closeDialog();
            showToast("Admin session unlocked!");
            showAdminDashboard(inputKey);
        } catch (err) {
            submitBtn.disabled = false;
            submitBtn.innerHTML = originalHtml;
            errElem.textContent = `Verification error: ${err.message}`;
            errElem.style.display = "block";
        }
    });
}

/**
 * Displays the complete Admin Menu Management Dashboard with genuine KPI analytics,
 * real-time search, filters, inline price editing, stock toggles, and custom food creation.
 */
function showAdminDashboard(token) {
    let adminSearchQuery = "";
    let adminFilterMode = "all"; // "all", "instock", "outstock", "custom", "seeded"

    const overlay = document.createElement("div");
    overlay.className = "cart-overlay";

    overlay.innerHTML = `
        <section class="cart-drawer admin-portal-drawer" role="dialog" aria-modal="true" aria-labelledby="admin-dashboard-title">
            <div class="cart-header">
                <div>
                    <span class="section-kicker" style="color:var(--accent);">FILE-BACKED MENU MANAGEMENT</span>
                    <h2 id="admin-dashboard-title">Admin Dashboard</h2>
                </div>
                <div style="display:flex; gap:8px; align-items:center;">
                    <button id="admin-add-food-btn" class="checkout-button" style="padding:8px 14px; font-size:11.5px; border-radius:9px; box-shadow:none;">
                        <span>+ Add Food Item</span>
                    </button>
                    <button id="admin-logout-btn" class="btn-pill btn-pill-dark" style="color:var(--accent-light); font-size:11.5px;">
                        <span>🚪 Lock</span>
                    </button>
                    <button class="cart-close" data-close-drawer aria-label="Close">×</button>
                </div>
            </div>

            <div class="orders-source-banner" style="margin-top:10px; margin-bottom:12px;">
                <span style="font-size:18px;">📁</span>
                <span>Menu overrides & custom additions are stored persistently in <b>data/menu-overrides.json</b> and take effect in customer pricing immediately.</span>
            </div>

            <!-- Genuine KPI Stats Grid -->
            <div class="admin-stats-grid" id="admin-stats-grid">
                <div class="admin-stat-card">
                    <span class="stat-icon">📦</span>
                    <span class="stat-label">Total Orders</span>
                    <span class="stat-value" id="kpi-orders">--</span>
                    <span class="stat-sub">Persisted in database</span>
                </div>
                <div class="admin-stat-card">
                    <span class="stat-icon">💰</span>
                    <span class="stat-label">Total Revenue</span>
                    <span class="stat-value accent" id="kpi-revenue">--</span>
                    <span class="stat-sub">Verified customer orders</span>
                </div>
                <div class="admin-stat-card">
                    <span class="stat-icon">🍽️</span>
                    <span class="stat-label">Menu Items</span>
                    <span class="stat-value" id="kpi-menu">--</span>
                    <span class="stat-sub" id="kpi-menu-sub">Loaded catalog items</span>
                </div>
                <div class="admin-stat-card">
                    <span class="stat-icon">⚠️</span>
                    <span class="stat-label">Out of Stock</span>
                    <span class="stat-value" id="kpi-stockout" style="color:var(--danger);">--</span>
                    <span class="stat-sub" id="kpi-stockout-sub">Currently unavailable</span>
                </div>
            </div>

            <!-- Toolbar: Search & Filter -->
            <div class="admin-toolbar">
                <div class="admin-search-wrapper">
                    <span class="orders-search-icon">🔍</span>
                    <input type="search" id="admin-menu-search" placeholder="Filter by food name, category, or ID..." value="" class="orders-search-input" aria-label="Filter menu items">
                </div>
                <select id="admin-filter-select" class="admin-filter-select" aria-label="Filter by availability or type">
                    <option value="all">All Items</option>
                    <option value="instock">In Stock Only</option>
                    <option value="outstock">Out of Stock Only</option>
                    <option value="custom">Custom Items Only</option>
                    <option value="seeded">Seeded Items Only</option>
                </select>
            </div>

            <div class="orders-count-indicator" id="admin-count-indicator">
                <span>Loading catalog items...</span>
            </div>

            <div class="cart-items" id="admin-items-container" style="display:flex; flex-direction:column; gap:12px; padding:6px 0 24px;">
                <!-- Dynamically rendered -->
            </div>
        </section>
    `;

    document.body.appendChild(overlay);
    document.body.style.overflow = "hidden";

    const closeDashboard = () => {
        overlay.remove();
        document.body.style.overflow = "";
    };

    const container = overlay.querySelector("#admin-items-container");
    const countIndicator = overlay.querySelector("#admin-count-indicator");
    const searchInput = overlay.querySelector("#admin-menu-search");
    const filterSelect = overlay.querySelector("#admin-filter-select");

    // Helper: handle session expiration gracefully
    function handleAuthError() {
        sessionStorage.removeItem("crave_admin_token");
        closeDashboard();
        showToast("Admin session expired or access unauthorized (403).");
        showAdminAuthModal();
    }

    async function loadStats() {
        try {
            const res = await fetch(`${API_BASE}/api/admin/stats`, {
                headers: { "X-Admin-Token": token }
            });
            if (res.status === 401 || res.status === 403) {
                handleAuthError();
                return;
            }
            if (!res.ok) return;
            const stats = await res.json();
            const ordersEl = overlay.querySelector("#kpi-orders");
            const revEl = overlay.querySelector("#kpi-revenue");
            const menuEl = overlay.querySelector("#kpi-menu");
            const menuSubEl = overlay.querySelector("#kpi-menu-sub");
            const stockoutEl = overlay.querySelector("#kpi-stockout");
            const stockoutSubEl = overlay.querySelector("#kpi-stockout-sub");

            if (ordersEl) ordersEl.textContent = stats.totalOrders;
            if (revEl) revEl.textContent = `₹${stats.totalRevenue}`;
            if (menuEl) menuEl.textContent = stats.totalMenuItems;
            if (menuSubEl) menuSubEl.textContent = `${stats.inStockCount} in stock • ${stats.customItemsCount} custom`;
            if (stockoutEl) {
                stockoutEl.textContent = stats.outOfStockCount;
                stockoutEl.style.color = stats.outOfStockCount > 0 ? "var(--danger)" : "var(--green)";
            }
            if (stockoutSubEl) {
                stockoutSubEl.textContent = stats.outOfStockCount > 0 ? "Items need restock" : "All items available";
            }
        } catch (e) {
            console.error("Failed to load admin stats:", e);
        }
    }

    function getFilteredAdminFoods() {
        const query = adminSearchQuery.trim().toLowerCase();
        return foods.filter(food => {
            const matchesQuery = !query ||
                food.name.toLowerCase().includes(query) ||
                food.category.toLowerCase().includes(query) ||
                String(food.id).includes(query);

            const isCustom = Number(food.id) > 9;
            const isAvail = food.available !== false;

            let matchesFilter = true;
            if (adminFilterMode === "instock") matchesFilter = isAvail;
            else if (adminFilterMode === "outstock") matchesFilter = !isAvail;
            else if (adminFilterMode === "custom") matchesFilter = isCustom;
            else if (adminFilterMode === "seeded") matchesFilter = !isCustom;

            return matchesQuery && matchesFilter;
        });
    }

    function renderMenuItems() {
        const displayFoods = getFilteredAdminFoods();

        countIndicator.innerHTML = `
            <span>Showing <b>${displayFoods.length}</b> of <b>${foods.length}</b> menu items</span>
            ${adminSearchQuery ? `<span style="color:var(--accent-light);">Filtered by "${adminSearchQuery}"</span>` : ''}
        `;

        if (displayFoods.length === 0) {
            container.innerHTML = `
                <div class="orders-empty-card">
                    <div class="orders-empty-icon">🔍</div>
                    <h3>No menu items match</h3>
                    <p>No food items match your current search "${adminSearchQuery}" or filter selection.</p>
                    <button class="add-button" id="admin-reset-filter-btn" style="padding:10px 18px; border-radius:10px; border:1px solid var(--border); color:var(--text); background:var(--surface-elevated);">
                        Clear Filters
                    </button>
                </div>
            `;
            return;
        }

        container.innerHTML = displayFoods.map((food, index) => {
            const isOutOfStock = food.available === false;
            const isCustom = Number(food.id) > 9;

            return `
                <article class="admin-card" style="animation-delay:${index * 30}ms;" data-food-id="${food.id}">
                    <!-- Card Top Row -->
                    <div style="display:flex; gap:14px; align-items:center;">
                        <img src="${food.image}" alt="${food.name}" onerror="this.onerror=null; this.src='https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=600&q=75';" style="width:52px; height:52px; border-radius:12px; object-fit:cover; border:1px solid var(--border); background:#14141E; flex-shrink:0;">
                        <div style="flex:1; min-width:0;">
                            <div style="display:flex; justify-content:space-between; align-items:center; gap:8px;">
                                <strong style="font-size:14px; color:var(--text); overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">${food.name}</strong>
                                <span class="${isOutOfStock ? 'badge-stock-out' : 'badge-stock-in'}">
                                    ${isOutOfStock ? 'OUT OF STOCK' : 'IN STOCK'}
                                </span>
                            </div>
                            <div style="display:flex; gap:8px; align-items:center; margin-top:3px; flex-wrap:wrap;">
                                <span style="font-size:11.5px; color:var(--muted);">${food.category} • ID #${food.id}</span>
                                ${isCustom
                                    ? '<span class="badge-custom-food">Custom Food</span>'
                                    : '<span class="badge-seeded-food">Seeded Food</span>'}
                            </div>
                        </div>
                    </div>

                    <!-- Card Actions Strip -->
                    <div style="display:flex; gap:10px; align-items:center; justify-content:space-between; flex-wrap:wrap; background:#0E0E15; padding:10px 14px; border-radius:12px; border:1px solid var(--border-subtle);">
                        <!-- Price Display & Edit Button -->
                        <div style="display:flex; align-items:center; gap:10px;">
                            <div style="display:flex; align-items:baseline; gap:4px;">
                                <span style="font-size:11px; color:var(--muted);">Price:</span>
                                <strong style="font-size:16px; color:var(--accent-light); font-family:'Outfit',sans-serif;">₹${food.price}</strong>
                            </div>
                            <button class="btn-pill btn-pill-dark edit-price-btn" data-id="${food.id}" aria-label="Edit price for ${food.name}">
                                <span>✏️ Edit Price</span>
                            </button>
                        </div>

                        <!-- Availability & Delete Controls -->
                        <div style="display:flex; gap:8px; align-items:center;">
                            <button class="stock-toggle-pill ${isOutOfStock ? 'out-of-stock' : 'in-stock'} toggle-stock-btn" data-id="${food.id}" data-current-avail="${!isOutOfStock}" aria-label="Toggle availability for ${food.name}">
                                <span>${isOutOfStock ? '✕ Out of Stock' : '✓ In Stock'}</span>
                            </button>
                            ${isCustom ? `
                                <button class="btn-pill btn-pill-danger delete-item-btn" data-id="${food.id}" data-name="${food.name}" aria-label="Delete custom food ${food.name}">
                                    <span>🗑 Delete</span>
                                </button>
                            ` : `
                                <button class="btn-pill btn-pill-dark delete-item-btn" data-id="${food.id}" data-name="${food.name}" title="Reset price and stock overrides back to default seeded values" aria-label="Reset overrides for ${food.name}">
                                    <span>↺ Reset</span>
                                </button>
                            `}
                        </div>
                    </div>
                </article>
            `;
        }).join("");
    }

    searchInput.addEventListener("input", e => {
        adminSearchQuery = e.target.value;
        renderMenuItems();
    });

    filterSelect.addEventListener("change", e => {
        adminFilterMode = e.target.value;
        renderMenuItems();
    });

    overlay.addEventListener("click", async event => {
        if (event.target === overlay || event.target.closest("[data-close-drawer]")) {
            closeDashboard();
            return;
        }

        if (event.target.closest("#admin-logout-btn")) {
            sessionStorage.removeItem("crave_admin_token");
            closeDashboard();
            showToast("Admin session locked.");
            return;
        }

        if (event.target.closest("#admin-add-food-btn")) {
            showAddFoodModal(token, async () => {
                await loadFoods();
                await loadStats();
                renderMenuItems();
            });
            return;
        }

        if (event.target.closest("#admin-reset-filter-btn")) {
            searchInput.value = "";
            adminSearchQuery = "";
            filterSelect.value = "all";
            adminFilterMode = "all";
            renderMenuItems();
            return;
        }

        // Edit Price Modal Trigger
        const editPriceBtn = event.target.closest(".edit-price-btn");
        if (editPriceBtn) {
            const foodId = Number(editPriceBtn.dataset.id);
            const food = foods.find(f => f.id === foodId);
            if (food) {
                showEditPriceModal(token, food, async () => {
                    await loadFoods();
                    await loadStats();
                    renderMenuItems();
                });
            }
            return;
        }

        // Toggle Stock Action
        const toggleBtn = event.target.closest(".toggle-stock-btn");
        if (toggleBtn) {
            if (toggleBtn.disabled) return;
            const foodId = toggleBtn.dataset.id;
            const currentAvail = toggleBtn.dataset.currentAvail === "true";
            const targetAvail = !currentAvail;

            toggleBtn.disabled = true;
            toggleBtn.innerHTML = `<span class="button-spinner"></span>`;

            try {
                const response = await fetch(`${API_BASE}/api/admin/menu/items/${foodId}/availability`, {
                    method: "PUT",
                    headers: {
                        "Content-Type": "application/json",
                        "X-Admin-Token": token
                    },
                    body: JSON.stringify({ available: targetAvail })
                });

                if (response.status === 401 || response.status === 403) {
                    handleAuthError();
                    return;
                }

                if (!response.ok) {
                    const err = await response.json().catch(() => ({}));
                    throw new Error(err.message || "Failed to update availability");
                }

                showToast(`Item is now ${targetAvail ? 'IN STOCK' : 'OUT OF STOCK'}`);
                await loadFoods();
                await loadStats();
                renderMenuItems();
            } catch (err) {
                showToast(`Error: ${err.message}`);
                toggleBtn.disabled = false;
                toggleBtn.innerHTML = `<span>${currentAvail ? '✓ In Stock' : '✕ Out of Stock'}</span>`;
            }
            return;
        }

        // Delete / Reset Overrides Action
        const deleteBtn = event.target.closest(".delete-item-btn");
        if (deleteBtn) {
            const foodId = deleteBtn.dataset.id;
            const foodName = deleteBtn.dataset.name;
            const isCustom = Number(foodId) > 9;

            const dialogTitle = isCustom ? "Delete Custom Food Item" : "Reset Menu Overrides";
            const dialogMsg = isCustom
                ? `Are you sure you want to permanently delete custom food item "${foodName}" (ID #${foodId}) from data/menu-overrides.json?`
                : `Are you sure you want to reset price and availability overrides for "${foodName}" (ID #${foodId}) back to original defaults?`;
            const confirmBtnText = isCustom ? "Delete Food Item" : "Reset to Defaults";

            showAdminConfirmDialog(dialogTitle, dialogMsg, confirmBtnText, isCustom, async () => {
                try {
                    const response = await fetch(`${API_BASE}/api/admin/menu/items/${foodId}`, {
                        method: "DELETE",
                        headers: { "X-Admin-Token": token }
                    });

                    if (response.status === 401 || response.status === 403) {
                        handleAuthError();
                        return;
                    }

                    if (!response.ok) {
                        const err = await response.json().catch(() => ({}));
                        throw new Error(err.message || "Failed to remove food item");
                    }

                    showToast(isCustom ? `Deleted "${foodName}" from menu.` : `Reset overrides for "${foodName}".`);
                    await loadFoods();
                    await loadStats();
                    renderMenuItems();
                } catch (err) {
                    showToast(`Error: ${err.message}`);
                }
            });
            return;
        }
    });

    const escapeHandler = event => {
        if (event.key === "Escape") {
            closeDashboard();
            document.removeEventListener("keydown", escapeHandler);
        }
    };
    document.addEventListener("keydown", escapeHandler);

    // Initial load: fetch stats & render items
    loadStats();
    renderMenuItems();
}

/**
 * Polished modal dialog for editing a food item price with adjustment chips and validation.
 */
function showEditPriceModal(token, food, onDone) {
    const dialog = document.createElement("div");
    dialog.className = "admin-dialog-overlay";

    dialog.innerHTML = `
        <div class="admin-dialog-content" role="dialog" aria-modal="true" aria-labelledby="edit-price-title">
            <div class="admin-dialog-header">
                <div>
                    <span class="section-kicker" style="color:var(--accent);">PRICE OVERRIDE</span>
                    <h3 id="edit-price-title">Edit Price: ${food.name}</h3>
                </div>
                <button class="cart-close" data-close-modal aria-label="Close">×</button>
            </div>

            <div style="display:flex; align-items:center; gap:12px; padding:8px 0;">
                <img src="${food.image}" alt="${food.name}" onerror="this.onerror=null; this.src='https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=600&q=75';" style="width:48px; height:48px; border-radius:10px; object-fit:cover; border:1px solid var(--border);">
                <div>
                    <strong style="color:var(--text); font-size:13.5px;">${food.name}</strong>
                    <div style="font-size:11.5px; color:var(--muted);">Current Price: <b style="color:var(--text);">₹${food.price}</b></div>
                </div>
            </div>

            <form id="edit-price-form" style="display:grid; gap:12px;">
                <div class="admin-form-group">
                    <label for="new-price-input">New Price (₹) *</label>
                    <input type="number" id="new-price-input" min="1" required value="${food.price}" class="admin-input-control" style="font-size:16px; font-weight:700;">
                </div>

                <!-- Price Increment Adjustment Chips -->
                <div style="display:flex; gap:6px; flex-wrap:wrap;">
                    <button type="button" class="btn-pill btn-pill-dark price-chip" data-adj="-10">-₹10</button>
                    <button type="button" class="btn-pill btn-pill-dark price-chip" data-adj="+10">+₹10</button>
                    <button type="button" class="btn-pill btn-pill-dark price-chip" data-adj="+20">+₹20</button>
                    <button type="button" class="btn-pill btn-pill-dark price-chip" data-adj="+50">+₹50</button>
                </div>

                <div id="edit-price-error" style="color:var(--danger); font-size:11.5px; display:none;"></div>

                <div class="admin-dialog-footer">
                    <button type="button" class="btn-pill btn-pill-dark" data-close-modal style="padding:10px 18px;">Cancel</button>
                    <button type="submit" id="save-price-submit-btn" class="checkout-button" style="padding:10px 20px; font-size:13px; border-radius:10px;">
                        <span>Save Price ✓</span>
                    </button>
                </div>
            </form>
        </div>
    `;

    document.body.appendChild(dialog);

    const closeDialog = () => dialog.remove();

    dialog.addEventListener("click", e => {
        if (e.target === dialog || e.target.closest("[data-close-modal]")) {
            closeDialog();
        }

        const chip = e.target.closest(".price-chip");
        if (chip) {
            const adj = parseInt(chip.dataset.adj, 10);
            const input = dialog.querySelector("#new-price-input");
            const curVal = parseInt(input.value, 10) || food.price;
            input.value = Math.max(1, curVal + adj);
        }
    });

    const form = dialog.querySelector("#edit-price-form");
    const saveBtn = dialog.querySelector("#save-price-submit-btn");
    const errBox = dialog.querySelector("#edit-price-error");

    form.addEventListener("submit", async e => {
        e.preventDefault();
        const priceVal = parseInt(dialog.querySelector("#new-price-input").value, 10);

        if (!priceVal || priceVal <= 0) {
            errBox.textContent = "Please enter a valid price greater than ₹0.";
            errBox.style.display = "block";
            return;
        }

        saveBtn.disabled = true;
        const originalHtml = saveBtn.innerHTML;
        saveBtn.innerHTML = `<span class="button-spinner"></span> Saving...`;

        try {
            const response = await fetch(`${API_BASE}/api/admin/menu/items/${food.id}/price`, {
                method: "PUT",
                headers: {
                    "Content-Type": "application/json",
                    "X-Admin-Token": token
                },
                body: JSON.stringify({ price: priceVal })
            });

            if (!response.ok) {
                const err = await response.json().catch(() => ({}));
                throw new Error(err.message || "Failed to update price");
            }

            showToast(`Updated "${food.name}" price to ₹${priceVal}!`);
            closeDialog();
            if (onDone) await onDone();
        } catch (err) {
            saveBtn.disabled = false;
            saveBtn.innerHTML = originalHtml;
            errBox.textContent = `Error: ${err.message}`;
            errBox.style.display = "block";
        }
    });
}

/**
 * Polished modal dialog for adding a new custom food item.
 */
function showAddFoodModal(token, onDone) {
    const dialog = document.createElement("div");
    dialog.className = "admin-dialog-overlay";

    dialog.innerHTML = `
        <div class="admin-dialog-content" role="dialog" aria-modal="true" aria-labelledby="add-food-title" style="max-width:540px;">
            <div class="admin-dialog-header">
                <div>
                    <span class="section-kicker" style="color:var(--accent);">MENU EXPANSION</span>
                    <h3 id="add-food-title">Add New Food Item</h3>
                </div>
                <button class="cart-close" data-close-modal aria-label="Close">×</button>
            </div>

            <form id="add-food-modal-form" style="display:grid; gap:12px; max-height:70vh; overflow-y:auto; padding-right:4px;">
                <div class="admin-form-group">
                    <label for="modal-food-name">Food Item Name *</label>
                    <input type="text" id="modal-food-name" required placeholder="e.g. Crispy Truffle Burger" class="admin-input-control">
                </div>

                <div style="display:grid; grid-template-columns:1fr 1fr; gap:10px;">
                    <div class="admin-form-group">
                        <label for="modal-food-category">Category *</label>
                        <select id="modal-food-category" required class="admin-input-control" style="cursor:pointer;">
                            <option value="Burgers">Burgers</option>
                            <option value="Pizza">Pizza</option>
                            <option value="Pasta">Pasta</option>
                            <option value="Snacks">Snacks</option>
                            <option value="Drinks">Drinks</option>
                            <option value="Desserts">Desserts</option>
                            <option value="Specials">Specials</option>
                        </select>
                    </div>

                    <div class="admin-form-group">
                        <label for="modal-food-price">Price (₹) *</label>
                        <input type="number" id="modal-food-price" min="1" required placeholder="e.g. 249" class="admin-input-control">
                    </div>
                </div>

                <div class="admin-form-group">
                    <label for="modal-food-desc">Description</label>
                    <textarea id="modal-food-desc" rows="2" placeholder="e.g. Handcrafted patty, melted smoked cheese, and secret truffle dressing." class="admin-input-control" style="resize:vertical;"></textarea>
                </div>

                <div style="display:grid; grid-template-columns:1fr 1fr; gap:10px;">
                    <div class="admin-form-group">
                        <label for="modal-food-badge">Badge (Optional)</label>
                        <input type="text" id="modal-food-badge" placeholder="e.g. CHEF'S SPECIAL" value="NEW" class="admin-input-control">
                    </div>
                    <div class="admin-form-group">
                        <label for="modal-food-rating">Rating (Optional)</label>
                        <input type="text" id="modal-food-rating" placeholder="e.g. 4.9" value="4.9" class="admin-input-control">
                    </div>
                </div>

                <div class="admin-form-group">
                    <label for="modal-food-image">Image URL</label>
                    <input type="url" id="modal-food-image" placeholder="https://images.unsplash.com/..." value="https://images.unsplash.com/photo-1568901346375-23c9450c58cd?auto=format&fit=crop&w=600&q=75" class="admin-input-control">
                </div>

                <div style="display:flex; align-items:center; gap:8px;">
                    <input type="checkbox" id="modal-food-avail" checked style="cursor:pointer; width:16px; height:16px; accent-color:var(--accent);">
                    <label for="modal-food-avail" style="font-size:12px; color:var(--text); cursor:pointer;">Available in stock immediately</label>
                </div>

                <div id="add-food-modal-error" style="color:var(--danger); font-size:11.5px; background:var(--danger-bg); border:1px solid var(--danger-border); border-radius:8px; padding:8px 12px; display:none;"></div>

                <div class="admin-dialog-footer">
                    <button type="button" class="btn-pill btn-pill-dark" data-close-modal style="padding:10px 18px;">Cancel</button>
                    <button type="submit" id="add-food-submit-btn" class="checkout-button" style="padding:10px 20px; font-size:13px; border-radius:10px;">
                        <span>Save to Menu ✓</span>
                    </button>
                </div>
            </form>
        </div>
    `;

    document.body.appendChild(dialog);

    const closeDialog = () => dialog.remove();

    dialog.addEventListener("click", e => {
        if (e.target === dialog || e.target.closest("[data-close-modal]")) {
            closeDialog();
        }
    });

    const form = dialog.querySelector("#add-food-modal-form");
    const submitBtn = dialog.querySelector("#add-food-submit-btn");
    const errBox = dialog.querySelector("#add-food-modal-error");

    form.addEventListener("submit", async e => {
        e.preventDefault();
        const name = document.getElementById("modal-food-name").value.trim();
        const category = document.getElementById("modal-food-category").value;
        const price = parseInt(document.getElementById("modal-food-price").value, 10);
        const description = document.getElementById("modal-food-desc").value.trim();
        const badge = document.getElementById("modal-food-badge").value.trim() || "NEW";
        const rating = document.getElementById("modal-food-rating").value.trim() || "4.9";
        const image = document.getElementById("modal-food-image").value.trim();
        const available = document.getElementById("modal-food-avail").checked;

        if (!name) {
            errBox.textContent = "Food item name is required.";
            errBox.style.display = "block";
            return;
        }

        if (!price || price <= 0) {
            errBox.textContent = "Price must be greater than ₹0.";
            errBox.style.display = "block";
            return;
        }

        submitBtn.disabled = true;
        const originalHtml = submitBtn.innerHTML;
        submitBtn.innerHTML = `<span class="button-spinner"></span> Adding food item...`;

        try {
            const response = await fetch(`${API_BASE}/api/admin/menu/items`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "X-Admin-Token": token
                },
                body: JSON.stringify({
                    name, category, price, description, badge, rating, image, available
                })
            });

            if (!response.ok) {
                const err = await response.json().catch(() => ({}));
                throw new Error(err.message || "Failed to add food item");
            }

            showToast(`Added "${name}" to menu!`);
            closeDialog();
            if (onDone) await onDone();
        } catch (err) {
            submitBtn.disabled = false;
            submitBtn.innerHTML = originalHtml;
            errBox.textContent = `Error: ${err.message}`;
            errBox.style.display = "block";
        }
    });
}

/**
 * In-app confirmation dialog replacing generic browser alert/confirm.
 */
function showAdminConfirmDialog(title, message, confirmBtnText, isDanger, onConfirm) {
    const dialog = document.createElement("div");
    dialog.className = "admin-dialog-overlay";

    dialog.innerHTML = `
        <div class="admin-dialog-content" role="dialog" aria-modal="true" aria-labelledby="confirm-dialog-title" style="max-width:440px;">
            <div class="admin-dialog-header">
                <div>
                    <span class="section-kicker" style="color:${isDanger ? 'var(--danger)' : 'var(--accent)'};">CONFIRM ACTION</span>
                    <h3 id="confirm-dialog-title">${title}</h3>
                </div>
                <button class="cart-close" data-close-modal aria-label="Close">×</button>
            </div>

            <p style="font-size:12.5px; color:var(--muted); line-height:1.5;">${message}</p>

            <div class="admin-dialog-footer">
                <button type="button" class="btn-pill btn-pill-dark" data-close-modal style="padding:10px 18px;">Cancel</button>
                <button type="button" id="confirm-dialog-ok-btn" class="btn-pill ${isDanger ? 'btn-pill-danger' : 'btn-pill-orange'}" style="padding:10px 20px; font-weight:700;">
                    ${confirmBtnText}
                </button>
            </div>
        </div>
    `;

    document.body.appendChild(dialog);

    const closeDialog = () => dialog.remove();

    dialog.addEventListener("click", e => {
        if (e.target === dialog || e.target.closest("[data-close-modal]")) {
            closeDialog();
            return;
        }

        if (e.target.closest("#confirm-dialog-ok-btn")) {
            closeDialog();
            if (onConfirm) onConfirm();
        }
    });
}

function navigateTo(page) {
    const pageNames = {
        home: "Discover",
        menu: "Explore menu",
        orders: "My orders",
        cart: "My cart",
        profile: "My profile",
        admin: "Admin portal"
    };

    if (page === "cart") {
        showCart();
        return;
    }

    if (page === "orders") {
        showOrders();
        return;
    }

    if (page === "profile") {
        showProfile();
        return;
    }

    if (page === "admin") {
        showAdminPortal();
        return;
    }

    document.querySelectorAll(".nav-item").forEach(button => {
        button.classList.toggle("active", button.dataset.page === page);
    });

    document.getElementById("breadcrumb-current").textContent =
        pageNames[page] || "Discover";

    if (page === "menu") {
        document.getElementById("food-section").scrollIntoView({
            behavior: "smooth",
            block: "start"
        });
    } else {
        window.scrollTo({ top: 0, behavior: "smooth" });
    }
}

document.getElementById("category-list").addEventListener("click", event => {
    const button = event.target.closest("[data-category]");
    if (button) selectCategory(button.dataset.category);
});

foodGrid.addEventListener("click", event => {
    const button = event.target.closest("[data-add]");
    if (button) addToCart(Number(button.dataset.add), button);
});

searchInput.addEventListener("input", renderFoods);

const sortSelect = document.getElementById("food-sort");
if (sortSelect) {
    sortSelect.addEventListener("change", renderFoods);
}

// Hero search bar handler
const heroSearchInput = document.getElementById("hero-search-input");
const heroSearchBtn = document.getElementById("hero-search-btn");
if (heroSearchInput) {
    const runHeroSearch = () => {
        const val = heroSearchInput.value.trim();
        if (searchInput) {
            searchInput.value = val;
            renderFoods();
            document.getElementById("food-section").scrollIntoView({
                behavior: "smooth",
                block: "start"
            });
        }
    };
    if (heroSearchBtn) heroSearchBtn.addEventListener("click", runHeroSearch);
    heroSearchInput.addEventListener("keydown", e => {
        if (e.key === "Enter") {
            e.preventDefault();
            runHeroSearch();
        }
    });
}

// Quick category shortcuts in hero banner
document.querySelectorAll("[data-quick-cat]").forEach(chip => {
    chip.addEventListener("click", () => {
        const cat = chip.dataset.quickCat;
        selectCategory(cat);
        document.getElementById("food-section").scrollIntoView({
            behavior: "smooth",
            block: "start"
        });
    });
});

// Clear button in menu search box
const foodSearchClear = document.getElementById("food-search-clear");
if (foodSearchClear) {
    foodSearchClear.addEventListener("click", () => {
        if (searchInput) {
            searchInput.value = "";
            renderFoods();
            searchInput.focus();
        }
    });
}

document.querySelectorAll(".nav-item").forEach(button => {
    button.addEventListener("click", () => navigateTo(button.dataset.page));
});

document.getElementById("header-cart").addEventListener("click", showCart);
document.getElementById("offer-explore").addEventListener("click", () => navigateTo("menu"));
document.getElementById("hero-explore").addEventListener("click", () => navigateTo("menu"));

document.getElementById("view-all").addEventListener("click", () => {
    searchInput.value = "";
    if (sortSelect) sortSelect.value = "default";
    selectCategory("All");
});

document.getElementById("reset-filters").addEventListener("click", () => {
    searchInput.value = "";
    if (sortSelect) sortSelect.value = "default";
    selectCategory("All");
});

document.addEventListener("keydown", event => {
    if (
        event.key === "/" &&
        !["INPUT", "TEXTAREA"].includes(document.activeElement.tagName)
    ) {
        event.preventDefault();
        searchInput.focus();
    }
});

// Location state and handler
let currentLocation = localStorage.getItem("crave_location") || "Choose your location";
function setLocation(loc) {
    currentLocation = loc;
    localStorage.setItem("crave_location", loc);
    const locElem = document.querySelector(".location-pill strong");
    if (locElem) locElem.textContent = loc;
    showToast(`Delivery location set to: ${loc}`);
}

const locationPill = document.querySelector(".location-pill");
if (locationPill) {
    locationPill.style.cursor = "pointer";
    locationPill.addEventListener("click", showLocationSelector);
}

// User Profile state and handler
let currentUser = JSON.parse(localStorage.getItem("crave_user") || '{"name":"Guest User","role":"Food enthusiast","loggedIn":false}');

function setUser(user) {
    currentUser = user;
    localStorage.setItem("crave_user", JSON.stringify(user));

    const avatar = document.getElementById("sidebar-user-avatar") || document.querySelector(".user-profile .avatar");
    const nameElem = document.getElementById("sidebar-user-name") || document.querySelector(".user-profile .user-info strong");
    const emailElem = document.getElementById("sidebar-user-email") || document.querySelector(".user-profile .user-info span");

    if (avatar) {
        if (user.pictureUrl) {
            avatar.innerHTML = `<img src="${escapeHtml(user.pictureUrl)}" alt="${escapeHtml(user.name || 'User')}" style="width:100%;height:100%;border-radius:50%;object-fit:cover;">`;
        } else {
            avatar.textContent = user.name ? user.name.charAt(0).toUpperCase() : "C";
        }
    }
    if (nameElem) nameElem.textContent = user.name || "Crave Member";
    if (emailElem) emailElem.textContent = user.email || (user.loggedIn ? "Logged in" : "Food enthusiast");
}

const userProfileCard = document.querySelector(".user-profile");
if (userProfileCard) {
    userProfileCard.style.cursor = "pointer";
    userProfileCard.addEventListener("click", showProfile);
}

// Sidebar quick logout button
const sidebarLogoutBtn = document.getElementById("sidebar-logout-btn");
if (sidebarLogoutBtn) {
    sidebarLogoutBtn.addEventListener("click", (e) => {
        e.stopPropagation();
        logoutUser();
    });
}

// Restore saved location if present
const savedLoc = localStorage.getItem("crave_location");
if (savedLoc) {
    const locElem = document.querySelector(".location-pill strong");
    if (locElem) locElem.textContent = savedLoc;
}

// ============================================================================
// AUTHENTICATION-FIRST PLATFORM & GOOGLE SIGN-IN INTEGRATION
// ============================================================================

let googleClientId = "755498069210-a53fiibrvm5gd8vkvkdjv88re3ae5prj.apps.googleusercontent.com";

function showAuthScreen() {
    const authScreen = document.getElementById("crave-auth-screen");
    const appShell = document.getElementById("app-shell");
    if (authScreen) authScreen.classList.remove("hidden");
    if (appShell) appShell.classList.add("hidden");
}

function hideAuthScreen() {
    const authScreen = document.getElementById("crave-auth-screen");
    const appShell = document.getElementById("app-shell");
    if (authScreen) authScreen.classList.add("hidden");
    if (appShell) appShell.classList.remove("hidden");
}

function showAuthError(panel, msg) {
    const banner = document.getElementById(`auth-${panel}-error`);
    if (banner) {
        banner.textContent = msg;
        banner.classList.remove("hidden");
    }
}

function hideAuthError(panel) {
    const banner = document.getElementById(`auth-${panel}-error`);
    if (banner) {
        banner.textContent = "";
        banner.classList.add("hidden");
    }
}

function setAuthButtonLoading(btnId, isLoading, defaultText) {
    const btn = document.getElementById(btnId);
    if (!btn) return;
    btn.disabled = isLoading;
    const btnText = btn.querySelector(".btn-text");
    const spinner = btn.querySelector(".btn-spinner");
    const arrow = btn.querySelector(".btn-arrow");
    if (btnText) btnText.textContent = isLoading ? "Please wait..." : defaultText;
    if (spinner) spinner.classList.toggle("hidden", !isLoading);
    if (arrow) arrow.classList.toggle("hidden", isLoading);
}

/**
 * Handle successful authentication (Local or Google)
 */
function onAuthSuccess(user, showToastFlag = true) {
    currentUser = {
        id: user.id,
        name: user.fullName || user.name || "Crave Member",
        email: user.email,
        role: user.role || "CUSTOMER",
        authProvider: user.authProvider || "LOCAL",
        pictureUrl: user.pictureUrl,
        loggedIn: true
    };
    setUser(currentUser);
    hideAuthScreen();

    // Load dishes and cart if not already loaded
    if (foods.length === 0) {
        loadFoods();
    }
    updateCart();

    if (showToastFlag) {
        showToast(`Welcome to Crave, ${currentUser.name}! 🍽️`);
    }
}

/**
 * Perform server-side session check on page load.
 */
async function checkSession() {
    try {
        const response = await fetch(`${API_BASE}/api/auth/me`, {
            credentials: "same-origin"
        });
        if (response.ok) {
            const data = await response.json();
            if (data && data.authenticated && data.user) {
                onAuthSuccess(data.user, false);
                return true;
            }
        }
    } catch (err) {
        console.warn("Session check request failed:", err);
    }

    // User is unauthenticated: display Crave auth landing screen
    showAuthScreen();
    initGoogleAuth();
    return false;
}

/**
 * Fetch Google Client ID and initialize Google Identity Services
 */
async function initGoogleAuth() {
    try {
        const res = await fetch(`${API_BASE}/api/auth/config`);
        if (res.ok) {
            const config = await res.json();
            if (config && config.googleClientId) {
                googleClientId = config.googleClientId;
            }
        }
    } catch (e) {
        console.warn("Could not retrieve auth config:", e);
    }
    renderGoogleButton();
}

/**
 * Render the official Google Sign-In button
 */
function renderGoogleButton() {
    const target = document.getElementById("g_id_signin_btn");
    if (!target) return;

    if (typeof google !== "undefined" && google.accounts && google.accounts.id) {
        try {
            google.accounts.id.initialize({
                client_id: googleClientId,
                callback: handleGoogleCredentialResponse,
                auto_select: false,
                cancel_on_tap_outside: true
            });
            google.accounts.id.renderButton(target, {
                type: "standard",
                theme: "filled_black",
                size: "large",
                text: "signin_with",
                shape: "pill",
                width: target.offsetWidth || 340
            });
        } catch (err) {
            console.warn("Failed rendering Google Sign-In button:", err);
        }
    } else {
        // Polling retry while Google script loads
        let attempts = 0;
        const interval = setInterval(() => {
            attempts++;
            if (typeof google !== "undefined" && google.accounts && google.accounts.id) {
                clearInterval(interval);
                renderGoogleButton();
            } else if (attempts >= 10) {
                clearInterval(interval);
            }
        }, 400);
    }
}

/**
 * Google Credential Callback: sends ID token to backend /api/auth/google
 */
async function handleGoogleCredentialResponse(response) {
    if (!response || !response.credential) {
        showAuthError("login", "Google Sign-In was cancelled or failed to provide an ID token.");
        return;
    }
    try {
        setAuthButtonLoading("login-submit-btn", true, "Verifying Google Token...");
        hideAuthError("login");

        const res = await fetch(`${API_BASE}/api/auth/google`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            credentials: "same-origin",
            body: JSON.stringify({ credential: response.credential })
        });
        const data = await res.json();
        if (!res.ok) {
            throw new Error(data.message || data.error || "Google authentication failed");
        }
        onAuthSuccess(data, true);
    } catch (err) {
        showAuthError("login", err.message || "Google authentication failed");
    } finally {
        setAuthButtonLoading("login-submit-btn", false, "Log In to Crave");
    }
}

/**
 * Email/Password Login
 */
async function handleLocalLogin(e) {
    e.preventDefault();
    const email = (document.getElementById("login-email").value || "").trim();
    const password = document.getElementById("login-password").value || "";

    if (!email || !password) {
        showAuthError("login", "Please enter both email and password.");
        return;
    }

    try {
        setAuthButtonLoading("login-submit-btn", true, "Signing in...");
        hideAuthError("login");

        const res = await fetch(`${API_BASE}/api/auth/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            credentials: "same-origin",
            body: JSON.stringify({ email, password })
        });
        const data = await res.json();
        if (!res.ok) {
            throw new Error(data.message || data.error || "Invalid email or password.");
        }
        onAuthSuccess(data, true);
    } catch (err) {
        showAuthError("login", err.message || "Login failed");
    } finally {
        setAuthButtonLoading("login-submit-btn", false, "Log In to Crave");
    }
}

/**
 * Email/Password Account Registration
 */
async function handleLocalSignup(e) {
    e.preventDefault();
    const fullName = (document.getElementById("signup-fullname").value || "").trim();
    const email = (document.getElementById("signup-email").value || "").trim();
    const password = document.getElementById("signup-password").value || "";
    const confirmPassword = document.getElementById("signup-confirm-password").value || "";

    if (!fullName || !email || !password) {
        showAuthError("signup", "Please complete all required fields.");
        return;
    }
    if (password.length < 6) {
        showAuthError("signup", "Password must be at least 6 characters.");
        return;
    }
    if (password !== confirmPassword) {
        showAuthError("signup", "Passwords do not match. Please verify.");
        return;
    }

    try {
        setAuthButtonLoading("signup-submit-btn", true, "Creating Account...");
        hideAuthError("signup");

        const res = await fetch(`${API_BASE}/api/auth/register`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            credentials: "same-origin",
            body: JSON.stringify({ fullName, email, password, confirmPassword })
        });
        const data = await res.json();
        if (!res.ok) {
            throw new Error(data.message || data.error || "Registration failed.");
        }
        onAuthSuccess(data, true);
    } catch (err) {
        showAuthError("signup", err.message || "Registration failed.");
    } finally {
        setAuthButtonLoading("signup-submit-btn", false, "Create Crave Account");
    }
}

/**
 * Sign Out: Invalidate server session and return to Auth screen
 */
async function logoutUser() {
    try {
        await fetch(`${API_BASE}/api/auth/logout`, {
            method: "POST",
            credentials: "same-origin"
        });
    } catch (e) {
        console.warn("Logout request failed:", e);
    }
    currentUser = { name: "Guest User", role: "Food enthusiast", loggedIn: false };
    localStorage.removeItem("crave_user");
    setUser(currentUser);
    cart.clear();
    updateCart();
    showAuthScreen();
    renderGoogleButton();
    showToast("Signed out successfully.");
}

/**
 * Password Visibility Toggles
 */
function setupPasswordToggles() {
    document.querySelectorAll(".auth-toggle-pwd").forEach(btn => {
        btn.addEventListener("click", () => {
            const targetId = btn.dataset.target;
            const input = document.getElementById(targetId);
            if (input) {
                const isPassword = input.type === "password";
                input.type = isPassword ? "text" : "password";
                btn.textContent = isPassword ? "🙈" : "👁";
            }
        });
    });
}

/**
 * Password Strength Indicator for Registration
 */
function setupPasswordStrength() {
    const pwdInput = document.getElementById("signup-password");
    const fill = document.getElementById("pwd-strength-fill");
    const label = document.getElementById("pwd-strength-label");
    if (!pwdInput || !fill || !label) return;

    pwdInput.addEventListener("input", () => {
        const val = pwdInput.value;
        if (!val) {
            fill.style.width = "0%";
            label.textContent = "Enter at least 6 characters";
            label.style.color = "var(--muted)";
            return;
        }
        let score = 0;
        if (val.length >= 6) score++;
        if (val.length >= 10) score++;
        if (/[A-Z]/.test(val) && /[a-z]/.test(val)) score++;
        if (/\d/.test(val)) score++;
        if (/[^A-Za-z0-9]/.test(val)) score++;

        if (val.length < 6) {
            fill.style.width = "20%";
            fill.style.backgroundColor = "var(--danger)";
            label.textContent = "Too short (min 6 characters)";
            label.style.color = "var(--danger)";
        } else if (score <= 2) {
            fill.style.width = "40%";
            fill.style.backgroundColor = "#FFA500";
            label.textContent = "Fair password";
            label.style.color = "#FFA500";
        } else if (score <= 4) {
            fill.style.width = "75%";
            fill.style.backgroundColor = "var(--accent)";
            label.textContent = "Good password";
            label.style.color = "var(--accent-light)";
        } else {
            fill.style.width = "100%";
            fill.style.backgroundColor = "#10B981";
            label.textContent = "Strong password ✓";
            label.style.color = "#10B981";
        }
    });
}

/**
 * Auth Tabs (Log In vs Sign Up)
 */
function setupAuthTabs() {
    const tabLogin = document.getElementById("auth-tab-login");
    const tabSignup = document.getElementById("auth-tab-signup");
    const panelLogin = document.getElementById("auth-panel-login");
    const panelSignup = document.getElementById("auth-panel-signup");

    if (tabLogin && tabSignup && panelLogin && panelSignup) {
        tabLogin.addEventListener("click", () => {
            tabLogin.classList.add("active");
            tabLogin.setAttribute("aria-selected", "true");
            tabSignup.classList.remove("active");
            tabSignup.setAttribute("aria-selected", "false");
            panelLogin.classList.remove("hidden");
            panelSignup.classList.add("hidden");
            hideAuthError("login");
        });

        tabSignup.addEventListener("click", () => {
            tabSignup.classList.add("active");
            tabSignup.setAttribute("aria-selected", "true");
            tabLogin.classList.remove("active");
            tabLogin.setAttribute("aria-selected", "false");
            panelSignup.classList.remove("hidden");
            panelLogin.classList.add("hidden");
            hideAuthError("signup");
        });
    }
}

// Bind auth forms
const loginForm = document.getElementById("auth-login-form");
if (loginForm) {
    loginForm.addEventListener("submit", handleLocalLogin);
}

const signupForm = document.getElementById("auth-signup-form");
if (signupForm) {
    signupForm.addEventListener("submit", handleLocalSignup);
}

// Initialize Auth Features
setupPasswordToggles();
setupPasswordStrength();
setupAuthTabs();

// Initial Auth Lifecycle Check
checkSession();
