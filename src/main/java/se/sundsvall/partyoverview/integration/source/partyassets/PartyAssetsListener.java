package se.sundsvall.partyoverview.integration.source.partyassets;

import generated.se.sundsvall.partyassets.AssetEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import se.sundsvall.partyoverview.service.EventService;

import static org.springframework.amqp.support.AmqpHeaders.MESSAGE_ID;
import static se.sundsvall.partyoverview.integration.source.partyassets.PartyAssetsTranslator.toObjectChange;

/**
 * Consumes the asset events party-assets publishes. Retries and dead-lettering are left to dept44-starter-rabbitmq.
 * <p>
 * The condition stands in for the starter, which starts no listener while dept44.rabbitmq.enabled is not true. Remove
 * it when the starter replaces spring-boot-starter-amqp.
 */
@Component
@ConditionalOnProperty(name = "dept44.rabbitmq.enabled", havingValue = "true")
class PartyAssetsListener {

	private final EventService eventService;

	PartyAssetsListener(final EventService eventService) {
		this.eventService = eventService;
	}

	@RabbitListener(queues = "${integration.party-assets.queue}")
	void receive(@Payload final AssetEvent event, @Header(MESSAGE_ID) final String messageId) {
		eventService.handle(toObjectChange(messageId, event));
	}
}
