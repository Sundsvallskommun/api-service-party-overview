package se.sundsvall.partyoverview.integration.db;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import se.sundsvall.partyoverview.integration.db.model.AssetEntity;

@CircuitBreaker(name = "assetRepository")
public interface AssetRepository extends JpaRepository<AssetEntity, String> {

	Optional<AssetEntity> findByMunicipalityIdAndSourceAndSourceId(String municipalityId, String source, String sourceId);

	/**
	 * The assets that concern a party. An exists subquery rather than a join, so that an asset with several roles for the
	 * same party comes back once without a DISTINCT over its long text columns.
	 */
	@Query("""
		select a from AssetEntity a
		where a.municipalityId = :municipalityId
		and a.deleted = false
		and exists (select 1 from a.parties p where p.partyId = :partyId)""")
	List<AssetEntity> findByMunicipalityIdAndPartyId(String municipalityId, String partyId);
}
