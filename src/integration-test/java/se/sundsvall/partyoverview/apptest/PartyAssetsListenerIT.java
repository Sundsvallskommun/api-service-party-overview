package se.sundsvall.partyoverview.apptest;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import se.sundsvall.dept44.test.AbstractAppTest;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;
import se.sundsvall.partyoverview.Application;
import se.sundsvall.partyoverview.integration.db.AssetRepository;
import se.sundsvall.partyoverview.integration.db.EventRepository;
import se.sundsvall.partyoverview.integration.db.model.AssetEntity;
import se.sundsvall.partyoverview.integration.db.model.EventEntity;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * The party-assets listener against a real broker, with the queue declared the way the topology declares it. The
 * messages in messages/ are what party-assets publishes: keep them in step with the expected events in party-assets'
 * AssetEventsIT.
 */
@WireMockAppTestSuite(files = "classpath:/PartyAssetsListenerIT/", classes = Application.class)
@Sql(scripts = {
	"/db/scripts/truncate.sql"
})
class PartyAssetsListenerIT extends AbstractAppTest {

	// The version in TEST and production
	private static final RabbitMQContainer BROKER = new RabbitMQContainer("rabbitmq:4.3.6-management-alpine");

	private static final String EXCHANGE = "api-fabriken.party-assets";
	private static final String QUEUE = "api-fabriken.party-overview.party-assets";
	private static final String DEAD_LETTER_QUEUE = QUEUE + ".dlq";
	private static final String MESSAGES = "PartyAssetsListenerIT/__files/messages/";
	private static final String MUNICIPALITY_ID = "2281";
	private static final String SOURCE = "PARTY_ASSETS";
	private static final String ASSET_ID = "3f2c8a51-6d4e-4b7a-9c1f-2e8d5b6a7c90";
	private static final String OVERVIEW_PATH = "/2281/parties/a6c380f3-6d26-496d-93fe-10b1e0160354/overview";
	private static final Duration TIMEOUT = Duration.ofSeconds(15);

	private static final RabbitAdmin ADMIN;
	private static final RabbitTemplate PUBLISHER;

	static {
		BROKER.start();

		// The service never declares anything, so the test does it as rabbitmq-topology would
		final var connectionFactory = new CachingConnectionFactory(BROKER.getHost(), BROKER.getAmqpPort());
		connectionFactory.setUsername(BROKER.getAdminUsername());
		connectionFactory.setPassword(BROKER.getAdminPassword());
		ADMIN = new RabbitAdmin(connectionFactory);
		final var exchange = new TopicExchange(EXCHANGE);
		final var queue = QueueBuilder.durable(QUEUE).quorum()
			.singleActiveConsumer()
			.deliveryLimit(5)
			.deadLetterExchange("")
			.deadLetterRoutingKey(DEAD_LETTER_QUEUE)
			.build();
		ADMIN.declareExchange(exchange);
		ADMIN.declareQueue(queue);
		ADMIN.declareQueue(QueueBuilder.durable(DEAD_LETTER_QUEUE).quorum().build());
		ADMIN.declareBinding(BindingBuilder.bind(queue).to(exchange).with("asset.*"));
		PUBLISHER = new RabbitTemplate(connectionFactory);
	}

	@Autowired
	private AssetRepository assetRepository;

	@Autowired
	private EventRepository eventRepository;

	@DynamicPropertySource
	static void broker(final DynamicPropertyRegistry registry) {
		registry.add("dept44.rabbitmq.enabled", () -> true);
		registry.add("spring.rabbitmq.host", BROKER::getHost);
		registry.add("spring.rabbitmq.port", BROKER::getAmqpPort);
		registry.add("spring.rabbitmq.username", BROKER::getAdminUsername);
		registry.add("spring.rabbitmq.password", BROKER::getAdminPassword);
	}

	@BeforeEach
	void purgeDeadLetters() {
		ADMIN.purgeQueue(DEAD_LETTER_QUEUE);
	}

	@Test
	void test01_createdAsset() throws IOException {
		publish("asset.created", "created.json", "6b1d0c1e-2f3a-4b5c-8d6e-7f8091a2b3c4");

		await().atMost(TIMEOUT).until(() -> asset().isPresent());

		getOverview();
		assertThat(eventRepository.findAll()).singleElement().satisfies(event -> {
			assertThat(event.getEventId()).isEqualTo("6b1d0c1e-2f3a-4b5c-8d6e-7f8091a2b3c4");
			assertThat(event.getVersion()).isZero();
		});
	}

	@Test
	void test02_redeliveryAndUpdate() throws IOException {
		publish("asset.created", "created.json", "6b1d0c1e-2f3a-4b5c-8d6e-7f8091a2b3c4");
		publish("asset.created", "created.json", "6b1d0c1e-2f3a-4b5c-8d6e-7f8091a2b3c4");
		publish("asset.updated", "updated.json", "7c2e1d2f-3a4b-4c5d-9e6f-8091a2b3c4d5");

		// One consumer takes the messages in order, so the update is handled last
		await().atMost(TIMEOUT).until(() -> asset().map(AssetEntity::getVersion).filter(version -> version == 1).isPresent());

		getOverview();
		assertThat(eventRepository.findAll()).extracting(EventEntity::getVersion).containsExactlyInAnyOrder(0L, 1L);
	}

	@Test
	void test03_deletedAsset() throws IOException {
		publish("asset.created", "created.json", "6b1d0c1e-2f3a-4b5c-8d6e-7f8091a2b3c4");
		publish("asset.updated", "updated.json", "7c2e1d2f-3a4b-4c5d-9e6f-8091a2b3c4d5");
		publish("asset.deleted", "deleted.json", "8d3f2e3a-4b5c-4d6e-8f70-91a2b3c4d5e6");

		await().atMost(TIMEOUT).until(() -> asset().map(AssetEntity::getDeleted).filter(Boolean.TRUE::equals).isPresent());

		getOverview();
		assertThat(eventRepository.findAll()).isEmpty();
	}

	@Test
	void test04_unusableMessagesAreDeadLettered() throws IOException {
		publish("asset.created", "without-party.json", "9e403f4b-5c6d-4e7f-9081-a2b3c4d5e6f7");
		PUBLISHER.send(EXCHANGE, "asset.created", new Message("not json".getBytes(UTF_8), MessagePropertiesBuilder.newInstance()
			.setContentType(APPLICATION_JSON_VALUE)
			.setMessageId("a0514a5c-6d7e-4f80-8192-b3c4d5e6f708")
			.build()));

		await().atMost(TIMEOUT).until(() -> ADMIN.getQueueInfo(DEAD_LETTER_QUEUE).getMessageCount() == 2);

		assertThat(assetRepository.findAll()).isEmpty();
		assertThat(eventRepository.findAll()).isEmpty();
	}

	private static void publish(final String routingKey, final String file, final String messageId) throws IOException {
		final var body = new ClassPathResource(MESSAGES + file).getContentAsByteArray();
		PUBLISHER.send(EXCHANGE, routingKey, new Message(body, MessagePropertiesBuilder.newInstance()
			.setContentType(APPLICATION_JSON_VALUE)
			.setMessageId(messageId)
			.build()));
	}

	private Optional<AssetEntity> asset() {
		return assetRepository.findByMunicipalityIdAndSourceAndSourceId(MUNICIPALITY_ID, SOURCE, ASSET_ID);
	}

	private void getOverview() {
		setupCall()
			.withHttpMethod(GET)
			.withServicePath(OVERVIEW_PATH)
			.withExpectedResponseStatus(OK)
			.withExpectedResponse("response.json")
			.sendRequestAndVerifyResponse();
	}
}
