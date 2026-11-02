package se.sundsvall.partyoverview.integration.db.model;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanConstructor;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanEquals;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanHashCode;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanToString;
import static com.google.code.beanmatchers.BeanMatchers.hasValidGettersAndSetters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.allOf;

class PartyEmbeddableTest {

	@Test
	void testBean() {
		MatcherAssert.assertThat(PartyEmbeddable.class, allOf(
			hasValidBeanConstructor(),
			hasValidGettersAndSetters(),
			hasValidBeanHashCode(),
			hasValidBeanEquals(),
			hasValidBeanToString()));
	}

	@Test
	void testBuilderMethods() {
		final var partyId = "81471222-5798-11e9-ae24-57fa13b361e1";
		final var role = "APPLICANT";

		final var result = PartyEmbeddable.create()
			.withPartyId(partyId)
			.withRole(role);

		assertThat(result).hasNoNullFieldsOrProperties();
		assertThat(result.getPartyId()).isEqualTo(partyId);
		assertThat(result.getRole()).isEqualTo(role);
	}

	@Test
	void testNoDirtOnCreatedBean() {
		assertThat(PartyEmbeddable.create()).hasAllNullFieldsOrProperties();
		assertThat(new PartyEmbeddable()).hasAllNullFieldsOrProperties();
	}
}
