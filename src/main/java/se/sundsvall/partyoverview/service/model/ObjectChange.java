package se.sundsvall.partyoverview.service.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.OffsetDateTime;
import java.util.List;
import se.sundsvall.dept44.common.validators.annotation.ValidMunicipalityId;
import se.sundsvall.dept44.common.validators.annotation.ValidUuid;
import se.sundsvall.partyoverview.integration.db.model.ObjectType;
import se.sundsvall.partyoverview.integration.db.model.Operation;

/**
 * A change of an object in a source system. The translator of each source creates it from the source's own event, and
 * from there on every source is handled alike. A version replaces the same or a lower one, and a DELETE is final.
 *
 * @param eventId        unique id of the event, used to recognize a redelivery
 * @param source         the system the event comes from, e.g. PARTY_ASSETS
 * @param objectType     what kind of object changed
 * @param operation      UPSERT for a new or changed object, DELETE when the source has removed it
 * @param sourceId       the id of the object in the source system
 * @param version        grows with every change of the object in the source system
 * @param occurredAt     when the change happened in the source system
 * @param municipalityId the municipality the object belongs to
 * @param authority      the committee (nämnd) responsible for the object, when the translator knows it
 * @param parties        the parties the object concerns
 * @param data           the object itself. Not needed for a DELETE
 */
public record ObjectChange(
	@ValidUuid String eventId,
	@NotBlank String source,
	@NotNull ObjectType objectType,
	@NotNull Operation operation,
	@NotBlank String sourceId,
	@NotNull @PositiveOrZero Long version,
	@NotNull OffsetDateTime occurredAt,
	@ValidMunicipalityId String municipalityId,
	String authority,
	List<@NotNull @Valid Party> parties,
	ObjectData data) {
}
