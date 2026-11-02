package se.sundsvall.partyoverview.service.mapper;

import org.junit.jupiter.api.Test;
import se.sundsvall.partyoverview.integration.db.model.EventEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.partyoverview.TestFactory.assetChange;
import static se.sundsvall.partyoverview.integration.db.model.Operation.DELETE;
import static se.sundsvall.partyoverview.integration.db.model.Operation.UPSERT;
import static se.sundsvall.partyoverview.service.mapper.EventMapper.toEventEntity;
import static se.sundsvall.partyoverview.service.mapper.EventMapper.toTombstoneEntity;

class EventMapperTest {

	@Test
	void toEventEntityMapsEveryField() {
		final var change = assetChange(UPSERT, 3);
		final var payload = "{\"key\":\"value\"}";

		final var result = toEventEntity(change, payload);

		assertThat(result).usingRecursiveComparison().isEqualTo(EventEntity.create()
			.withEventId(change.eventId())
			.withMunicipalityId(change.municipalityId())
			.withSource(change.source())
			.withObjectType(change.objectType())
			.withSourceId(change.sourceId())
			.withOperation(UPSERT)
			.withVersion(3L)
			.withOccurredAt(change.occurredAt())
			.withAuthority(change.authority())
			.withPayload(payload));
	}

	@Test
	void toEventEntityFromNull() {
		assertThat(toEventEntity(null, "{}")).isNull();
	}

	@Test
	void toTombstoneEntityKeepsNoPayload() {
		final var change = assetChange(DELETE, 4);

		final var result = toTombstoneEntity(change);

		assertThat(result.getEventId()).isEqualTo(change.eventId());
		assertThat(result.getOperation()).isEqualTo(DELETE);
		assertThat(result.getVersion()).isEqualTo(4L);
		assertThat(result.getPayload()).isNull();
	}
}
