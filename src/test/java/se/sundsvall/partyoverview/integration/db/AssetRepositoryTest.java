package se.sundsvall.partyoverview.integration.db;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import se.sundsvall.partyoverview.integration.db.model.AssetEntity;
import se.sundsvall.partyoverview.integration.db.model.PartyEmbeddable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

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

	@Autowired
	private AssetRepository repository;

	@Test
	void findByMunicipalityIdAndSourceAndSourceId() {
		final var result = repository.findByMunicipalityIdAndSourceAndSourceId(MUNICIPALITY_ID, SOURCE, SOURCE_ID);

		assertThat(result).hasValueSatisfying(asset -> {
			assertThat(asset.getId()).isEqualTo("e84b72ee-1a34-44b5-b8f6-2e0e42e99010");
			assertThat(asset.getVersion()).isEqualTo(3L);
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
	void findDistinctByMunicipalityIdAndPartiesPartyIdReturnsEachAssetOnce() {
		final var result = repository.findDistinctByMunicipalityIdAndPartiesPartyId(MUNICIPALITY_ID, PARTY_ID);

		assertThat(result)
			.extracting(AssetEntity::getSourceId)
			.containsExactly(SOURCE_ID);
	}

	@Test
	void findDistinctByMunicipalityIdAndPartiesPartyIdForUnknownParty() {
		assertThat(repository.findDistinctByMunicipalityIdAndPartiesPartyId(MUNICIPALITY_ID, "f0000000-0000-0000-0000-000000000000")).isEmpty();
	}

	@Test
	void deleteByMunicipalityIdAndSourceAndSourceId() {
		repository.deleteByMunicipalityIdAndSourceAndSourceId(MUNICIPALITY_ID, SOURCE, SOURCE_ID);
		repository.flush();

		assertThat(repository.findByMunicipalityIdAndSourceAndSourceId(MUNICIPALITY_ID, SOURCE, SOURCE_ID)).isEmpty();
		assertThat(repository.count()).isEqualTo(2);
	}

	@Test
	void saveAssignsAnId() {
		final var saved = repository.saveAndFlush(AssetEntity.create()
			.withMunicipalityId(MUNICIPALITY_ID)
			.withSource(SOURCE)
			.withSourceId("7a1d9c3e-2b4f-4e6a-9c8d-0f1e2d3c4b5a")
			.withVersion(1L)
			.withParties(List.of(PartyEmbeddable.create().withPartyId(PARTY_ID))));

		assertThat(saved.getId()).isNotBlank();
	}

	@Test
	void duplicateSourceIdInTheSameMunicipalityIsRejected() {
		final var duplicate = AssetEntity.create()
			.withMunicipalityId(MUNICIPALITY_ID)
			.withSource(SOURCE)
			.withSourceId(SOURCE_ID)
			.withVersion(4L);

		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> repository.saveAndFlush(duplicate));
	}
}
