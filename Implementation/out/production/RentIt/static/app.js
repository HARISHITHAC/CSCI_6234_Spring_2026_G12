const state = {
    user: null,
    chatSessionId: null,
    pendingApiCalls: 0,
    currentView: "marketplace"
};

const el = {
    authSection: document.getElementById("authSection"),
    authTabLogin: document.getElementById("authTabLogin"),
    authTabRegister: document.getElementById("authTabRegister"),
    authToRegister: document.getElementById("authToRegister"),
    authToLogin: document.getElementById("authToLogin"),
    loginPane: document.getElementById("loginPane"),
    registerPane: document.getElementById("registerPane"),
    appSection: document.getElementById("appSection"),
    appViewTabs: document.getElementById("appViewTabs"),
    hostViewTab: document.getElementById("hostViewTab"),
    viewMarketplace: document.getElementById("viewMarketplace"),
    viewBookings: document.getElementById("viewBookings"),
    viewConcierge: document.getElementById("viewConcierge"),
    viewHost: document.getElementById("viewHost"),
    hostSection: document.getElementById("hostSection"),
    loginForm: document.getElementById("loginForm"),
    registerForm: document.getElementById("registerForm"),
    logoutBtn: document.getElementById("logoutBtn"),
    refreshBtn: document.getElementById("refreshBtn"),
    userLabel: document.getElementById("userLabel"),
    userMeta: document.getElementById("userMeta"),
    searchForm: document.getElementById("searchForm"),
    listingResults: document.getElementById("listingResults"),
    myBookings: document.getElementById("myBookings"),
    createListingForm: document.getElementById("createListingForm"),
    createKind: document.getElementById("createKind"),
    propertyFields: document.getElementById("propertyFields"),
    equipmentFields: document.getElementById("equipmentFields"),
    myListings: document.getElementById("myListings"),
    pendingBookings: document.getElementById("pendingBookings"),
    startChatBtn: document.getElementById("startChatBtn"),
    chatForm: document.getElementById("chatForm"),
    chatHistory: document.getElementById("chatHistory"),
    chatQuickPrompts: document.getElementById("chatQuickPrompts"),
    chatRecommendations: document.getElementById("chatRecommendations"),
    toast: document.getElementById("toast")
};

async function api(path, options = {}) {
    state.pendingApiCalls += 1;
    const opts = { ...options };
    opts.headers = {
        "Content-Type": "application/json",
        ...(opts.headers || {})
    };
    try {
        const response = await fetch(path, opts);
        const text = await response.text();
        let body = null;
        if (text) {
            try {
                body = JSON.parse(text);
            } catch {
                body = { message: text };
            }
        }
        if (!response.ok) {
            throw new Error(body?.message || `Request failed (${response.status})`);
        }
        return body;
    } finally {
        state.pendingApiCalls = Math.max(0, state.pendingApiCalls - 1);
    }
}

function toast(message) {
    el.toast.textContent = message;
    el.toast.classList.remove("hidden");
    window.clearTimeout(window.__toastTimer);
    window.__toastTimer = window.setTimeout(() => el.toast.classList.add("hidden"), 3500);
}

function setAuthenticatedUi(user) {
    state.user = user;
    el.authSection.classList.add("hidden");
    el.appSection.classList.remove("hidden");
    el.userLabel.textContent = `Welcome, ${user.name}`;
    el.userMeta.textContent = `${user.email} | ${user.role}`;

    const hostLike = user.role === "HOST" || user.role === "ADMIN";
    el.hostViewTab.classList.toggle("hidden", !hostLike);
    if (!hostLike && state.currentView === "host") {
        setAppView("marketplace");
        return;
    }
    setAppView(state.currentView || "marketplace");
}

function setLoggedOutUi() {
    state.user = null;
    state.chatSessionId = null;
    el.appSection.classList.add("hidden");
    el.authSection.classList.remove("hidden");
    setAuthMode("login");
    setAppView("marketplace");
}

function statusClass(status) {
    if (["CONFIRMED", "COMPLETED", "APPROVED", "SUCCESS"].includes(status)) return "ok";
    if (["PENDING_APPROVAL", "REQUESTED", "INITIATED"].includes(status)) return "warn";
    return "bad";
}

function toHtml(text) {
    return String(text ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;");
}

function setAuthMode(mode) {
    const loginMode = mode !== "register";
    el.loginPane.classList.toggle("hidden", !loginMode);
    el.registerPane.classList.toggle("hidden", loginMode);
    el.authTabLogin.classList.toggle("active", loginMode);
    el.authTabRegister.classList.toggle("active", !loginMode);
}

function setAppView(view) {
    const requested = view || "marketplace";
    const hostLike = state.user && (state.user.role === "HOST" || state.user.role === "ADMIN");
    const nextView = requested === "host" && !hostLike ? "marketplace" : requested;
    state.currentView = nextView;

    const visibility = {
        marketplace: nextView === "marketplace",
        bookings: nextView === "bookings",
        concierge: nextView === "concierge",
        host: nextView === "host"
    };

    el.viewMarketplace.classList.toggle("hidden", !visibility.marketplace);
    el.viewBookings.classList.toggle("hidden", !visibility.bookings);
    el.viewConcierge.classList.toggle("hidden", !visibility.concierge);
    el.viewHost.classList.toggle("hidden", !visibility.host);

    if (el.appViewTabs) {
        el.appViewTabs.querySelectorAll(".view-tab[data-view]").forEach((button) => {
            button.classList.toggle("active", button.dataset.view === nextView);
        });
    }
}

function defaultImageForListing(listing) {
    if (listing.kind === "PROPERTY") {
        return "https://images.unsplash.com/photo-1484154218962-a197022b5858?auto=format&fit=crop&w=1200&q=80";
    }
    return "https://images.unsplash.com/photo-1485965120184-e220f721d03e?auto=format&fit=crop&w=1200&q=80";
}

async function checkAuth() {
    try {
        const me = await api("/api/auth/me");
        setAuthenticatedUi(me.user);
        await loadDashboard();
    } catch {
        setLoggedOutUi();
    }
}

async function loadDashboard() {
    await Promise.all([
        searchListings(),
        loadMyBookings(),
        loadHostDataIfNeeded()
    ]);
}

async function register(event) {
    event.preventDefault();
    const form = new FormData(el.registerForm);
    try {
        await api("/api/auth/register", {
            method: "POST",
            body: JSON.stringify({
                name: form.get("name"),
                email: form.get("email"),
                password: form.get("password"),
                role: form.get("role")
            })
        });
        toast("Registered successfully. You can login now.");
        el.registerForm.reset();
        setAuthMode("login");
    } catch (e) {
        toast(e.message);
    }
}

async function login(event) {
    event.preventDefault();
    const form = new FormData(el.loginForm);
    try {
        const result = await api("/api/auth/login", {
            method: "POST",
            body: JSON.stringify({
                email: form.get("email"),
                password: form.get("password")
            })
        });
        setAuthenticatedUi(result.user);
        toast("Login successful.");
        await loadDashboard();
    } catch (e) {
        toast(e.message);
    }
}

async function logout() {
    try {
        await api("/api/auth/logout", { method: "POST" });
    } catch {
        // ignore logout errors
    } finally {
        setLoggedOutUi();
    }
}

function buildSearchQuery() {
    const form = new FormData(el.searchForm);
    const params = new URLSearchParams();
    const fields = ["q", "location", "kind", "startDate", "endDate", "quantity", "minPrice", "maxPrice"];
    fields.forEach((field) => {
        const value = form.get(field);
        if (value !== null && String(value).trim() !== "") {
            params.set(field, String(value).trim());
        }
    });
    return params.toString();
}

function listingCard(listing, bookActionLabel = "Book") {
    const subtype = listing.kind === "PROPERTY"
        ? `Type: ${toHtml(listing.propertyType || "-")} | Guests: ${toHtml(listing.maxGuests || "-")}`
        : `Type: ${toHtml(listing.equipmentType || "-")} | Condition: ${toHtml(listing.conditionText || "-")}`;
    const imageSrc = toHtml(listing.imageUrl || defaultImageForListing(listing));
    const fallbackSrc = toHtml(defaultImageForListing(listing));
    const availabilityChip = listing.availableForDates
        ? `<span class="chip ok">Available</span>`
        : `<span class="chip bad">Unavailable</span>`;

    return `
      <article class="card">
        <img class="listing-image" src="${imageSrc}" data-fallback="${fallbackSrc}" alt="${toHtml(listing.title)}" loading="lazy" onerror="this.onerror=null;this.src=this.dataset.fallback;">
        <h4>${toHtml(listing.title)}</h4>
        <p>${toHtml(listing.description)}</p>
        <div class="listing-meta">
          <p><strong>${toHtml(listing.location)}</strong> | ${listing.kind}</p>
          <p class="listing-price">$${toHtml(listing.pricePerDay)}/day</p>
        </div>
        <p>${subtype}</p>
        <p>Qty: ${toHtml(listing.totalQuantity)}</p>
        <p>${availabilityChip} ${listing.hostApprovalRequired ? '<span class="chip warn">Host approval</span>' : '<span class="chip ok">Auto approve</span>'}</p>
        <div class="booking-actions">
            <button data-action="book" data-id="${listing.id}">${bookActionLabel}</button>
        </div>
      </article>
    `;
}

function renderListings(listings) {
    if (!listings.length) {
        el.listingResults.innerHTML = `<p class="muted">No listings found.</p>`;
        return;
    }
    el.listingResults.innerHTML = listings.map((l) => listingCard(l)).join("");
    el.listingResults.querySelectorAll("button[data-action='book']").forEach((button) => {
        button.addEventListener("click", () => createBooking(button.dataset.id));
    });
}

async function searchListings(event) {
    if (event) event.preventDefault();
    try {
        el.listingResults.innerHTML = `<p class="muted">Loading listings...</p>`;
        const qs = buildSearchQuery();
        const result = await api(`/api/listings${qs ? `?${qs}` : ""}`);
        renderListings(result.listings || []);
    } catch (e) {
        toast(e.message);
    }
}

async function createBooking(listingId) {
    const form = new FormData(el.searchForm);
    const startDate = form.get("startDate");
    const endDate = form.get("endDate");
    const quantity = Number(form.get("quantity") || 1);
    if (!startDate || !endDate) {
        toast("Select start/end dates in Search before booking.");
        return;
    }
    try {
        const response = await api("/api/bookings", {
            method: "POST",
            body: JSON.stringify({
                listingId,
                startDate,
                endDate,
                quantity
            })
        });
        toast(response.message);
        if (!response.created && response.alternatives?.length) {
            el.listingResults.innerHTML = `
              <p class="muted">Requested option unavailable. Suggested alternatives:</p>
              ${response.alternatives.map((alt) => listingCard(alt, "Book Alternative")).join("")}
            `;
            el.listingResults.querySelectorAll("button[data-action='book']").forEach((button) => {
                button.addEventListener("click", () => createBooking(button.dataset.id));
            });
        }
        await Promise.all([loadMyBookings(), loadHostDataIfNeeded(), searchListings()]);
    } catch (e) {
        toast(e.message);
    }
}

function bookingActions(booking) {
    let actions = "";
    if (booking.paymentRequired) {
        actions += `
          <div class="booking-actions">
            <select id="pay-method-${booking.id}">
              <option value="CARD">Card</option>
              <option value="WALLET">Wallet</option>
              <option value="BANK">Bank</option>
            </select>
            <button data-action="pay" data-id="${booking.id}">Pay Now</button>
          </div>
        `;
    }
    if (["PENDING_APPROVAL", "APPROVED", "CONFIRMED"].includes(booking.status)) {
        actions += `<div class="booking-actions"><button data-action="cancel-booking" data-id="${booking.id}">Cancel</button></div>`;
    }
    if (booking.status === "COMPLETED") {
        actions += `
          <div class="booking-actions">
            <input id="review-comment-${booking.id}" placeholder="Review comment" />
            <select id="review-rating-${booking.id}">
              <option value="5">5</option>
              <option value="4">4</option>
              <option value="3">3</option>
              <option value="2">2</option>
              <option value="1">1</option>
            </select>
            <button data-action="review" data-id="${booking.id}">Leave Review</button>
          </div>
        `;
    }
    return actions;
}

function renderBookings(bookings) {
    if (!bookings.length) {
        el.myBookings.innerHTML = `<p class="muted">No bookings yet.</p>`;
        return;
    }
    el.myBookings.innerHTML = bookings.map((booking) => `
      <article class="card">
        <h4>${toHtml(booking.listingTitle)}</h4>
        <p>${toHtml(booking.startDate)} -> ${toHtml(booking.endDate)} | Qty: ${toHtml(booking.quantity)}</p>
        <p>Total: $${toHtml(booking.totalPrice)}</p>
        <p><span class="chip ${statusClass(booking.status)}">${toHtml(booking.status)}</span></p>
        ${booking.rejectionReason ? `<p><strong>Rejection:</strong> ${toHtml(booking.rejectionReason)}</p>` : ""}
        ${booking.receiptNumber ? `<p><strong>Receipt:</strong> ${toHtml(booking.receiptNumber)}</p>` : ""}
        ${bookingActions(booking)}
      </article>
    `).join("");

    el.myBookings.querySelectorAll("button[data-action='pay']").forEach((button) => {
        button.addEventListener("click", () => payBooking(button.dataset.id));
    });
    el.myBookings.querySelectorAll("button[data-action='cancel-booking']").forEach((button) => {
        button.addEventListener("click", () => cancelBooking(button.dataset.id));
    });
    el.myBookings.querySelectorAll("button[data-action='review']").forEach((button) => {
        button.addEventListener("click", () => leaveReview(button.dataset.id));
    });
}

async function loadMyBookings() {
    if (!state.user) return;
    try {
        el.myBookings.innerHTML = `<p class="muted">Loading bookings...</p>`;
        const bookings = await api("/api/bookings/me");
        renderBookings(bookings || []);
    } catch (e) {
        toast(e.message);
    }
}

async function payBooking(bookingId) {
    const methodInput = document.getElementById(`pay-method-${bookingId}`);
    const method = methodInput ? methodInput.value : "CARD";
    try {
        const result = await api("/api/payments/pay", {
            method: "POST",
            body: JSON.stringify({
                bookingId,
                method
            })
        });
        if (result.status === "SUCCESS") {
            toast(`Payment successful. Receipt: ${result.receiptNumber}`);
        } else {
            toast(`Payment failed with status: ${result.status}`);
        }
        await loadMyBookings();
    } catch (e) {
        toast(e.message);
    }
}

async function cancelBooking(bookingId) {
    try {
        await api(`/api/bookings/${bookingId}/cancel`, { method: "POST" });
        toast("Booking cancelled.");
        await Promise.all([loadMyBookings(), loadHostDataIfNeeded(), searchListings()]);
    } catch (e) {
        toast(e.message);
    }
}

async function leaveReview(bookingId) {
    const comment = document.getElementById(`review-comment-${bookingId}`)?.value?.trim() || "";
    const rating = Number(document.getElementById(`review-rating-${bookingId}`)?.value || "5");
    if (!comment) {
        toast("Write a review comment first.");
        return;
    }
    try {
        await api("/api/reviews", {
            method: "POST",
            body: JSON.stringify({
                bookingId,
                rating,
                comment
            })
        });
        toast("Review submitted.");
        await loadMyBookings();
    } catch (e) {
        toast(e.message);
    }
}

function setCreateKindFields() {
    const kind = el.createKind.value;
    if (kind === "PROPERTY") {
        el.propertyFields.classList.remove("hidden");
        el.equipmentFields.classList.add("hidden");
    } else {
        el.propertyFields.classList.add("hidden");
        el.equipmentFields.classList.remove("hidden");
    }
}

async function createListing(event) {
    event.preventDefault();
    const form = new FormData(el.createListingForm);
    const kind = form.get("kind");
    const body = {
        kind,
        title: form.get("title"),
        description: form.get("description"),
        location: form.get("location"),
        imageUrl: form.get("imageUrl") || null,
        pricePerDay: Number(form.get("pricePerDay")),
        totalQuantity: Number(form.get("totalQuantity")),
        hostApprovalRequired: form.get("hostApprovalRequired") === "true",
        propertyType: form.get("propertyType") || null,
        maxGuests: form.get("maxGuests") ? Number(form.get("maxGuests")) : null,
        equipmentType: form.get("equipmentType") || null,
        conditionText: form.get("conditionText") || null
    };

    try {
        await api("/api/listings", {
            method: "POST",
            body: JSON.stringify(body)
        });
        toast("Listing saved.");
        el.createListingForm.reset();
        setCreateKindFields();
        await Promise.all([loadMyListings(), searchListings()]);
    } catch (e) {
        toast(e.message);
    }
}

function myListingCard(listing) {
    const imageSrc = toHtml(listing.imageUrl || defaultImageForListing(listing));
    const fallbackSrc = toHtml(defaultImageForListing(listing));
    return `
      <article class="card">
        <img class="listing-image" src="${imageSrc}" data-fallback="${fallbackSrc}" alt="${toHtml(listing.title)}" loading="lazy" onerror="this.onerror=null;this.src=this.dataset.fallback;">
        <h4>${toHtml(listing.title)}</h4>
        <p>${toHtml(listing.kind)} | <span class="chip ${statusClass(listing.status)}">${toHtml(listing.status)}</span></p>
        <p>${toHtml(listing.location)} | $${toHtml(listing.pricePerDay)}/day</p>
        <div class="booking-actions">
          <button data-action="toggle-status" data-id="${listing.id}" data-status="${listing.status}">
            ${listing.status === "ACTIVE" ? "Set Inactive" : "Set Active"}
          </button>
        </div>
        <div class="booking-actions">
          <input type="date" id="block-start-${listing.id}">
          <input type="date" id="block-end-${listing.id}">
          <button data-action="block-dates" data-id="${listing.id}">Block</button>
          <button data-action="open-dates" data-id="${listing.id}">Open</button>
        </div>
      </article>
    `;
}

async function loadMyListings() {
    try {
        el.myListings.innerHTML = `<p class="muted">Loading listings...</p>`;
        const listings = await api("/api/listings/host/mine");
        if (!listings.length) {
            el.myListings.innerHTML = `<p class="muted">No listings yet.</p>`;
            return;
        }
        el.myListings.innerHTML = listings.map(myListingCard).join("");

        el.myListings.querySelectorAll("button[data-action='toggle-status']").forEach((button) => {
            button.addEventListener("click", () => toggleListingStatus(button.dataset.id, button.dataset.status));
        });
        el.myListings.querySelectorAll("button[data-action='block-dates']").forEach((button) => {
            button.addEventListener("click", () => updateAvailability(button.dataset.id, "block"));
        });
        el.myListings.querySelectorAll("button[data-action='open-dates']").forEach((button) => {
            button.addEventListener("click", () => updateAvailability(button.dataset.id, "open"));
        });
    } catch (e) {
        toast(e.message);
    }
}

async function toggleListingStatus(listingId, currentStatus) {
    const next = currentStatus === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    try {
        await api(`/api/listings/${listingId}/status?status=${next}`, { method: "PATCH" });
        toast(`Listing updated to ${next}.`);
        await Promise.all([loadMyListings(), searchListings()]);
    } catch (e) {
        toast(e.message);
    }
}

async function updateAvailability(listingId, action) {
    const startDate = document.getElementById(`block-start-${listingId}`)?.value;
    const endDate = document.getElementById(`block-end-${listingId}`)?.value;
    if (!startDate || !endDate) {
        toast("Select start and end date for availability update.");
        return;
    }
    try {
        await api(`/api/listings/${listingId}/availability/${action}`, {
            method: "POST",
            body: JSON.stringify({ startDate, endDate })
        });
        toast(`Availability updated (${action}).`);
    } catch (e) {
        toast(e.message);
    }
}

function pendingRequestCard(booking) {
    return `
      <article class="card">
        <h4>${toHtml(booking.listingTitle)}</h4>
        <p>Booking ${toHtml(booking.id)}</p>
        <p>${toHtml(booking.startDate)} -> ${toHtml(booking.endDate)} | Qty: ${toHtml(booking.quantity)}</p>
        <p>Total: $${toHtml(booking.totalPrice)}</p>
        <div class="booking-actions">
          <button data-action="approve" data-id="${booking.id}">Approve</button>
          <button data-action="reject" data-id="${booking.id}">Reject</button>
        </div>
      </article>
    `;
}

async function loadPendingBookings() {
    try {
        el.pendingBookings.innerHTML = `<p class="muted">Loading pending requests...</p>`;
        const pending = await api("/api/bookings/host/pending");
        if (!pending.length) {
            el.pendingBookings.innerHTML = `<p class="muted">No pending requests.</p>`;
            return;
        }
        el.pendingBookings.innerHTML = pending.map(pendingRequestCard).join("");
        el.pendingBookings.querySelectorAll("button[data-action='approve']").forEach((button) => {
            button.addEventListener("click", () => decideBooking(button.dataset.id, true));
        });
        el.pendingBookings.querySelectorAll("button[data-action='reject']").forEach((button) => {
            button.addEventListener("click", () => decideBooking(button.dataset.id, false));
        });
    } catch (e) {
        toast(e.message);
    }
}

async function decideBooking(bookingId, approve) {
    const reason = approve ? null : window.prompt("Rejection reason:");
    if (!approve && (!reason || !reason.trim())) {
        toast("Rejection reason is required.");
        return;
    }
    try {
        await api(`/api/bookings/${bookingId}/decision`, {
            method: "POST",
            body: JSON.stringify({
                approve,
                reason
            })
        });
        toast(`Booking ${approve ? "approved" : "rejected"}.`);
        await Promise.all([loadPendingBookings(), searchListings()]);
    } catch (e) {
        toast(e.message);
    }
}

async function loadHostDataIfNeeded() {
    if (!state.user || (state.user.role !== "HOST" && state.user.role !== "ADMIN")) {
        return;
    }
    await Promise.all([loadMyListings(), loadPendingBookings()]);
}

function renderChatHistory(history) {
    if (!history.length) {
        el.chatHistory.innerHTML = `<p class="muted">No chat messages yet.</p>`;
        return;
    }
    el.chatHistory.innerHTML = history.map((msg) => `
      <div class="chat-msg ${msg.sender === "BOT" ? "bot" : "user"}">
        <strong>${toHtml(msg.sender)}:</strong> ${toHtml(msg.content)}
      </div>
    `).join("");
    el.chatHistory.scrollTop = el.chatHistory.scrollHeight;
}

function renderChatRecommendations(recommendations) {
    if (!recommendations?.length) {
        el.chatRecommendations.innerHTML = "";
        return;
    }
    el.chatRecommendations.innerHTML = recommendations.map((item) => listingCard(item, "Book This")).join("");
    el.chatRecommendations.querySelectorAll("button[data-action='book']").forEach((button) => {
        button.addEventListener("click", () => createBooking(button.dataset.id));
    });
}

async function startChatSession() {
    try {
        el.startChatBtn.disabled = true;
        const session = await api("/api/chat/start", { method: "POST" });
        state.chatSessionId = session.sessionId;
        renderChatHistory([{ sender: "BOT", content: session.greeting }]);
        toast("Chat session started.");
    } catch (e) {
        toast(e.message);
    } finally {
        el.startChatBtn.disabled = false;
    }
}

async function sendChatMessage(event) {
    event.preventDefault();
    if (!state.chatSessionId) {
        await startChatSession();
    }
    const form = new FormData(el.chatForm);
    const text = String(form.get("text") || "").trim();
    if (!text) return;
    const submitButton = el.chatForm.querySelector("button[type='submit']");
    try {
        submitButton.disabled = true;
        const result = await api("/api/chat/message", {
            method: "POST",
            body: JSON.stringify({
                sessionId: state.chatSessionId,
                text
            })
        });
        renderChatHistory(result.history || []);
        renderChatRecommendations(result.recommendations || []);
        el.chatForm.reset();
    } catch (e) {
        toast(e.message);
    } finally {
        submitButton.disabled = false;
    }
}

function setupQuickPrompts() {
    if (!el.chatQuickPrompts) return;
    el.chatQuickPrompts.querySelectorAll(".quick-prompt").forEach((button) => {
        button.addEventListener("click", () => {
            const input = el.chatForm?.querySelector("input[name='text']");
            if (!input) return;
            input.value = button.textContent.trim();
            input.focus();
        });
    });
}

el.registerForm.addEventListener("submit", register);
el.loginForm.addEventListener("submit", login);
el.logoutBtn.addEventListener("click", logout);
el.refreshBtn.addEventListener("click", () => loadDashboard());
el.searchForm.addEventListener("submit", searchListings);
el.createListingForm.addEventListener("submit", createListing);
el.createKind.addEventListener("change", setCreateKindFields);
el.startChatBtn.addEventListener("click", startChatSession);
el.chatForm.addEventListener("submit", sendChatMessage);
el.authTabLogin.addEventListener("click", () => setAuthMode("login"));
el.authTabRegister.addEventListener("click", () => setAuthMode("register"));
if (el.authToRegister) el.authToRegister.addEventListener("click", () => setAuthMode("register"));
if (el.authToLogin) el.authToLogin.addEventListener("click", () => setAuthMode("login"));
if (el.appViewTabs) {
    el.appViewTabs.querySelectorAll(".view-tab[data-view]").forEach((button) => {
        button.addEventListener("click", () => setAppView(button.dataset.view));
    });
}

setCreateKindFields();
setupQuickPrompts();
setAuthMode("login");
setAppView("marketplace");
checkAuth();
