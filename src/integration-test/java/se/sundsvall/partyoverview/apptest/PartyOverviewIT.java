package se.sundsvall.partyoverview.apptest;

import generated.se.sundsvall.partyassets.AssetEvent;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.jdbc.Sql;
import se.sundsvall.dept44.test.AbstractAppTest;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;
import se.sundsvall.partyoverview.Application;
import se.sundsvall.partyoverview.integration.db.EventRepository;
import se.sundsvall.partyoverview.integration.db.model.EventEntity;
import se.sundsvall.partyoverview.service.EventService;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.OK;
import static se.sundsvall.partyoverview.integration.db.model.Operation.DELETE;
import static se.sundsvall.partyoverview.integration.db.model.Operation.UPSERT;
import static se.sundsvall.partyoverview.integration.source.partyassets.PartyAssetsTranslator.toObjectChange;

/**
 * Party overview integration tests.
 *
 * @see src/integration-test/resources/db/scripts/partyOverviewIT.sql for data setup.
 */
@WireMockAppTestSuite(files = "classpath:/PartyOverviewIT/", classes = Application.class)
@Sql(scripts = {
	"/db/scripts/truncate.sql",
	"/db/scripts/partyOverviewIT.sql"
})
class PartyOverviewIT extends AbstractAppTest {

	private static final String RESPONSE_FILE = "response.json";
	private static final String PATH = "/2281/parties/%s/overview";

	@Autowired
	private EventService eventService;

	@Autowired
	private EventRepository eventRepository;

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void test01_getOverview() {
		setupCall()
			.withHttpMethod(GET)
			.withServicePath(PATH.formatted("81471222-5798-11e9-ae24-57fa13b361e1"))
			.withExpectedResponseStatus(OK)
			.withExpectedResponse(RESPONSE_FILE)
			.sendRequestAndVerifyResponse();
	}

	@Test
	void test02_getOverviewForPartyWithoutAssets() {
		setupCall()
			.withHttpMethod(GET)
			.withServicePath(PATH.formatted("f0000000-0000-0000-0000-000000000000"))
			.withExpectedResponseStatus(OK)
			.withExpectedResponse(RESPONSE_FILE)
			.sendRequestAndVerifyResponse();
	}

	/**
	 * party-assets events, read and translated as the listener does: two versions of one asset, the second with fields
	 * party-overview does not read, a redelivery of the first, and an asset that is created and deleted. The overview
	 * shows the newest version of the first asset, while the history keeps both of its versions and only a tombstone of
	 * the second.
	 */
	@Test
	void test03_handleEventsAndGetOverview() throws IOException {
		final var deliveries = jsonMapper.readValue(new ClassPathResource("PartyOverviewIT/__files/test03_handleEventsAndGetOverview/events.json").getInputStream(), Delivery[].class);
		for (final var delivery : deliveries) {
			eventService.handle(toObjectChange(delivery.messageId(), delivery.event()));
		}

		setupCall()
			.withHttpMethod(GET)
			.withServicePath(PATH.formatted("d4c3b2a1-9f8e-4d7c-8b6a-5f4e3d2c1b0a"))
			.withExpectedResponseStatus(OK)
			.withExpectedResponse(RESPONSE_FILE)
			.sendRequestAndVerifyResponse();

		assertThat(eventRepository.findAll())
			.extracting(EventEntity::getSourceId, EventEntity::getOperation, EventEntity::getVersion, entity -> entity.getPayload() == null)
			.containsExactlyInAnyOrder(
				tuple("6e5d4c3b-2a19-4f8e-9d7c-6b5a4f3e2d1c", UPSERT, 1L, false),
				tuple("6e5d4c3b-2a19-4f8e-9d7c-6b5a4f3e2d1c", UPSERT, 2L, false),
				tuple("7f6e5d4c-3b2a-4190-8e7d-6c5b4a3f2e1d", DELETE, 2L, true));
	}

	/**
	 * A message as the listener receives it: the AMQP message id and the payload.
	 */
	record Delivery(String messageId, AssetEvent event) {
	}
}
