package se.sundsvall.partyoverview.integration.source.partyassets;

import generated.se.sundsvall.partyassets.AssetEvent;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import se.sundsvall.partyoverview.service.EventService;
import se.sundsvall.partyoverview.service.model.ObjectChange;

import static org.springframework.amqp.support.AmqpHeaders.MESSAGE_ID;
import static se.sundsvall.partyoverview.integration.source.Collisions.isCollision;
import static se.sundsvall.partyoverview.integration.source.partyassets.PartyAssetsTranslator.toObjectChange;

/**
 * Consumes the asset events party-assets publishes. A collision with a concurrent change of the same asset is requeued
 * at once. Any other failure is left to dept44-starter-rabbitmq to retry or dead-letter, since a lasting failure that
 * is requeued at once would loop.
 * <p>
 * The condition stands in for the starter, which starts no listener while dept44.rabbitmq.enabled is not true. Remove
 * it when the starter replaces spring-boot-starter-amqp.
 */
@Component
@ConditionalOnProperty(name = "dept44.rabbitmq.enabled", havingValue = "true")
class PartyAssetsListener {

	private static final String COLLISION = "Event %s collided with a concurrent change of the same object";

	private final EventService eventService;

	PartyAssetsListener(final EventService eventService) {
		this.eventService = eventService;
	}

	@RabbitListener(queues = "${integration.party-assets.queue}")
	void receive(@Payload final AssetEvent event, @Header(MESSAGE_ID) final String messageId) {
		toObjectChange(messageId, event).ifPresent(this::handle);
	}

	private void handle(final ObjectChange change) {
		try {
			eventService.handle(change);
		} catch (final RuntimeException e) {
			if (isCollision(e)) {
				throw new ImmediateRequeueAmqpException(COLLISION.formatted(change.eventId()), e);
			}
			throw e;
		}
	}
}
