package com.parking.infrastructure.adapter.out.persistence;

import com.parking.application.port.out.VehicleEntryRepositoryPort;
import com.parking.domain.entity.EntryHistoryPage;
import com.parking.domain.entity.EntryHistoryQuery;
import com.parking.domain.entity.EntryStatus;
import com.parking.domain.entity.VehicleEntry;
import com.parking.domain.exception.VehicleAlreadyInsideException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class VehicleEntryPersistenceAdapter implements VehicleEntryRepositoryPort {

    private static final Sort MOST_RECENT_FIRST = Sort.by(Sort.Direction.DESC, "entryTime", "id");

    private final SpringDataVehicleEntryRepository springDataRepository;

    /**
     * The use case already checks for an active entry; the UNIQUE constraint on
     * {@code active_plate} catches two simultaneous entries of the same plate.
     */
    @Override
    public VehicleEntry save(VehicleEntry entry) {
        try {
            return springDataRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException ex) {
            throw new VehicleAlreadyInsideException(entry.getPlate());
        }
    }

    @Override
    public Optional<VehicleEntry> findActiveByPlate(String plate) {
        return springDataRepository.findByPlateAndStatus(plate, EntryStatus.ACTIVE);
    }

    @Override
    public boolean existsActiveByPlate(String plate) {
        return springDataRepository.existsByPlateAndStatus(plate, EntryStatus.ACTIVE);
    }

    @Override
    public List<VehicleEntry> findAllActive() {
        return springDataRepository.findAllByStatusOrderByEntryTimeDescIdDesc(EntryStatus.ACTIVE);
    }

    @Override
    public EntryHistoryPage findHistory(EntryHistoryQuery query) {
        Page<VehicleEntry> page = springDataRepository.findAll(
                matching(query), PageRequest.of(query.page(), query.size(), MOST_RECENT_FIRST));
        return new EntryHistoryPage(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    /** Built per query because PostgreSQL cannot type a NULL parameter in "(:from IS NULL OR ...)". */
    private static Specification<VehicleEntry> matching(EntryHistoryQuery query) {
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.plate() != null) {
                predicates.add(builder.like(root.<String>get("plate"), "%" + query.plate() + "%"));
            }
            if (query.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.<Instant>get("entryTime"), query.from()));
            }
            if (query.to() != null) {
                predicates.add(builder.lessThan(root.<Instant>get("entryTime"), query.to()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
