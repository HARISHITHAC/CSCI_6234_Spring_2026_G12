# RentIt (Spring Boot + Web UI Prototype)

This implementation follows the provided system design:
- Listings search/filter with availability checks.
- Booking request flow with host approval or auto-approval.
- Payment simulation and receipt generation.
- Booking state transitions (`PENDING_APPROVAL`, `APPROVED`, `CONFIRMED`, etc.).
- Chat concierge sessions/messages with listing recommendations.
- Host listing/availability management.
- Reviews after completed rentals.

## Tech Stack
- Java 17
- Spring Boot 3 (Web, Validation, JPA)
- H2 database (file-based)
- Static frontend (HTML/CSS/Vanilla JS)

## Run
1. Build and run with Maven:
   `mvn spring-boot:run`
2. Open:
   `http://localhost:8080`

## Demo Users
- `renter@rentit.local` / `password123`
- `host@rentit.local` / `password123`
- `host2@rentit.local` / `password123`
- `admin@rentit.local` / `password123`

The app seeds these users + sample listings on first startup.

## Gemini RAG Chatbot
The chatbot now supports Gemini with your own listing data as RAG context.

1. Set API key before starting the app:
   - PowerShell: `$env:GEMINI_API_KEY="your_key_here"`
2. Ensure provider is Gemini (default):
   - `rentit.ai.provider=GEMINI`
3. Start app normally.

If `GEMINI_API_KEY` is missing, chatbot automatically falls back to deterministic local matching.
