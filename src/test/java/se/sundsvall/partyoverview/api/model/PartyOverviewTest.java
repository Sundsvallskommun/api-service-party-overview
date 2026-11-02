package se.sundsvall.partyoverview.api.model;

import java.util.List;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanConstructor;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanEquals;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanHashCode;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanToString;
import static com.google.code.beanmatchers.BeanMatchers.hasValidGettersAndSetters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.allOf;

class PartyOverviewTest {

	@Test
	void testBean() {
		MatcherAssert.assertThat(PartyOverview.class, allOf(
			hasValidBeanConstructor(),
			hasValidGettersAndSetters(),
			hasValidBeanHashCode(),
			hasValidBeanEquals(),
			hasValidBeanToString()));
	}

	@Test
	void testBuilderMethods() {
		final var assets = List.of(Asset.create().withTitle("title"));

		final var result = PartyOverview.create()
			.withAssets(assets);

		assertThat(result).hasNoNullFieldsOrProperties();
		assertThat(result.getAssets()).isEqualTo(assets);
	}

	@Test
	void testNoDirtOnCreatedBean() {
		assertThat(PartyOverview.create()).hasAllNullFieldsOrProperties();
		assertThat(new PartyOverview()).hasAllNullFieldsOrProperties();
	}
}
