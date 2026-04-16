package com.rentit.config;

import com.rentit.domain.AvailabilitySlot;
import com.rentit.domain.EquipmentListing;
import com.rentit.domain.Listing;
import com.rentit.domain.PropertyListing;
import com.rentit.domain.UserAccount;
import com.rentit.domain.enums.ListingStatus;
import com.rentit.domain.enums.Role;
import com.rentit.repository.AvailabilitySlotRepository;
import com.rentit.repository.ListingRepository;
import com.rentit.repository.UserAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Configuration
public class DemoDataInitializer {

    @Bean
    CommandLineRunner seedDemoData(
            UserAccountRepository userAccountRepository,
            ListingRepository listingRepository,
            AvailabilitySlotRepository availabilitySlotRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            if (userAccountRepository.count() > 0) {
                backfillImages(listingRepository);
                return;
            }

            UserAccount renter = new UserAccount();
            renter.setName("Renter Demo");
            renter.setEmail("renter@rentit.local");
            renter.setPasswordHash(passwordEncoder.encode("password123"));
            renter.setRole(Role.RENTER);
            userAccountRepository.save(renter);

            UserAccount host = new UserAccount();
            host.setName("Host Demo");
            host.setEmail("host@rentit.local");
            host.setPasswordHash(passwordEncoder.encode("password123"));
            host.setRole(Role.HOST);
            userAccountRepository.save(host);

            UserAccount host2 = new UserAccount();
            host2.setName("Host 2");
            host2.setEmail("host2@rentit.local");
            host2.setPasswordHash(passwordEncoder.encode("password123"));
            host2.setRole(Role.HOST);
            userAccountRepository.save(host2);

            UserAccount admin = new UserAccount();
            admin.setName("Admin Demo");
            admin.setEmail("admin@rentit.local");
            admin.setPasswordHash(passwordEncoder.encode("password123"));
            admin.setRole(Role.ADMIN);
            userAccountRepository.save(admin);

            PropertyListing property = new PropertyListing();
            property.setTitle("Downtown Studio Apartment");
            property.setDescription("Modern studio for short stay near city center.");
            property.setLocation("New York");
            property.setImageUrl("https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=1200&q=80");
            property.setPricePerDay(new BigDecimal("120.00"));
            property.setStatus(ListingStatus.ACTIVE);
            property.setHostApprovalRequired(true);
            property.setTotalQuantity(1);
            property.setPropertyType("Studio");
            property.setMaxGuests(2);
            property.setOwner(host);
            listingRepository.save(property);

            PropertyListing property2 = new PropertyListing();
            property2.setTitle("Soho Loft");
            property2.setDescription("Spacious loft, self check-in and flexible dates.");
            property2.setLocation("New York");
            property2.setImageUrl("https://images.unsplash.com/photo-1493663284031-b7e3aefcae8e?auto=format&fit=crop&w=1200&q=80");
            property2.setPricePerDay(new BigDecimal("145.00"));
            property2.setStatus(ListingStatus.ACTIVE);
            property2.setHostApprovalRequired(false);
            property2.setTotalQuantity(1);
            property2.setPropertyType("Loft");
            property2.setMaxGuests(3);
            property2.setOwner(host2);
            listingRepository.save(property2);

            EquipmentListing equipment = new EquipmentListing();
            equipment.setTitle("Mountain Bike");
            equipment.setDescription("Trail-ready mountain bike with helmet.");
            equipment.setLocation("New York");
            equipment.setImageUrl("https://images.unsplash.com/photo-1485965120184-e220f721d03e?auto=format&fit=crop&w=1200&q=80");
            equipment.setPricePerDay(new BigDecimal("35.00"));
            equipment.setStatus(ListingStatus.ACTIVE);
            equipment.setHostApprovalRequired(false);
            equipment.setTotalQuantity(3);
            equipment.setEquipmentType("Bike");
            equipment.setConditionText("Excellent");
            equipment.setOwner(host);
            listingRepository.save(equipment);

            AvailabilitySlot blocked = new AvailabilitySlot();
            blocked.setListing(property);
            blocked.setStartDate(LocalDate.now().plusDays(5));
            blocked.setEndDate(LocalDate.now().plusDays(7));
            blocked.setBlocked(true);
            availabilitySlotRepository.save(blocked);

            backfillImages(listingRepository);
        };
    }

    private void backfillImages(ListingRepository listingRepository) {
        for (Listing listing : listingRepository.findAll()) {
            if (listing.getImageUrl() != null && !listing.getImageUrl().isBlank()) {
                continue;
            }
            String title = listing.getTitle() == null ? "" : listing.getTitle().toLowerCase();
            if (title.contains("studio")) {
                listing.setImageUrl("https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=1200&q=80");
            } else if (title.contains("loft")) {
                listing.setImageUrl("https://images.unsplash.com/photo-1493663284031-b7e3aefcae8e?auto=format&fit=crop&w=1200&q=80");
            } else if (title.contains("bike")) {
                listing.setImageUrl("https://images.unsplash.com/photo-1485965120184-e220f721d03e?auto=format&fit=crop&w=1200&q=80");
            }
            listingRepository.save(listing);
        }
    }
}
