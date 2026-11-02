package se.sundsvall.partyoverview.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblem;
import se.sundsvall.dept44.problem.violations.Violation;
import se.sundsvall.partyoverview.Application;
import se.sundsvall.partyoverview.service.PartyOverviewService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static se.sundsvall.partyoverview.TestFactory.MUNICIPALITY_ID;
import static se.sundsvall.partyoverview.TestFactory.PARTY_ID;

@ActiveProfiles("junit")
@SpringBootTest(classes = Application.class, webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
class PartyOverviewResourceFailureTest {

	private static final String PATH = "/{municipalityId}/parties/{partyId}/overview";
	private static final String INVALID_MUNICIPALITY_ID = "invalid-municipality-id";
	private static final String INVALID_UUID = "not-a-valid-uuid";

	@MockitoBean
	private PartyOverviewService serviceMock;

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void getOverviewWithInvalidMunicipalityId() {
		final var response = webTestClient.get()
			.uri(PATH, INVALID_MUNICIPALITY_ID, PARTY_ID)
			.exchange()
			.expectStatus().isEqualTo(BAD_REQUEST)
			.expectHeader().contentType(APPLICATION_PROBLEM_JSON)
			.expectBody(ConstraintViolationProblem.class)
			.returnResult()
			.getResponseBody();

		assertThat(response).isNotNull();
		assertThat(response.getViolations())
			.extracting(Violation::field, Violation::message)
			.containsExactly(tuple("getOverview.municipalityId", "not a valid municipality ID"));
		verifyNoInteractions(serviceMock);
	}

	@Test
	void getOverviewWithInvalidPartyId() {
		final var response = webTestClient.get()
			.uri(PATH, MUNICIPALITY_ID, INVALID_UUID)
			.exchange()
			.expectStatus().isEqualTo(BAD_REQUEST)
			.expectHeader().contentType(APPLICATION_PROBLEM_JSON)
			.expectBody(ConstraintViolationProblem.class)
			.returnResult()
			.getResponseBody();

		assertThat(response).isNotNull();
		assertThat(response.getViolations())
			.extracting(Violation::field, Violation::message)
			.containsExactly(tuple("getOverview.partyId", "not a valid UUID"));
		verifyNoInteractions(serviceMock);
	}
}
