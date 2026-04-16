package com.rentit.repository;

import com.rentit.domain.Booking;
import com.rentit.domain.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @Query("""
            select b from Booking b
            where b.listing.id = :listingId
              and b.status in :statuses
              and b.startDate <= :endDate
              and b.endDate >= :startDate
            """)
    List<Booking> findOverlappingByStatuses(
            @Param("listingId") UUID listingId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    List<Booking> findByRenterIdOrderByCreatedAtDesc(UUID renterId);

    @Query("""
            select b from Booking b
            where b.listing.owner.id = :hostId
              and b.status = :status
            order by b.createdAt desc
            """)
    List<Booking> findHostBookingsByStatus(@Param("hostId") UUID hostId, @Param("status") BookingStatus status);

    List<Booking> findByStatusOrderByCreatedAtDesc(BookingStatus status);
}
