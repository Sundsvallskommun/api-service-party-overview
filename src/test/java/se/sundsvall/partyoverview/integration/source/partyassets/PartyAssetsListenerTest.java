package se.sundsvall.partyoverview.integration.source.partyassets;

import generated.se.sundsvall.partyassets.AssetEvent.EventTypeEnum;
import java.sql.SQLException;
import java.util.stream.Stream;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import se.sundsvall.partyoverview.service.EventService;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static se.sundsvall.partyoverview.TestFactory.assetEvent;
import static se.sundsvall.partyoverview.integration.source.partyassets.PartyAssetsTranslator.toObjectChange;

@ExtendWith(MockitoExtension.class)
class PartyAssetsListenerTest {

	private static final String MESSAGE_ID = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c01";

	@Mock
	private EventService eventServiceMock;

	@InjectMocks
	private PartyAssetsListener listener;

	@Test
	void receive() {
		final var event = assetEvent(EventTypeEnum.UPDATED, 1);

		listener.receive(event, MESSAGE_ID);

		verify(eventServiceMock).handle(toObjectChange(MESSAGE_ID, event).orElseThrow());
		verifyNoMoreInteractions(eventServiceMock);
	}

	@Test
	void receiveDraft() {
		final var event = assetEvent(EventTypeEnum.CREATED, 1);
		event.getAsset().setStatus("DRAFT");

		listener.receive(event, MESSAGE_ID);

		verifyNoInteractions(eventServiceMock);
	}

	@ParameterizedTest
	@MethodSource("collisions")
	void receiveRequeuesACollision(final RuntimeException collision) {
		final var event = assetEvent(EventTypeEnum.UPDATED, 1);
		doThrow(collision).when(eventServiceMock).handle(any());

		assertThatExceptionOfType(ImmediateRequeueAmqpException.class)
			.isThrownBy(() -> listener.receive(event, MESSAGE_ID))
			.withMessage("Event %s collided with a concurrent change of the same object".formatted(MESSAGE_ID))
			.withCause(collision);
	}

	@ParameterizedTest
	@MethodSource("otherFailures")
	void receiveLeavesAnyOtherFailureToTheStarter(final RuntimeException failure) {
		final var event = assetEvent(EventTypeEnum.UPDATED, 1);
		doThrow(failure).when(eventServiceMock).handle(any());

		assertThatExceptionOfType(RuntimeException.class)
			.isThrownBy(() -> listener.receive(event, MESSAGE_ID))
			.isSameAs(failure);
	}

	private static Stream<RuntimeException> collisions() {
		return Stream.of(
			new OptimisticLockingFailureException("Row was updated by another transaction"),
			integrityViolation(ConstraintKind.UNIQUE));
	}

	private static Stream<RuntimeException> otherFailures() {
		return Stream.of(
			integrityViolation(ConstraintKind.NOT_NULL),
			new DataIntegrityViolationException("Data too long for column 'title'"),
			new IllegalStateException("Something else"));
	}

	private static DataIntegrityViolationException integrityViolation(final ConstraintKind kind) {
		return new DataIntegrityViolationException("could not execute statement",
			new ConstraintViolationException("could not execute statement", new SQLException("violation"), "insert into asset", kind, "a_constraint"));
	}
}
