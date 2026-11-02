package se.sundsvall.partyoverview;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetEvent;
import generated.se.sundsvall.partyassets.AssetEvent.EventTypeEnum;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import se.sundsvall.partyoverview.service.model.AssetData;
import se.sundsvall.partyoverview.service.model.ObjectChange;
import se.sundsvall.partyoverview.service.model.Operation;
import se.sundsvall.partyoverview.service.model.Party;

import static java.util.UUID.randomUUID;
import static se.sundsvall.partyoverview.integration.db.model.ObjectType.ASSET;

public final class TestFactory {

	public static final String MUNICIPALITY_ID = "2281";
	public static final String PARTY_ID = "81471222-5798-11e9-ae24-57fa13b361e1";
	public static final String SOURCE = "PARTY_ASSETS";
	public static final String SOURCE_ID = "1c8f38a6-b492-4037-b7dc-de5bc6c629f0";
	public static final String AUTHORITY = "MILJONAMNDEN";
	public static final String ROLE = "OWNER";
	public static final LocalDate ISSUED = LocalDate.of(2026, 1, 1);
	public static final LocalDate VALID_TO = LocalDate.of(2027, 1, 1);

	private TestFactory() {}

	public static AssetData assetData() {
		return new AssetData("PRH-123456789", "CASEDATA", "PERMIT", "title", "description", "ACTIVE", "statusReason", ISSUED, VALID_TO);
	}

	public static ObjectChange assetChange(final Operation operation, final long version) {
		return new ObjectChange(randomUUID().toString(), SOURCE, ASSET, operation, SOURCE_ID, version, OffsetDateTime.now(), MUNICIPALITY_ID, AUTHORITY,
			List.of(new Party(PARTY_ID, ROLE)), assetData());
	}

	public static AssetEvent assetEvent(final EventTypeEnum eventType, final long version) {
		return new AssetEvent()
			.eventType(eventType)
			.municipalityId(MUNICIPALITY_ID)
			.version(version)
			.occurredAt(OffsetDateTime.now())
			.asset(new Asset()
				.id(SOURCE_ID)
				.assetId("PRH-123456789")
				.origin("CASEDATA")
				.partyId(PARTY_ID)
				.type("PERMIT")
				.title("title")
				.description("description")
				.status("ACTIVE")
				.statusReason("statusReason")
				.issued(ISSUED)
				.validTo(VALID_TO));
	}
}
