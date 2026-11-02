package se.sundsvall.partyoverview.service.mapper;

import se.sundsvall.partyoverview.integration.db.model.EventEntity;
import se.sundsvall.partyoverview.service.model.ObjectChange;

public final class EventMapper {

	private EventMapper() {}

	public static EventEntity updateEventEntity(final EventEntity entity, final ObjectChange change, final String payload) {
		return entity
			.withEventId(change.eventId())
			.withMunicipalityId(change.municipalityId())
			.withSource(change.source())
			.withObjectType(change.objectType())
			.withSourceId(change.sourceId())
			.withVersion(change.version())
			.withOccurredAt(change.occurredAt())
			.withAuthority(change.authority())
			.withPayload(payload);
	}
}
