package se.sundsvall.partyoverview.service.mapper;

import se.sundsvall.partyoverview.integration.db.model.EventEntity;
import se.sundsvall.partyoverview.service.model.ObjectChange;

import static java.util.Optional.ofNullable;

public final class EventMapper {

	private EventMapper() {}

	public static EventEntity toEventEntity(final ObjectChange change, final String payload) {
		return ofNullable(change)
			.map(c -> EventEntity.create()
				.withEventId(c.eventId())
				.withMunicipalityId(c.municipalityId())
				.withSource(c.source())
				.withObjectType(c.objectType())
				.withSourceId(c.sourceId())
				.withOperation(c.operation())
				.withVersion(c.version())
				.withOccurredAt(c.occurredAt())
				.withAuthority(c.authority())
				.withPayload(payload))
			.orElse(null);
	}

	/**
	 * A tombstone keeps no payload, since nothing about the object may outlive its deletion in the source system.
	 */
	public static EventEntity toTombstoneEntity(final ObjectChange change) {
		return toEventEntity(change, null);
	}
}
