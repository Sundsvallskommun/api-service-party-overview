package se.sundsvall.partyoverview.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import se.sundsvall.partyoverview.Application;
import se.sundsvall.partyoverview.integration.db.AssetRepository;
import se.sundsvall.partyoverview.integration.db.EventRepository;
import se.sundsvall.partyoverview.service.model.ObjectChange;
import se.sundsvall.partyoverview.service.model.Party;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static se.sundsvall.partyoverview.TestFactory.MUNICIPALITY_ID;
import static se.sundsvall.partyoverview.TestFactory.SOURCE;
import static se.sundsvall.partyoverview.TestFactory.SOURCE_ID;
import static se.sundsvall.partyoverview.TestFactory.assetChange;
import static se.sundsvall.partyoverview.integration.db.model.ObjectType.ASSET;
import static se.sundsvall.partyoverview.service.model.Operation.UPSERT;

/**
 * Validation of {@link ObjectChange} through the Spring proxy of {@link EventService}, the way a listener calls it.
 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("junit")
class EventServiceValidationTest {

	@MockitoBean
	private EventRepository eventRepositoryMock;

	@MockitoBean
	private AssetRepository assetRepositoryMock;

	@Autowired
	private EventService service;

	@Test
	void handleValidChange() {
		service.handle(assetChange(UPSERT, 1));

		verify(assetRepositoryMock).findByMunicipalityIdAndSourceAndSourceId(MUNICIPALITY_ID, SOURCE, SOURCE_ID);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidChanges")
	void handleInvalidChange(final String field, final ObjectChange change, final String message) {
		assertThatExceptionOfType(ConstraintViolationException.class)
			.isThrownBy(() -> service.handle(change))
			.satisfies(exception -> assertThat(exception.getConstraintViolations())
				.extracting(violation -> violation.getPropertyPath().toString(), ConstraintViolation::getMessage)
				.containsExactly(tuple("handle.change." + field, message)));

		verifyNoInteractions(eventRepositoryMock, assetRepositoryMock);
	}

	private static Stream<Arguments> invalidChanges() {
		final var change = assetChange(UPSERT, 1);
		final var partiesWithNull = new ArrayList<Party>();
		partiesWithNull.add(null);

		return Stream.of(
			Arguments.of("eventId", new ObjectChange("not-a-uuid", SOURCE, ASSET, UPSERT, SOURCE_ID, 1L, change.occurredAt(), MUNICIPALITY_ID, null, change.parties(), change.data()),
				"not a valid UUID"),
			Arguments.of("source", new ObjectChange(change.eventId(), " ", ASSET, UPSERT, SOURCE_ID, 1L, change.occurredAt(), MUNICIPALITY_ID, null, change.parties(), change.data()),
				"must not be blank"),
			Arguments.of("objectType", new ObjectChange(change.eventId(), SOURCE, null, UPSERT, SOURCE_ID, 1L, change.occurredAt(), MUNICIPALITY_ID, null, change.parties(), change.data()),
				"must not be null"),
			Arguments.of("operation", new ObjectChange(change.eventId(), SOURCE, ASSET, null, SOURCE_ID, 1L, change.occurredAt(), MUNICIPALITY_ID, null, change.parties(), change.data()),
				"must not be null"),
			Arguments.of("sourceId", new ObjectChange(change.eventId(), SOURCE, ASSET, UPSERT, null, 1L, change.occurredAt(), MUNICIPALITY_ID, null, change.parties(), change.data()),
				"must not be blank"),
			Arguments.of("version", new ObjectChange(change.eventId(), SOURCE, ASSET, UPSERT, SOURCE_ID, -1L, change.occurredAt(), MUNICIPALITY_ID, null, change.parties(), change.data()),
				"must be greater than or equal to 0"),
			Arguments.of("occurredAt", new ObjectChange(change.eventId(), SOURCE, ASSET, UPSERT, SOURCE_ID, 1L, null, MUNICIPALITY_ID, null, change.parties(), change.data()),
				"must not be null"),
			Arguments.of("municipalityId", new ObjectChange(change.eventId(), SOURCE, ASSET, UPSERT, SOURCE_ID, 1L, OffsetDateTime.now(), "not-a-municipality", null, change.parties(),
				change.data()), "not a valid municipality ID"),
			Arguments.of("parties[0].partyId", new ObjectChange(change.eventId(), SOURCE, ASSET, UPSERT, SOURCE_ID, 1L, change.occurredAt(), MUNICIPALITY_ID, null,
				List.of(new Party("not-a-uuid", "OWNER")), change.data()), "not a valid UUID"),
			Arguments.of("parties[0].<list element>", new ObjectChange(change.eventId(), SOURCE, ASSET, UPSERT, SOURCE_ID, 1L, change.occurredAt(), MUNICIPALITY_ID, null,
				partiesWithNull, change.data()), "must not be null"));
	}
}
