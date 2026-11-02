package se.sundsvall.partyoverview.service.mapper;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import se.sundsvall.partyoverview.api.model.Asset;
import se.sundsvall.partyoverview.integration.db.model.AssetEntity;
import se.sundsvall.partyoverview.integration.db.model.PartyEmbeddable;
import se.sundsvall.partyoverview.service.model.ObjectChange;
import se.sundsvall.partyoverview.service.model.Party;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static se.sundsvall.partyoverview.TestFactory.AUTHORITY;
import static se.sundsvall.partyoverview.TestFactory.MUNICIPALITY_ID;
import static se.sundsvall.partyoverview.TestFactory.PARTY_ID;
import static se.sundsvall.partyoverview.TestFactory.ROLE;
import static se.sundsvall.partyoverview.TestFactory.SOURCE;
import static se.sundsvall.partyoverview.TestFactory.SOURCE_ID;
import static se.sundsvall.partyoverview.TestFactory.assetChange;
import static se.sundsvall.partyoverview.TestFactory.assetData;
import static se.sundsvall.partyoverview.service.mapper.AssetMapper.toAsset;
import static se.sundsvall.partyoverview.service.mapper.AssetMapper.toAssets;
import static se.sundsvall.partyoverview.service.mapper.AssetMapper.toDeletedAssetEntity;
import static se.sundsvall.partyoverview.service.mapper.AssetMapper.toPartyEmbeddables;
import static se.sundsvall.partyoverview.service.mapper.AssetMapper.updateAssetEntity;
import static se.sundsvall.partyoverview.service.model.Operation.DELETE;
import static se.sundsvall.partyoverview.service.model.Operation.UPSERT;

class AssetMapperTest {

	private static final String ID = "e84b72ee-1a34-44b5-b8f6-2e0e42e99010";

	@Test
	void updateAssetEntitySetsEveryField() {
		final var entity = AssetEntity.create().withId(ID);

		final var result = updateAssetEntity(entity, assetChange(UPSERT, 3), assetData());

		assertThat(result).isSameAs(entity);
		assertThat(result).usingRecursiveComparison().isEqualTo(AssetEntity.create()
			.withId(ID)
			.withMunicipalityId(MUNICIPALITY_ID)
			.withSource(SOURCE)
			.withSourceId(SOURCE_ID)
			.withVersion(3L)
			.withDeleted(false)
			.withAuthority(AUTHORITY)
			.withParties(List.of(PartyEmbeddable.create().withPartyId(PARTY_ID).withRole(ROLE)))
			.withAssetId("PRH-123456789")
			.withOrigin("CASEDATA")
			.withType("PERMIT")
			.withTitle("title")
			.withDescription("description")
			.withStatus("ACTIVE")
			.withStatusReason("statusReason")
			.withIssued(LocalDate.of(2026, 1, 1))
			.withValidTo(LocalDate.of(2027, 1, 1)));
	}

	@Test
	void updateAssetEntityReplacesTheParties() {
		final var entity = AssetEntity.create().withParties(List.of(PartyEmbeddable.create().withPartyId("old-party-id")));
		final var change = assetChange(UPSERT, 3);
		final var changeWithoutParties = new ObjectChange(change.eventId(), change.source(), change.objectType(), change.operation(), change.sourceId(), change.version(),
			change.occurredAt(), change.municipalityId(), change.authority(), null, change.data());

		final var result = updateAssetEntity(entity, changeWithoutParties, assetData());

		assertThat(result.getParties()).isEmpty();
	}

	@Test
	void toDeletedAssetEntityKeepsOnlyTheIdentity() {
		final var entity = updateAssetEntity(AssetEntity.create().withId(ID).withLockVersion(2L), assetChange(UPSERT, 3), assetData());

		final var result = toDeletedAssetEntity(entity, assetChange(DELETE, 4));

		assertThat(result).isSameAs(entity);
		assertThat(result).usingRecursiveComparison().isEqualTo(AssetEntity.create()
			.withId(ID)
			.withLockVersion(2L)
			.withMunicipalityId(MUNICIPALITY_ID)
			.withSource(SOURCE)
			.withSourceId(SOURCE_ID)
			.withVersion(4L)
			.withDeleted(true)
			.withParties(List.of()));
	}

	@ParameterizedTest
	@MethodSource("toAssetArguments")
	void toAssetFromEntity(final AssetEntity entity, final Asset expected) {
		assertThat(toAsset(entity)).usingRecursiveComparison().isEqualTo(expected);
	}

	private static Stream<Arguments> toAssetArguments() {
		return Stream.of(
			Arguments.of(null, null),
			Arguments.of(AssetEntity.create(), Asset.create()),
			Arguments.of(
				AssetEntity.create()
					.withId(ID)
					.withMunicipalityId(MUNICIPALITY_ID)
					.withSource(SOURCE)
					.withSourceId(SOURCE_ID)
					.withVersion(3L)
					.withAuthority(AUTHORITY)
					.withAssetId("PRH-123456789")
					.withOrigin("CASEDATA")
					.withType("PERMIT")
					.withTitle("title")
					.withDescription("description")
					.withStatus("ACTIVE")
					.withStatusReason("statusReason")
					.withIssued(LocalDate.of(2026, 1, 1))
					.withValidTo(LocalDate.of(2027, 1, 1)),
				Asset.create()
					.withSource(SOURCE)
					.withSourceId(SOURCE_ID)
					.withAssetId("PRH-123456789")
					.withOrigin("CASEDATA")
					.withType("PERMIT")
					.withTitle("title")
					.withDescription("description")
					.withStatus("ACTIVE")
					.withStatusReason("statusReason")
					.withIssued(LocalDate.of(2026, 1, 1))
					.withValidTo(LocalDate.of(2027, 1, 1))));
	}

	@Test
	void toAssetsFromEntities() {
		final var entities = List.of(
			AssetEntity.create().withSourceId("1").withTitle("a"),
			AssetEntity.create().withSourceId("2").withTitle("b"));

		assertThat(toAssets(entities))
			.extracting(Asset::getSourceId, Asset::getTitle)
			.containsExactly(tuple("1", "a"), tuple("2", "b"));
	}

	@Test
	void toAssetsFromNull() {
		assertThat(toAssets(null)).isEmpty();
	}

	@Test
	void toPartyEmbeddablesFromParties() {
		final var result = toPartyEmbeddables(List.of(new Party(PARTY_ID, ROLE), new Party(PARTY_ID, null)));

		assertThat(result)
			.extracting(PartyEmbeddable::getPartyId, PartyEmbeddable::getRole)
			.containsExactly(tuple(PARTY_ID, ROLE), tuple(PARTY_ID, null));
	}

	@Test
	void toPartyEmbeddablesFromNull() {
		assertThat(toPartyEmbeddables(null)).isEmpty();
	}
}
