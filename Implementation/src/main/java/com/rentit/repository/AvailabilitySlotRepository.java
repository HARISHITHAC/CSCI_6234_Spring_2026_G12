package com.rentit.repository;

import com.rentit.domain.AvailabilitySlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, UUID> {

    @Query("""
            select s from AvailabilitySlot s
            where s.listing.id = :listingId
              and s.blocked = true
              and s.startDate <= :endDate
              and s.endDate >= :startDate
            """)
    List<AvailabilitySlot> findBlockedOverlaps(
            @Param("listingId") UUID listingId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    List<AvailabilitySlot> findByListingId(UUID listingId);
}
