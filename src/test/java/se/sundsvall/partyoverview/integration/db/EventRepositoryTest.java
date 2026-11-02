package se.sundsvall.partyoverview.integration.db;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import se.sundsvall.partyoverview.integration.db.model.EventEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;
import static se.sundsvall.partyoverview.integration.db.model.ObjectType.ASSET;
import static se.sundsvall.partyoverview.integration.source.Collisions.isCollision;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@ActiveProfiles("junit")
@Sql(scripts = {
	"/db/scripts/truncate.sql",
	"/db/scripts/eventRepositoryTest.sql"
})
class EventRepositoryTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String SOURCE = "PARTY_ASSETS";
	private static final String SOURCE_ID = "1c8f38a6-b492-4037-b7dc-de5bc6c629f0";
	private static final String OTHER_SOURCE_ID = "5f0b5b0a-7b1c-4cf2-9a8e-2d6f6f0f1a01";

	@Autowired
	private EventRepository repository;

	@Test
	void findByMunicipalityIdAndSourceAndObjectTypeAndSourceIdAndVersion() {
		final var result = repository.findByMunicipalityIdAndSourceAndObjectTypeAndSourceIdAndVersion(MUNICIPALITY_ID, SOURCE, ASSET, SOURCE_ID, 2L);

		assertThat(result).hasValueSatisfying(event -> {
			assertThat(event.getEventId()).isEqualTo("c0a4e9f2-6d3b-4f1a-9e8c-7b5a3d1f2e04");
			assertThat(event.getPayload()).isEqualTo("{\"version\":2}");
		});
	}

	@Test
	void findByMunicipalityIdAndSourceAndObjectTypeAndSourceIdAndVersionForUnknownVersion() {
		assertThat(repository.findByMunicipalityIdAndSourceAndObjectTypeAndSourceIdAndVersion(MUNICIPALITY_ID, SOURCE, ASSET, SOURCE_ID, 3L)).isEmpty();
	}

	@Test
	void deleteByMunicipalityIdAndSourceAndObjectTypeAndSourceIdRemovesTheWholeHistory() {
		repository.deleteByMunicipalityIdAndSourceAndObjectTypeAndSourceId(MUNICIPALITY_ID, SOURCE, ASSET, SOURCE_ID);

		assertThat(repository.findAll())
			.extracting(EventEntity::getSourceId)
			.containsExactly(OTHER_SOURCE_ID);
	}

	@Test
	void saveAssignsAnIdAndReceivedAt() {
		final var saved = repository.saveAndFlush(event(3L));

		assertThat(saved.getId()).isNotBlank();
		assertThat(saved.getReceivedAt()).isNotNull();
	}

	@Test
	void sameVersionOfTheSameObjectTwiceIsRejectedAsUniqueViolation() {
		final var duplicate = event(2L);

		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> repository.saveAndFlush(duplicate))
			.satisfies(exception -> assertThat(isCollision(exception)).isTrue());
	}

	private static EventEntity event(final long version) {
		return EventEntity.create()
			.withEventId("e2c6ab14-8f5d-4b3c-9a0e-9d7c5f3b4a06")
			.withMunicipalityId(MUNICIPALITY_ID)
			.withSource(SOURCE)
			.withObjectType(ASSET)
			.withSourceId(SOURCE_ID)
			.withVersion(version)
			.withOccurredAt(OffsetDateTime.now())
			.withPayload("{}");
	}
}
