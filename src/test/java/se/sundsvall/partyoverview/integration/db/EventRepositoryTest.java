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
import static se.sundsvall.partyoverview.integration.db.model.Operation.DELETE;
import static se.sundsvall.partyoverview.integration.db.model.Operation.UPSERT;

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
	private static final String DELETED_SOURCE_ID = "5f0b5b0a-7b1c-4cf2-9a8e-2d6f6f0f1a01";

	@Autowired
	private EventRepository repository;

	@Test
	void existsByEventId() {
		assertThat(repository.existsByEventId("b82bd8ac-1507-4d9a-958d-369261eecc15")).isTrue();
		assertThat(repository.existsByEventId("f0000000-0000-0000-0000-000000000000")).isFalse();
	}

	@Test
	void existsByMunicipalityIdAndSourceAndObjectTypeAndSourceIdAndOperation() {
		assertThat(repository.existsByMunicipalityIdAndSourceAndObjectTypeAndSourceIdAndOperation(MUNICIPALITY_ID, SOURCE, ASSET, DELETED_SOURCE_ID, DELETE)).isTrue();
		assertThat(repository.existsByMunicipalityIdAndSourceAndObjectTypeAndSourceIdAndOperation(MUNICIPALITY_ID, SOURCE, ASSET, SOURCE_ID, DELETE)).isFalse();
	}

	@Test
	void deleteByMunicipalityIdAndSourceAndObjectTypeAndSourceId() {
		repository.deleteByMunicipalityIdAndSourceAndObjectTypeAndSourceId(MUNICIPALITY_ID, SOURCE, ASSET, SOURCE_ID);
		repository.flush();

		assertThat(repository.findAll())
			.extracting(EventEntity::getSourceId)
			.containsExactly(DELETED_SOURCE_ID);
	}

	@Test
	void saveAssignsAnIdAndReceivedAt() {
		final var saved = repository.saveAndFlush(EventEntity.create()
			.withEventId("e2c6ab14-8f5d-4b3c-9a0e-9d7c5f3b4a06")
			.withMunicipalityId(MUNICIPALITY_ID)
			.withSource(SOURCE)
			.withObjectType(ASSET)
			.withSourceId(SOURCE_ID)
			.withOperation(UPSERT)
			.withVersion(3L)
			.withOccurredAt(OffsetDateTime.now())
			.withPayload("{}"));

		assertThat(saved.getId()).isNotBlank();
		assertThat(saved.getReceivedAt()).isNotNull();
	}

	@Test
	void duplicateEventIdIsRejected() {
		final var duplicate = EventEntity.create()
			.withEventId("b82bd8ac-1507-4d9a-958d-369261eecc15")
			.withMunicipalityId(MUNICIPALITY_ID)
			.withSource(SOURCE)
			.withObjectType(ASSET)
			.withSourceId(SOURCE_ID)
			.withOperation(UPSERT)
			.withVersion(3L)
			.withOccurredAt(OffsetDateTime.now());

		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> repository.saveAndFlush(duplicate));
	}
}
