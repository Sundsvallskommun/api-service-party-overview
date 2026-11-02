package se.sundsvall.partyoverview.integration.db;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import se.sundsvall.partyoverview.integration.db.model.AssetEntity;

@CircuitBreaker(name = "assetRepository")
public interface AssetRepository extends JpaRepository<AssetEntity, String> {

	Optional<AssetEntity> findByMunicipalityIdAndSourceAndSourceId(String municipalityId, String source, String sourceId);

	List<AssetEntity> findDistinctByMunicipalityIdAndPartiesPartyId(String municipalityId, String partyId);

	void deleteByMunicipalityIdAndSourceAndSourceId(String municipalityId, String source, String sourceId);
}
