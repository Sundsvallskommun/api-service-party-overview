package se.sundsvall.partyoverview.service;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
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
import se.sundsvall.partyoverview.integration.db.model.PartyEmbeddable;
import se.sundsvall.partyoverview.service.model.ObjectChange;
import se.sundsvall.partyoverview.service.model.Party;
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
import static se.sundsvall.partyoverview.service.model.Operation.DELETE;
import static se.sundsvall.partyoverview.service.model.Operation.UPSERT;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

	private static final String ASSET_ENTITY_ID = "e84b72ee-1a34-44b5-b8f6-2e0e42e99010";
	private static final String EVENT_ENTITY_ID = "5c3f1f0e-1a2b-4c3d-8e4f-5a6b7c8d9e01";

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
		whenAssetIs(null);
		whenHistoryHolds(change, null);

		service.handle(change);

		verifyHistoryIsSaved(change);
		assertThat(eventEntityCaptor.getValue().getId()).isNull();
		assertThat(eventEntityCaptor.getValue().getPayload()).contains(change.eventId(), PARTY_ID, "PRH-123456789");
		verify(assetRepositoryMock).save(assetEntityCaptor.capture());
		assertThat(assetEntityCaptor.getValue().getId()).isNull();
		assertThat(assetEntityCaptor.getValue().getVersion()).isEqualTo(1L);
		assertThat(assetEntityCaptor.getValue().getDeleted()).isFalse();
		assertThat(assetEntityCaptor.getValue().getAssetId()).isEqualTo("PRH-123456789");
	}

	@Test
	void handleUpsertOfNewerVersion() {
		final var change = assetChange(UPSERT, 3);
		whenAssetIs(AssetEntity.create().withId(ASSET_ENTITY_ID).withVersion(2L).withDeleted(false));
		whenHistoryHolds(change, null);

		service.handle(change);

		verifyHistoryIsSaved(change);
		verify(assetRepositoryMock).save(assetEntityCaptor.capture());
		assertThat(assetEntityCaptor.getValue().getId()).isEqualTo(ASSET_ENTITY_ID);
		assertThat(assetEntityCaptor.getValue().getVersion()).isEqualTo(3L);
	}

	@Test
	void handleUpsertOfSameVersionReplacesTheAssetAndItsHistory() {
		final var change = assetChange(UPSERT, 3);
		whenAssetIs(AssetEntity.create().withId(ASSET_ENTITY_ID).withVersion(3L).withDeleted(false).withTitle("wrongly translated title"));
		whenHistoryHolds(change, EventEntity.create().withId(EVENT_ENTITY_ID).withPayload("{\"title\":\"wrongly translated title\"}"));

		service.handle(change);

		verifyHistoryIsSaved(change);
		assertThat(eventEntityCaptor.getValue().getId()).isEqualTo(EVENT_ENTITY_ID);
		assertThat(eventEntityCaptor.getValue().getEventId()).isEqualTo(change.eventId());
		assertThat(eventEntityCaptor.getValue().getPayload()).doesNotContain("wrongly translated title");
		verify(assetRepositoryMock).save(assetEntityCaptor.capture());
		assertThat(assetEntityCaptor.getValue().getId()).isEqualTo(ASSET_ENTITY_ID);
		assertThat(assetEntityCaptor.getValue().getTitle()).isEqualTo("title");
	}

	@Test
	void handleUpsertOfOlderVersionKeepsItAsHistoryOnly() {
		final var change = assetChange(UPSERT, 3);
		whenAssetIs(AssetEntity.create().withId(ASSET_ENTITY_ID).withVersion(4L).withDeleted(false));
		whenHistoryHolds(change, null);

		service.handle(change);

		verifyHistoryIsSaved(change);
	}

	@Test
	void handleUpsertOfDeletedAssetIsSkipped() {
		final var change = assetChange(UPSERT, 5);
		whenAssetIs(AssetEntity.create().withId(ASSET_ENTITY_ID).withVersion(4L).withDeleted(true));

		service.handle(change);

		verifyAssetIsLookedUp();
	}

	@Test
	void handleUpsertWithoutData() {
		final var change = withoutData(assetChange(UPSERT, 1));

		assertThatExceptionOfType(ThrowableProblem.class)
			.isThrownBy(() -> service.handle(change))
			.satisfies(problem -> {
				assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST);
				assertThat(problem.getDetail()).isEqualTo("Event %s has no data".formatted(change.eventId()));
			});
	}

	@ParameterizedTest
	@NullAndEmptySource
	void handleUpsertWithoutParties(final List<Party> parties) {
		final var change = withParties(assetChange(UPSERT, 1), parties);

		assertThatExceptionOfType(ThrowableProblem.class)
			.isThrownBy(() -> service.handle(change))
			.satisfies(problem -> {
				assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST);
				assertThat(problem.getDetail()).isEqualTo("Event %s concerns no party".formatted(change.eventId()));
			});
	}

	@Test
	void handleDeleteOfKnownAsset() {
		final var change = withoutData(withParties(assetChange(DELETE, 4), List.of()));
		whenAssetIs(AssetEntity.create().withId(ASSET_ENTITY_ID).withVersion(3L).withDeleted(false).withTitle("title")
			.withParties(List.of(PartyEmbeddable.create().withPartyId(PARTY_ID))));

		service.handle(change);

		verify(eventRepositoryMock).deleteByMunicipalityIdAndSourceAndObjectTypeAndSourceId(MUNICIPALITY_ID, SOURCE, ASSET, SOURCE_ID);
		verify(assetRepositoryMock).save(assetEntityCaptor.capture());
		assertThat(assetEntityCaptor.getValue().getId()).isEqualTo(ASSET_ENTITY_ID);
		assertThat(assetEntityCaptor.getValue().getVersion()).isEqualTo(4L);
		assertThat(assetEntityCaptor.getValue().getDeleted()).isTrue();
		assertThat(assetEntityCaptor.getValue().getTitle()).isNull();
		assertThat(assetEntityCaptor.getValue().getParties()).isEmpty();
		verifyAssetIsLookedUp();
	}

	@Test
	void handleDeleteOfUnknownAssetLeavesATombstone() {
		final var change = withoutData(withParties(assetChange(DELETE, 2), List.of()));
		whenAssetIs(null);

		service.handle(change);

		verify(eventRepositoryMock).deleteByMunicipalityIdAndSourceAndObjectTypeAndSourceId(MUNICIPALITY_ID, SOURCE, ASSET, SOURCE_ID);
		verify(assetRepositoryMock).save(assetEntityCaptor.capture());
		assertThat(assetEntityCaptor.getValue().getId()).isNull();
		assertThat(assetEntityCaptor.getValue().getSourceId()).isEqualTo(SOURCE_ID);
		assertThat(assetEntityCaptor.getValue().getDeleted()).isTrue();
		verifyAssetIsLookedUp();
	}

	@Test
	void handleDeleteOfDeletedAssetIsSkipped() {
		final var change = assetChange(DELETE, 5);
		whenAssetIs(AssetEntity.create().withId(ASSET_ENTITY_ID).withVersion(4L).withDeleted(true));

		service.handle(change);

		verifyAssetIsLookedUp();
	}

	private void whenAssetIs(final AssetEntity asset) {
		when(assetRepositoryMock.findByMunicipalityIdAndSourceAndSourceId(MUNICIPALITY_ID, SOURCE, SOURCE_ID)).thenReturn(Optional.ofNullable(asset));
	}

	private void whenHistoryHolds(final ObjectChange change, final EventEntity event) {
		when(eventRepositoryMock.findByMunicipalityIdAndSourceAndObjectTypeAndSourceIdAndVersion(MUNICIPALITY_ID, SOURCE, ASSET, SOURCE_ID, change.version()))
			.thenReturn(Optional.ofNullable(event));
	}

	private void verifyAssetIsLookedUp() {
		verify(assetRepositoryMock).findByMunicipalityIdAndSourceAndSourceId(MUNICIPALITY_ID, SOURCE, SOURCE_ID);
	}

	private void verifyHistoryIsSaved(final ObjectChange change) {
		verifyAssetIsLookedUp();
		verify(eventRepositoryMock).findByMunicipalityIdAndSourceAndObjectTypeAndSourceIdAndVersion(MUNICIPALITY_ID, SOURCE, ASSET, SOURCE_ID, change.version());
		verify(eventRepositoryMock).save(eventEntityCaptor.capture());
		assertThat(eventEntityCaptor.getValue().getVersion()).isEqualTo(change.version());
	}

	private static ObjectChange withParties(final ObjectChange change, final List<Party> parties) {
		return new ObjectChange(change.eventId(), change.source(), change.objectType(), change.operation(), change.sourceId(), change.version(), change.occurredAt(),
			change.municipalityId(), change.authority(), parties, change.data());
	}

	private static ObjectChange withoutData(final ObjectChange change) {
		return new ObjectChange(change.eventId(), change.source(), change.objectType(), change.operation(), change.sourceId(), change.version(), change.occurredAt(),
			change.municipalityId(), change.authority(), change.parties(), null);
	}
}
