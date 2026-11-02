package se.sundsvall.partyoverview.service.mapper;

import org.junit.jupiter.api.Test;
import se.sundsvall.partyoverview.integration.db.model.EventEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.partyoverview.TestFactory.assetChange;
import static se.sundsvall.partyoverview.service.mapper.EventMapper.updateEventEntity;
import static se.sundsvall.partyoverview.service.model.Operation.UPSERT;

class EventMapperTest {

	private static final String ID = "5c3f1f0e-1a2b-4c3d-8e4f-5a6b7c8d9e01";

	@Test
	void updateEventEntitySetsEveryField() {
		final var change = assetChange(UPSERT, 3);
		final var payload = "{\"key\":\"value\"}";
		final var entity = EventEntity.create().withId(ID);

		final var result = updateEventEntity(entity, change, payload);

		assertThat(result).isSameAs(entity);
		assertThat(result).usingRecursiveComparison().isEqualTo(EventEntity.create()
			.withId(ID)
			.withEventId(change.eventId())
			.withMunicipalityId(change.municipalityId())
			.withSource(change.source())
			.withObjectType(change.objectType())
			.withSourceId(change.sourceId())
			.withVersion(3L)
			.withOccurredAt(change.occurredAt())
			.withAuthority(change.authority())
			.withPayload(payload));
	}

	@Test
	void updateEventEntityReplacesWhatAnEarlierDeliveryStored() {
		final var change = assetChange(UPSERT, 3);
		final var entity = EventEntity.create().withId(ID).withEventId("earlier-event-id").withPayload("{\"earlier\":true}");

		final var result = updateEventEntity(entity, change, "{}");

		assertThat(result.getId()).isEqualTo(ID);
		assertThat(result.getEventId()).isEqualTo(change.eventId());
		assertThat(result.getPayload()).isEqualTo("{}");
	}
}
