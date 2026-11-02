package se.sundsvall.partyoverview.api;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import se.sundsvall.partyoverview.Application;
import se.sundsvall.partyoverview.api.model.Asset;
import se.sundsvall.partyoverview.api.model.PartyOverview;
import se.sundsvall.partyoverview.service.PartyOverviewService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static se.sundsvall.partyoverview.TestFactory.MUNICIPALITY_ID;
import static se.sundsvall.partyoverview.TestFactory.PARTY_ID;

@ActiveProfiles("junit")
@SpringBootTest(classes = Application.class, webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
class PartyOverviewResourceTest {

	private static final String PATH = "/{municipalityId}/parties/{partyId}/overview";

	@MockitoBean
	private PartyOverviewService serviceMock;

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void getOverview() {
		final var overview = PartyOverview.create().withAssets(List.of(Asset.create().withTitle("title")));
		when(serviceMock.getOverview(MUNICIPALITY_ID, PARTY_ID)).thenReturn(overview);

		final var response = webTestClient.get()
			.uri(PATH, MUNICIPALITY_ID, PARTY_ID)
			.exchange()
			.expectStatus().isOk()
			.expectHeader().contentType(APPLICATION_JSON)
			.expectBody(PartyOverview.class)
			.returnResult()
			.getResponseBody();

		assertThat(response).isEqualTo(overview);
		verify(serviceMock).getOverview(MUNICIPALITY_ID, PARTY_ID);
		verifyNoMoreInteractions(serviceMock);
	}
}
