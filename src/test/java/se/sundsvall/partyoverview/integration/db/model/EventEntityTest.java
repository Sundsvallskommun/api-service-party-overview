package se.sundsvall.partyoverview.integration.db.model;

import java.time.OffsetDateTime;
import java.util.Random;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanConstructor;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanEqualsExcluding;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanHashCodeExcluding;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanToString;
import static com.google.code.beanmatchers.BeanMatchers.hasValidGettersAndSetters;
import static com.google.code.beanmatchers.BeanMatchers.registerValueGenerator;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.CoreMatchers.allOf;
import static se.sundsvall.partyoverview.integration.db.model.ObjectType.ASSET;
import static se.sundsvall.partyoverview.integration.db.model.Operation.UPSERT;

class EventEntityTest {

	@BeforeAll
	static void setup() {
		registerValueGenerator(() -> OffsetDateTime.now().plusDays(new Random().nextInt()), OffsetDateTime.class);
	}

	@Test
	void testBean() {
		// ObjectType has a single constant, so BeanMatchers cannot give objectType two distinct values
		MatcherAssert.assertThat(EventEntity.class, allOf(
			hasValidBeanConstructor(),
			hasValidGettersAndSetters(),
			hasValidBeanHashCodeExcluding("objectType"),
			hasValidBeanEqualsExcluding("objectType"),
			hasValidBeanToString()));
	}

	@Test
	void testBuilderMethods() {
		final var id = "id";
		final var eventId = "b82bd8ac-1507-4d9a-958d-369261eecc15";
		final var municipalityId = "2281";
		final var source = "PARTY_ASSETS";
		final var sourceId = "1c8f38a6-b492-4037-b7dc-de5bc6c629f0";
		final var version = 3L;
		final var occurredAt = OffsetDateTime.now().minusMinutes(1);
		final var receivedAt = OffsetDateTime.now();
		final var authority = "authority";
		final var payload = "{}";

		final var result = EventEntity.create()
			.withId(id)
			.withEventId(eventId)
			.withMunicipalityId(municipalityId)
			.withSource(source)
			.withObjectType(ASSET)
			.withSourceId(sourceId)
			.withOperation(UPSERT)
			.withVersion(version)
			.withOccurredAt(occurredAt)
			.withReceivedAt(receivedAt)
			.withAuthority(authority)
			.withPayload(payload);

		assertThat(result).hasNoNullFieldsOrProperties();
		assertThat(result.getId()).isEqualTo(id);
		assertThat(result.getEventId()).isEqualTo(eventId);
		assertThat(result.getMunicipalityId()).isEqualTo(municipalityId);
		assertThat(result.getSource()).isEqualTo(source);
		assertThat(result.getObjectType()).isEqualTo(ASSET);
		assertThat(result.getSourceId()).isEqualTo(sourceId);
		assertThat(result.getOperation()).isEqualTo(UPSERT);
		assertThat(result.getVersion()).isEqualTo(version);
		assertThat(result.getOccurredAt()).isEqualTo(occurredAt);
		assertThat(result.getReceivedAt()).isEqualTo(receivedAt);
		assertThat(result.getAuthority()).isEqualTo(authority);
		assertThat(result.getPayload()).isEqualTo(payload);
	}

	@Test
	void prePersistSetsReceivedAt() {
		final var entity = EventEntity.create();

		entity.prePersist();

		assertThat(entity.getReceivedAt()).isCloseTo(OffsetDateTime.now(), within(1, SECONDS));
	}

	@Test
	void testNoDirtOnCreatedBean() {
		assertThat(EventEntity.create()).hasAllNullFieldsOrProperties();
		assertThat(new EventEntity()).hasAllNullFieldsOrProperties();
	}
}
