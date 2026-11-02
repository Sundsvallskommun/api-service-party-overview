package se.sundsvall.partyoverview.integration.source.partyassets;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetEvent;
import generated.se.sundsvall.partyassets.AssetEvent.EventTypeEnum;
import java.util.List;
import java.util.Optional;
import se.sundsvall.partyoverview.service.model.AssetData;
import se.sundsvall.partyoverview.service.model.ObjectChange;
import se.sundsvall.partyoverview.service.model.Operation;
import se.sundsvall.partyoverview.service.model.Party;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static se.sundsvall.partyoverview.integration.db.model.ObjectType.ASSET;
import static se.sundsvall.partyoverview.service.model.Operation.DELETE;
import static se.sundsvall.partyoverview.service.model.Operation.UPSERT;

/**
 * Translates the events of party-assets to {@link ObjectChange}. A field the event lacks is left out, so that
 * EventService rejects the change and names what is missing.
 * <p>
 * Drafts are skipped, the way party-assets leaves them out of its own list of assets. A draft never becomes a draft
 * again once issued, so its deletion is skipped too: it concerns an asset the overview never held.
 */
public final class PartyAssetsTranslator {

	static final String SOURCE = "PARTY_ASSETS";
	static final String OWNER_ROLE = "OWNER";
	static final String DRAFT_STATUS = "DRAFT";

	private PartyAssetsTranslator() {}

	public static Optional<ObjectChange> toObjectChange(final String messageId, final AssetEvent event) {
		final var asset = ofNullable(event.getAsset());
		if (asset.map(Asset::getStatus).filter(DRAFT_STATUS::equals).isPresent()) {
			return Optional.empty();
		}

		return Optional.of(new ObjectChange(
			messageId,
			SOURCE,
			ASSET,
			ofNullable(event.getEventType()).map(PartyAssetsTranslator::toOperation).orElse(null),
			asset.map(Asset::getId).orElse(null),
			event.getVersion(),
			event.getOccurredAt(),
			event.getMunicipalityId(),
			// party-assets does not know which committee an asset belongs to
			null,
			asset.map(Asset::getPartyId).map(PartyAssetsTranslator::toParties).orElse(emptyList()),
			asset.map(PartyAssetsTranslator::toAssetData).orElse(null)));
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
