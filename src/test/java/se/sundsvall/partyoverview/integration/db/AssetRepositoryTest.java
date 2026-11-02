package se.sundsvall.partyoverview.integration.db;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import se.sundsvall.partyoverview.integration.db.model.AssetEntity;
import se.sundsvall.partyoverview.integration.db.model.PartyEmbeddable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;
import static se.sundsvall.partyoverview.integration.source.Collisions.isCollision;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@ActiveProfiles("junit")
@Sql(scripts = {
	"/db/scripts/truncate.sql",
	"/db/scripts/assetRepositoryTest.sql"
})
class AssetRepositoryTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String SOURCE = "PARTY_ASSETS";
	private static final String SOURCE_ID = "1c8f38a6-b492-4037-b7dc-de5bc6c629f0";
	private static final String PARTY_ID = "81471222-5798-11e9-ae24-57fa13b361e1";
	private static final String OTHER_PARTY_ID = "c5d21b57-c785-4d3c-8361-940cae999ff7";

	@Autowired
	private AssetRepository repository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void findByMunicipalityIdAndSourceAndSourceId() {
		final var result = repository.findByMunicipalityIdAndSourceAndSourceId(MUNICIPALITY_ID, SOURCE, SOURCE_ID);

		assertThat(result).hasValueSatisfying(asset -> {
			assertThat(asset.getId()).isEqualTo("e84b72ee-1a34-44b5-b8f6-2e0e42e99010");
			assertThat(asset.getVersion()).isEqualTo(3L);
			assertThat(asset.getDeleted()).isFalse();
			assertThat(asset.getTitle()).isEqualTo("Serveringstillstånd");
			assertThat(asset.getIssued()).isEqualTo(LocalDate.of(2026, 1, 1));
			assertThat(asset.getParties())
				.extracting(PartyEmbeddable::getPartyId, PartyEmbeddable::getRole)
				.containsExactlyInAnyOrder(tuple(PARTY_ID, "OWNER"), tuple(PARTY_ID, "APPLICANT"));
		});
	}

	@Test
	void findByMunicipalityIdAndSourceAndSourceIdInOtherMunicipality() {
		assertThat(repository.findByMunicipalityIdAndSourceAndSourceId("2260", SOURCE, SOURCE_ID)).isEmpty();
	}

	@Test
	void findByMunicipalityIdAndPartyIdReturnsEachAssetOnce() {
		final var result = repository.findByMunicipalityIdAndPartyId(MUNICIPALITY_ID, PARTY_ID);

		assertThat(result)
			.extracting(AssetEntity::getSourceId)
			.containsExactly(SOURCE_ID);
	}

	@Test
	void findByMunicipalityIdAndPartyIdLeavesOutTombstones() {
		final var result = repository.findByMunicipalityIdAndPartyId(MUNICIPALITY_ID, OTHER_PARTY_ID);

		assertThat(result)
			.extracting(AssetEntity::getSourceId)
			.containsExactly("5f0b5b0a-7b1c-4cf2-9a8e-2d6f6f0f1a01");
	}

	@Test
	void findByMunicipalityIdAndPartyIdForUnknownParty() {
		assertThat(repository.findByMunicipalityIdAndPartyId(MUNICIPALITY_ID, "f0000000-0000-0000-0000-000000000000")).isEmpty();
	}

	@Test
	void saveAssignsAnIdAndALockVersion() {
		final var saved = repository.saveAndFlush(AssetEntity.create()
			.withMunicipalityId(MUNICIPALITY_ID)
			.withSource(SOURCE)
			.withSourceId("7a1d9c3e-2b4f-4e6a-9c8d-0f1e2d3c4b5a")
			.withVersion(1L)
			.withDeleted(false)
			.withParties(List.of(PartyEmbeddable.create().withPartyId(PARTY_ID))));

		assertThat(saved.getId()).isNotBlank();
		assertThat(saved.getLockVersion()).isZero();
	}

	@Test
	void updateFromAStaleCopyIsRejected() {
		final var stale = repository.findByMunicipalityIdAndSourceAndSourceId(MUNICIPALITY_ID, SOURCE, SOURCE_ID).orElseThrow();
		entityManager.detach(stale);
		final var current = repository.findByMunicipalityIdAndSourceAndSourceId(MUNICIPALITY_ID, SOURCE, SOURCE_ID).orElseThrow();
		repository.saveAndFlush(current.withTitle("changed by another consumer"));

		assertThatExceptionOfType(OptimisticLockingFailureException.class)
			.isThrownBy(() -> repository.saveAndFlush(stale.withTitle("changed from a stale copy")));
	}

	@Test
	void duplicateSourceIdInTheSameMunicipalityIsRejectedAsUniqueViolation() {
		final var duplicate = AssetEntity.create()
			.withMunicipalityId(MUNICIPALITY_ID)
			.withSource(SOURCE)
			.withSourceId(SOURCE_ID)
			.withVersion(4L)
			.withDeleted(false);

		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> repository.saveAndFlush(duplicate))
			.satisfies(exception -> assertThat(isCollision(exception)).isTrue());
	}
}
