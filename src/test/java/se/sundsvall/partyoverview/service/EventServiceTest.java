package se.sundsvall.partyoverview.service;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.dept44.problem.ThrowableProblem;
import se.sundsvall.partyoverview.integration.db.AssetRepository;
import se.sundsvall.partyoverview.integration.db.EventRepository;
import se.sundsvall.partyoverview.integration.db.model.AssetEntity;
import se.sundsvall.partyoverview.integration.db.model.EventEntity;
import se.sundsvall.partyoverview.service.model.ObjectChange;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static se.sundsvall.partyoverview.TestFactory.MUNICIPALITY_ID;
import static se.sundsvall.partyoverview.TestFactory.PARTY_ID;
import static se.sundsvall.partyoverview.TestFactory.SOURCE;
import static se.sundsvall.partyoverview.TestFactory.SOURCE_ID;
import static se.sundsvall.partyoverview.TestFactory.assetChange;
import static se.sundsvall.partyoverview.integration.db.model.ObjectType.ASSET;
import static se.sundsvall.partyoverview.integration.db.model.Operation.DELETE;
import static se.sundsvall.partyoverview.integration.db.model.Operation.UPSERT;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

	private static final String ASSET_ENTITY_ID = "e84b72ee-1a34-44b5-b8f6-2e0e42e99010";

	@Mock
	private EventRepository eventRepositoryMock;

	@Mock
	private AssetRepository assetRepositoryMock;

	@Spy
	private JsonMapper jsonMapper = JsonMapper.builder().build();

	@InjectMocks
	private EventService service;

	@Captor
	private ArgumentCaptor<EventEntity> eventEntityCaptor;

	@Captor
	private ArgumentCaptor<AssetEntity> assetEntityCaptor;

	@AfterEach
	void verifyNoMoreRepositoryInteractions() {
		verifyNoMoreInteractions(eventRepositoryMock, assetRepositoryMock);
	}

	@Test
	void handleUpsertOfNewAsset() {
		final var change = assetChange(UPSERT, 1);
		whenEventIsNew(change);
		when(assetRepositoryMock.findByMunicipalityIdAndSourceAndSourceId(MUNICIPALITY_ID, SOURCE, SOURCE_ID)).thenReturn(Optional.empty());

		service.handle(change);

		verify(eventRepositoryMock).save(eventEntityCaptor.capture());
		assertThat(eventEntityCaptor.getValue().getEventId()).isEqualTo(change.eventId());
		assertThat(eventEntityCaptor.getValue().getOperation()).isEqualTo(UPSERT);
		assertThat(eventEntityCaptor.getValue().getPayload()).contains(change.eventId(), PARTY_ID, "PRH-123456789");

		verify(assetRepositoryMock).findByMunicipalityIdAndSourceAndSourceId(MUNICIPALITY_ID, SOURCE, SOURCE_ID);
		verify(assetRepositoryMock).save(assetEntityCaptor.capture());
		assertThat(assetEntityCaptor.getValue().getId()).isNull();
		assertThat(assetEntityCaptor.getValue().getVersion()).isEqualTo(1L);
		assertThat(assetEntityCaptor.getValue().getAssetId()).isEqualTo("PRH-123456789");
		verifyEventIsChecked(change);
	}

	@Test
	void handleUpsertOfNewerVersion() {
		final var change = assetChange(UPSERT, 3);
		final var existing = AssetEntity.create().withId(ASSET_ENTITY_ID).withVersion(2L);
		whenEventIsNew(change);
		when(assetRepositoryMock.findByMunicipalityIdAndSourceAndSourceId(MUNICIPALITY_ID, SOURCE, SOURCE_ID)).thenReturn(Optional.of(existing));

		service.handle(change);

		verify(eventRepositoryMock).save(eventEntityCaptor.capture());
		verify(assetRepositoryMock).findByMunicipalityIdAndSourceAndSourceId(MUNICIPALITY_ID, SOURCE, SOURCE_ID);
		verify(assetRepositoryMock).save(assetEntityCaptor.capture());
		assertThat(assetEntityCaptor.getValue().getId()).isEqualTo(ASSET_ENTITY_ID);
		assertThat(assetEntityCaptor.getValue().getVersion()).isEqualTo(3L);
		verifyEventIsChecked(change);
	}

	@Test
	void handleUpsertOfOlderVersionKeepsItAsHistoryOnly() {
		final var change = assetChange(UPSERT, 3);
		final var existing = AssetEntity.create().withId(ASSET_ENTITY_ID).withVersion(4L);
		whenEventIsNew(change);
		when(assetRepositoryMock.findByMunicipalityIdAndSourceAndSourceId(MUNICIPALITY_ID, SOURCE, SOURCE_ID)).thenReturn(Optional.of(existing));

		service.handle(change);

		verify(eventRepositoryMock).save(eventEntityCaptor.capture());
		assertThat(eventEntityCaptor.getValue().getEventId()).isEqualTo(change.eventId());
		verify(assetRepositoryMock).findByMunicipalityIdAndSourceAndSourceId(MUNICIPALITY_ID, SOURCE, SOURCE_ID);
		verifyEventIsChecked(change);
	}

	@Test
	void handleUpsertOfSameVersionReplacesTheAsset() {
		final var change = assetChange(UPSERT, 3);
		final var existing = AssetEntity.create().withId(ASSET_ENTITY_ID).withVersion(3L).withTitle("wrongly translated title");
		whenEventIsNew(change);
		when(assetRepositoryMock.findByMunicipalityIdAndSourceAndSourceId(MUNICIPALITY_ID, SOURCE, SOURCE_ID)).thenReturn(Optional.of(existing));

		service.handle(change);

		verify(eventRepositoryMock).save(eventEntityCaptor.capture());
		verify(assetRepositoryMock).findByMunicipalityIdAndSourceAndSourceId(MUNICIPALITY_ID, SOURCE, SOURCE_ID);
		verify(assetRepositoryMock).save(assetEntityCaptor.capture());
		assertThat(assetEntityCaptor.getValue().getId()).isEqualTo(ASSET_ENTITY_ID);
		assertThat(assetEntityCaptor.getValue().getVersion()).isEqualTo(3L);
		assertThat(assetEntityCaptor.getValue().getTitle()).isEqualTo("title");
		verifyEventIsChecked(change);
	}

	@Test
	void handleUpsertWithoutData() {
		final var change = withoutData(assetChange(UPSERT, 1));
		whenEventIsNew(change);

		assertThatExceptionOfType(ThrowableProblem.class)
			.isThrownBy(() -> service.handle(change))
			.satisfies(problem -> {
				assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST);
				assertThat(problem.getDetail()).isEqualTo("Event %s has no data".formatted(change.eventId()));
			});

		verifyEventIsChecked(change);
	}

	@Test
	void handleDelete() {
		final var change = withoutData(assetChange(DELETE, 4));
		whenEventIsNew(change);

		service.handle(change);

		verify(assetRepositoryMock).deleteByMunicipalityIdAndSourceAndSourceId(MUNICIPALITY_ID, SOURCE, SOURCE_ID);
		verify(eventRepositoryMock).deleteByMunicipalityIdAndSourceAndObjectTypeAndSourceId(MUNICIPALITY_ID, SOURCE, ASSET, SOURCE_ID);
		verify(eventRepositoryMock).save(eventEntityCaptor.capture());
		assertThat(eventEntityCaptor.getValue().getEventId()).isEqualTo(change.eventId());
		assertThat(eventEntityCaptor.getValue().getOperation()).isEqualTo(DELETE);
		assertThat(eventEntityCaptor.getValue().getPayload()).isNull();
		verifyEventIsChecked(change);
	}

	@Test
	void handleEventThatHasBeenHandledBefore() {
		final var change = assetChange(UPSERT, 1);
		when(eventRepositoryMock.existsByEventId(change.eventId())).thenReturn(true);

		service.handle(change);

		verify(eventRepositoryMock).existsByEventId(change.eventId());
	}

	@Test
	void handleEventForDeletedObject() {
		final var change = assetChange(UPSERT, 5);
		when(eventRepositoryMock.existsByEventId(change.eventId())).thenReturn(false);
		when(eventRepositoryMock.existsByMunicipalityIdAndSourceAndObjectTypeAndSourceIdAndOperation(MUNICIPALITY_ID, SOURCE, ASSET, SOURCE_ID, DELETE)).thenReturn(true);

		service.handle(change);

		verifyEventIsChecked(change);
	}

	private void whenEventIsNew(final ObjectChange change) {
		when(eventRepositoryMock.existsByEventId(change.eventId())).thenReturn(false);
		when(eventRepositoryMock.existsByMunicipalityIdAndSourceAndObjectTypeAndSourceIdAndOperation(MUNICIPALITY_ID, SOURCE, ASSET, SOURCE_ID, DELETE)).thenReturn(false);
	}

	private void verifyEventIsChecked(final ObjectChange change) {
		verify(eventRepositoryMock).existsByEventId(change.eventId());
		verify(eventRepositoryMock).existsByMunicipalityIdAndSourceAndObjectTypeAndSourceIdAndOperation(MUNICIPALITY_ID, SOURCE, ASSET, SOURCE_ID, DELETE);
	}

	private static ObjectChange withoutData(final ObjectChange change) {
		return new ObjectChange(change.eventId(), change.source(), change.objectType(), change.operation(), change.sourceId(), change.version(), change.occurredAt(),
			change.municipalityId(), change.authority(), change.parties(), null);
	}
}
