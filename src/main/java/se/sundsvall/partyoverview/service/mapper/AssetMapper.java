package se.sundsvall.partyoverview.service.mapper;

import java.util.ArrayList;
import java.util.List;
import se.sundsvall.partyoverview.api.model.Asset;
import se.sundsvall.partyoverview.integration.db.model.AssetEntity;
import se.sundsvall.partyoverview.integration.db.model.PartyEmbeddable;
import se.sundsvall.partyoverview.service.model.AssetData;
import se.sundsvall.partyoverview.service.model.ObjectChange;
import se.sundsvall.partyoverview.service.model.Party;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toCollection;

public final class AssetMapper {

	private AssetMapper() {}

	public static AssetEntity updateAssetEntity(final AssetEntity entity, final ObjectChange change, final AssetData data) {
		return entity
			.withMunicipalityId(change.municipalityId())
			.withSource(change.source())
			.withSourceId(change.sourceId())
			.withVersion(change.version())
			.withDeleted(false)
			.withAuthority(change.authority())
			.withParties(toPartyEmbeddables(change.parties()))
			.withAssetId(data.assetId())
			.withOrigin(data.origin())
			.withType(data.type())
			.withTitle(data.title())
			.withDescription(data.description())
			.withStatus(data.status())
			.withStatusReason(data.statusReason())
			.withIssued(data.issued())
			.withValidTo(data.validTo());
	}

	/**
	 * Turns the asset into a tombstone. Nothing but its identity and the version of the deletion is kept, since nothing
	 * about the asset may outlive its deletion in the source system.
	 */
	public static AssetEntity toDeletedAssetEntity(final AssetEntity entity, final ObjectChange change) {
		return entity
			.withMunicipalityId(change.municipalityId())
			.withSource(change.source())
			.withSourceId(change.sourceId())
			.withVersion(change.version())
			.withDeleted(true)
			.withAuthority(null)
			.withParties(new ArrayList<>())
			.withAssetId(null)
			.withOrigin(null)
			.withType(null)
			.withTitle(null)
			.withDescription(null)
			.withStatus(null)
			.withStatusReason(null)
			.withIssued(null)
			.withValidTo(null);
	}

	public static List<Asset> toAssets(final List<AssetEntity> entities) {
		return ofNullable(entities).orElse(emptyList()).stream()
			.map(AssetMapper::toAsset)
			.toList();
	}

	public static Asset toAsset(final AssetEntity entity) {
		return ofNullable(entity)
			.map(e -> Asset.create()
				.withSource(e.getSource())
				.withSourceId(e.getSourceId())
				.withAssetId(e.getAssetId())
				.withOrigin(e.getOrigin())
				.withType(e.getType())
				.withTitle(e.getTitle())
				.withDescription(e.getDescription())
				.withStatus(e.getStatus())
				.withStatusReason(e.getStatusReason())
				.withIssued(e.getIssued())
				.withValidTo(e.getValidTo()))
			.orElse(null);
	}

	static List<PartyEmbeddable> toPartyEmbeddables(final List<Party> parties) {
		return ofNullable(parties).orElse(emptyList()).stream()
			.map(party -> PartyEmbeddable.create()
				.withPartyId(party.partyId())
				.withRole(party.role()))
			.collect(toCollection(ArrayList::new));
	}
}
