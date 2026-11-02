package se.sundsvall.partyoverview.integration.source.partyassets;

import generated.se.sundsvall.partyassets.AssetEvent;
import generated.se.sundsvall.partyassets.AssetEvent.EventTypeEnum;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import se.sundsvall.partyoverview.service.model.ObjectChange;
import se.sundsvall.partyoverview.service.model.Operation;
import se.sundsvall.partyoverview.service.model.Party;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.partyoverview.TestFactory.MUNICIPALITY_ID;
import static se.sundsvall.partyoverview.TestFactory.PARTY_ID;
import static se.sundsvall.partyoverview.TestFactory.SOURCE;
import static se.sundsvall.partyoverview.TestFactory.SOURCE_ID;
import static se.sundsvall.partyoverview.TestFactory.assetData;
import static se.sundsvall.partyoverview.TestFactory.assetEvent;
import static se.sundsvall.partyoverview.integration.db.model.ObjectType.ASSET;
import static se.sundsvall.partyoverview.integration.source.partyassets.PartyAssetsTranslator.toObjectChange;
import static se.sundsvall.partyoverview.service.model.Operation.UPSERT;

class PartyAssetsTranslatorTest {

	private static final String MESSAGE_ID = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c01";

	@Test
	void toObjectChangeMapsEveryField() {
		final var event = assetEvent(EventTypeEnum.UPDATED, 3);

		final var result = toObjectChange(MESSAGE_ID, event);

		assertThat(result).contains(new ObjectChange(MESSAGE_ID, SOURCE, ASSET, UPSERT, SOURCE_ID, 3L, event.getOccurredAt(), MUNICIPALITY_ID, null,
			List.of(new Party(PARTY_ID, "OWNER")), assetData()));
	}

	@ParameterizedTest
	@CsvSource({
		"CREATED, UPSERT",
		"UPDATED, UPSERT",
		"DELETED, DELETE"
	})
	void toObjectChangeTranslatesTheEventType(final EventTypeEnum eventType, final Operation expected) {
		assertThat(toObjectChange(MESSAGE_ID, assetEvent(eventType, 1)))
			.hasValueSatisfying(change -> assertThat(change.operation()).isEqualTo(expected));
	}

	@ParameterizedTest
	@EnumSource(EventTypeEnum.class)
	void toObjectChangeSkipsDrafts(final EventTypeEnum eventType) {
		final var event = assetEvent(eventType, 1);
		event.getAsset().setStatus("DRAFT");

		assertThat(toObjectChange(MESSAGE_ID, event)).isEmpty();
	}

	@Test
	void toObjectChangeLeavesWhatTheEventLacksOut() {
		final var result = toObjectChange(MESSAGE_ID, new AssetEvent());

		assertThat(result).contains(new ObjectChange(MESSAGE_ID, SOURCE, ASSET, null, null, null, null, null, null, List.of(), null));
	}
}
