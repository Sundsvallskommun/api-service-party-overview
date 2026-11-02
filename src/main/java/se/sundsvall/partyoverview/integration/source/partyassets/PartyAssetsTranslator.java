package se.sundsvall.partyoverview.integration.source.partyassets;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetEvent;
import generated.se.sundsvall.partyassets.AssetEvent.EventTypeEnum;
import java.util.List;
import se.sundsvall.partyoverview.integration.db.model.Operation;
import se.sundsvall.partyoverview.service.model.AssetData;
import se.sundsvall.partyoverview.service.model.ObjectChange;
import se.sundsvall.partyoverview.service.model.Party;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static se.sundsvall.partyoverview.integration.db.model.ObjectType.ASSET;
import static se.sundsvall.partyoverview.integration.db.model.Operation.DELETE;
import static se.sundsvall.partyoverview.integration.db.model.Operation.UPSERT;

/**
 * Translates the events of party-assets to {@link ObjectChange}. A field the event lacks is left null, so that the
 * validation in EventService names it.
 */
public final class PartyAssetsTranslator {

	static final String SOURCE = "PARTY_ASSETS";
	static final String OWNER_ROLE = "OWNER";

	private PartyAssetsTranslator() {}

	public static ObjectChange toObjectChange(final String messageId, final AssetEvent event) {
		final var asset = ofNullable(event.getAsset());

		return new ObjectChange(
			messageId,
			SOURCE,
			ASSET,
			ofNullable(event.getEventType()).map(PartyAssetsTranslator::toOperation).orElse(null),
			asset.map(Asset::getId).orElse(null),
			ofNullable(event.getRevision()).map(Integer::longValue).orElse(null),
			event.getOccurredAt(),
			event.getMunicipalityId(),
			// party-assets does not know which committee an asset belongs to
			null,
			asset.map(Asset::getPartyId).map(PartyAssetsTranslator::toParties).orElse(emptyList()),
			asset.map(PartyAssetsTranslator::toAssetData).orElse(null));
	}

	private static Operation toOperation(final EventTypeEnum eventType) {
		return switch (eventType) {
			case CREATED, UPDATED -> UPSERT;
			case DELETED -> DELETE;
		};
	}

	private static List<Party> toParties(final String partyId) {
		return List.of(new Party(partyId, OWNER_ROLE));
	}

	private static AssetData toAssetData(final Asset asset) {
		return new AssetData(
			asset.getAssetId(),
			asset.getOrigin(),
			asset.getType(),
			asset.getTitle(),
			asset.getDescription(),
			asset.getStatus(),
			asset.getStatusReason(),
			asset.getIssued(),
			asset.getValidTo());
	}
}
