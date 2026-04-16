package com.rentit.repository;

import com.rentit.domain.Listing;
import com.rentit.domain.UserAccount;
import com.rentit.domain.enums.ListingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ListingRepository extends JpaRepository<Listing, UUID> {
    List<Listing> findByStatus(ListingStatus status);
    List<Listing> findByOwner(UserAccount owner);
}
