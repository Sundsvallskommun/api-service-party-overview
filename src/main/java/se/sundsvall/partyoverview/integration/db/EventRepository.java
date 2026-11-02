package se.sundsvall.partyoverview.integration.db;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.data.jpa.repository.JpaRepository;
import se.sundsvall.partyoverview.integration.db.model.EventEntity;
import se.sundsvall.partyoverview.integration.db.model.ObjectType;
import se.sundsvall.partyoverview.integration.db.model.Operation;

@CircuitBreaker(name = "eventRepository")
public interface EventRepository extends JpaRepository<EventEntity, String> {

	boolean existsByEventId(String eventId);

	boolean existsByMunicipalityIdAndSourceAndObjectTypeAndSourceIdAndOperation(String municipalityId, String source, ObjectType objectType, String sourceId, Operation operation);

	void deleteByMunicipalityIdAndSourceAndObjectTypeAndSourceId(String municipalityId, String source, ObjectType objectType, String sourceId);
}
