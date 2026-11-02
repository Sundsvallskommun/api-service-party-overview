package se.sundsvall.partyoverview.apptest;

import generated.se.sundsvall.partyassets.AssetEvent;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.jdbc.Sql;
import se.sundsvall.dept44.test.AbstractAppTest;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;
import se.sundsvall.partyoverview.Application;
import se.sundsvall.partyoverview.integration.db.AssetRepository;
import se.sundsvall.partyoverview.integration.db.EventRepository;
import se.sundsvall.partyoverview.integration.db.model.AssetEntity;
import se.sundsvall.partyoverview.integration.db.model.EventEntity;
import se.sundsvall.partyoverview.service.EventService;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.OK;
import static se.sundsvall.partyoverview.integration.source.partyassets.PartyAssetsTranslator.toObjectChange;

/**
 * Party overview integration tests. The tests that handle events read party-assets events from events.json and
 * translate them the way the listener does.
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
	private static final String MUNICIPALITY_ID = "2281";
	private static final String SOURCE = "PARTY_ASSETS";
	private static final String PARTY_ID = "d4c3b2a1-9f8e-4d7c-8b6a-5f4e3d2c1b0a";
	private static final String OTHER_PARTY_ID = "e5d4c3b2-a1f9-4e8d-9c7b-6a5f4e3d2c1b";
	private static final String ASSET_ID = "6e5d4c3b-2a19-4f8e-9d7c-6b5a4f3e2d1c";
	private static final String OTHER_ASSET_ID = "7f6e5d4c-3b2a-4190-8e7d-6c5b4a3f2e1d";
	private static final String DRAFT_ASSET_ID = "8a7f6e5d-4c3b-4a21-9f8e-7d6c5b4a3f2e";

	@Autowired
	private EventService eventService;

	@Autowired
	private EventRepository eventRepository;

	@Autowired
	private AssetRepository assetRepository;

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void test01_getOverview() {
		getOverview("81471222-5798-11e9-ae24-57fa13b361e1", RESPONSE_FILE);
	}

	@Test
	void test02_getOverviewForPartyWithoutAssets() {
		getOverview("f0000000-0000-0000-0000-000000000000", RESPONSE_FILE);
	}

	/**
	 * Two versions of an asset, the second with fields party-overview does not read, then a redelivery of the first.
	 * Another asset is created and deleted, and a draft is created. The overview shows the newest version of the first
	 * asset. Its history holds each version once, the deleted asset is a tombstone without history, and the draft left
	 * no trace.
	 */
	@Test
	void test03_handleEventsAndGetOverview() throws IOException {
		handleEvents("test03_handleEventsAndGetOverview");

		getOverview(PARTY_ID, RESPONSE_FILE);
		assertThat(eventRepository.findAll())
			.extracting(EventEntity::getSourceId, EventEntity::getVersion, EventEntity::getEventId)
			.containsExactlyInAnyOrder(
				tuple(ASSET_ID, 1L, "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c01"),
				tuple(ASSET_ID, 2L, "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c02"));
		assertThat(findAsset(OTHER_ASSET_ID)).hasValueSatisfying(asset -> {
			assertThat(asset.getDeleted()).isTrue();
			assertThat(asset.getTitle()).isNull();
			assertThat(asset.getParties()).isEmpty();
		});
		assertThat(findAsset(DRAFT_ASSET_ID)).isEmpty();
	}

	/**
	 * Version 2, then version 1 with a message id of its own. Version 1 goes to the history, but the overview keeps
	 * version 2.
	 */
	@Test
	void test04_olderVersionIsKeptAsHistoryOnly() throws IOException {
		handleEvents("test04_olderVersionIsKeptAsHistoryOnly");

		getOverview(PARTY_ID, RESPONSE_FILE);
		assertThat(eventRepository.findAll())
			.extracting(EventEntity::getVersion)
			.containsExactlyInAnyOrder(1L, 2L);
	}

	/**
	 * Created, deleted, then updated. The update comes after the deletion and is skipped, so nothing about the asset is
	 * left but the tombstone.
	 */
	@Test
	void test05_eventAfterDeleteIsSkipped() throws IOException {
		handleEvents("test05_eventAfterDeleteIsSkipped");

		getOverview(PARTY_ID, RESPONSE_FILE);
		assertThat(eventRepository.findAll()).isEmpty();
		assertThat(findAsset(ASSET_ID)).hasValueSatisfying(asset -> assertThat(asset.getDeleted()).isTrue());
	}

	/**
	 * The deletion arrives before the creation. The creation is skipped, since the tombstone is already there.
	 */
	@Test
	void test06_deleteBeforeCreate() throws IOException {
		handleEvents("test06_deleteBeforeCreate");

		getOverview(PARTY_ID, RESPONSE_FILE);
		assertThat(eventRepository.findAll()).isEmpty();
		assertThat(findAsset(ASSET_ID)).hasValueSatisfying(asset -> {
			assertThat(asset.getDeleted()).isTrue();
			assertThat(asset.getVersion()).isEqualTo(2L);
		});
	}

	/**
	 * The asset moves from one party to another. The first party loses it from its overview, the second gets it.
	 */
	@Test
	void test07_assetMovesToAnotherParty() throws IOException {
		handleEvents("test07_assetMovesToAnotherParty");

		getOverview(PARTY_ID, "response-first-party.json");
		getOverview(OTHER_PARTY_ID, "response-second-party.json");
	}

	/**
	 * Version 1 arrives with a wrong title, then again with the right one and a message id of its own, as a republish
	 * after a translator bug would. Both the overview and the history hold the corrected version, once.
	 */
	@Test
	void test08_republishOfTheSameVersionCorrectsIt() throws IOException {
		handleEvents("test08_republishOfTheSameVersionCorrectsIt");

		getOverview(PARTY_ID, RESPONSE_FILE);
		assertThat(eventRepository.findAll()).singleElement().satisfies(event -> {
			assertThat(event.getEventId()).isEqualTo("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c82");
			assertThat(event.getPayload()).contains("Serveringstillstånd").doesNotContain("Serveringstilstånd");
		});
	}

	private void handleEvents(final String testName) throws IOException {
		final var file = new ClassPathResource("PartyOverviewIT/__files/%s/events.json".formatted(testName));
		for (final var delivery : jsonMapper.readValue(file.getInputStream(), Delivery[].class)) {
			toObjectChange(delivery.messageId(), delivery.event()).ifPresent(eventService::handle);
		}
	}

	private void getOverview(final String partyId, final String responseFile) {
		setupCall()
			.withHttpMethod(GET)
			.withServicePath(PATH.formatted(partyId))
			.withExpectedResponseStatus(OK)
			.withExpectedResponse(responseFile)
			.sendRequestAndVerifyResponse();
	}

	private Optional<AssetEntity> findAsset(final String sourceId) {
		return assetRepository.findByMunicipalityIdAndSourceAndSourceId(MUNICIPALITY_ID, SOURCE, sourceId);
	}

	/**
	 * A message as the listener receives it: the AMQP message id and the payload.
	 */
	record Delivery(String messageId, AssetEvent event) {
	}
}
