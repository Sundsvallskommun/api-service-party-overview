package se.sundsvall.partyoverview.api.model;

import java.time.LocalDate;
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

class AssetTest {

	@BeforeAll
	static void setup() {
		registerValueGenerator(() -> LocalDate.now().plusDays(new Random().nextInt()), LocalDate.class);
	}

	@Test
	void testBean() {
		MatcherAssert.assertThat(Asset.class, allOf(
			hasValidBeanConstructor(),
			hasValidGettersAndSetters(),
			hasValidBeanHashCode(),
			hasValidBeanEquals(),
			hasValidBeanToString()));
	}

	@Test
	void testBuilderMethods() {
		final var source = "PARTY_ASSETS";
		final var sourceId = "1c8f38a6-b492-4037-b7dc-de5bc6c629f0";
		final var assetId = "PRH-123456789";
		final var origin = "CASEDATA";
		final var type = "PERMIT";
		final var title = "title";
		final var description = "description";
		final var status = "ACTIVE";
		final var statusReason = "statusReason";
		final var issued = LocalDate.now();
		final var validTo = LocalDate.now().plusYears(1);

		final var result = Asset.create()
			.withSource(source)
			.withSourceId(sourceId)
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
		assertThat(result.getSource()).isEqualTo(source);
		assertThat(result.getSourceId()).isEqualTo(sourceId);
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
		assertThat(Asset.create()).hasAllNullFieldsOrProperties();
		assertThat(new Asset()).hasAllNullFieldsOrProperties();
	}
}
