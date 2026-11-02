package se.sundsvall.partyoverview.integration.db;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import se.sundsvall.partyoverview.integration.db.model.EventEntity;
import se.sundsvall.partyoverview.integration.db.model.ObjectType;

@CircuitBreaker(name = "eventRepository")
public interface EventRepository extends JpaRepository<EventEntity, String> {

	Optional<EventEntity> findByMunicipalityIdAndSourceAndObjectTypeAndSourceIdAndVersion(String municipalityId, String source, ObjectType objectType, String sourceId, Long version);

	/**
	 * Removes the whole history of an object in one statement, without loading its payloads first.
	 */
	@Modifying
	@Query("delete from EventEntity e where e.municipalityId = :municipalityId and e.source = :source and e.objectType = :objectType and e.sourceId = :sourceId")
	void deleteByMunicipalityIdAndSourceAndObjectTypeAndSourceId(String municipalityId, String source, ObjectType objectType, String sourceId);
}
