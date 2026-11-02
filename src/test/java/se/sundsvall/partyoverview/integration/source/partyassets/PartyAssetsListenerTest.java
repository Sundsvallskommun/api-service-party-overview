package se.sundsvall.partyoverview.integration.source.partyassets;

import generated.se.sundsvall.partyassets.AssetEvent.EventTypeEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.partyoverview.service.EventService;

import static org.mockito.Mockito.verify;
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

		verify(eventServiceMock).handle(toObjectChange(MESSAGE_ID, event));
		verifyNoMoreInteractions(eventServiceMock);
	}
}
