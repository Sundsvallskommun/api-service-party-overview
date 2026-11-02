package se.sundsvall.partyoverview.integration.db.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanConstructor;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanEquals;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanHashCode;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanToString;
import static com.google.code.beanmatchers.BeanMatchers.hasValidGettersAndSetters;
import static com.google.code.beanmatchers.BeanMatchers.registerValueGenerator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.allOf;

class AssetEntityTest {

	@BeforeAll
	static void setup() {
		registerValueGenerator(() -> LocalDate.now().plusDays(new Random().nextInt()), LocalDate.class);
	}

	@Test
	void testBean() {
		MatcherAssert.assertThat(AssetEntity.class, allOf(
			hasValidBeanConstructor(),
			hasValidGettersAndSetters(),
			hasValidBeanHashCode(),
			hasValidBeanEquals(),
			hasValidBeanToString()));
	}

	@Test
	void testBuilderMethods() {
		final var id = "id";
		final var municipalityId = "2281";
		final var source = "PARTY_ASSETS";
		final var sourceId = "1c8f38a6-b492-4037-b7dc-de5bc6c629f0";
		final var version = 3L;
		final var lockVersion = 1L;
		final var deleted = false;
		final var authority = "authority";
		final var parties = List.of(PartyEmbeddable.create().withPartyId("81471222-5798-11e9-ae24-57fa13b361e1"));
		final var assetId = "PRH-123456789";
		final var origin = "CASEDATA";
		final var type = "PERMIT";
		final var title = "title";
		final var description = "description";
		final var status = "ACTIVE";
		final var statusReason = "statusReason";
		final var issued = LocalDate.now();
		final var validTo = LocalDate.now().plusYears(1);

		final var result = AssetEntity.create()
			.withId(id)
			.withMunicipalityId(municipalityId)
			.withSource(source)
			.withSourceId(sourceId)
			.withVersion(version)
			.withLockVersion(lockVersion)
			.withDeleted(deleted)
			.withAuthority(authority)
			.withParties(parties)
			.withAssetId(assetId)
			.withOrigin(origin)
			.withType(type)
			.withTitle(title)
			.withDescription(description)
			.withStatus(status)
			.withStatusReason(statusReason)
			.withIssued(issued)
			.withValidTo(validTo);

		assertThat(result).hasNoNullFieldsOrProperties();
		assertThat(result.getId()).isEqualTo(id);
		assertThat(result.getMunicipalityId()).isEqualTo(municipalityId);
		assertThat(result.getSource()).isEqualTo(source);
		assertThat(result.getSourceId()).isEqualTo(sourceId);
		assertThat(result.getVersion()).isEqualTo(version);
		assertThat(result.getLockVersion()).isEqualTo(lockVersion);
		assertThat(result.getDeleted()).isEqualTo(deleted);
		assertThat(result.getAuthority()).isEqualTo(authority);
		assertThat(result.getParties()).isEqualTo(parties);
		assertThat(result.getAssetId()).isEqualTo(assetId);
		assertThat(result.getOrigin()).isEqualTo(origin);
		assertThat(result.getType()).isEqualTo(type);
		assertThat(result.getTitle()).isEqualTo(title);
		assertThat(result.getDescription()).isEqualTo(description);
		assertThat(result.getStatus()).isEqualTo(status);
		assertThat(result.getStatusReason()).isEqualTo(statusReason);
		assertThat(result.getIssued()).isEqualTo(issued);
		assertThat(result.getValidTo()).isEqualTo(validTo);
	}

	@Test
	void testNoDirtOnCreatedBean() {
		assertThat(AssetEntity.create()).hasAllNullFieldsOrProperties();
		assertThat(new AssetEntity()).hasAllNullFieldsOrProperties();
	}
}
