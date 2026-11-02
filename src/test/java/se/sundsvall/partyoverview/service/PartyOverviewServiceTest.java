package se.sundsvall.partyoverview.service;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.partyoverview.api.model.Asset;
import se.sundsvall.partyoverview.integration.db.AssetRepository;
import se.sundsvall.partyoverview.integration.db.model.AssetEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static se.sundsvall.partyoverview.TestFactory.MUNICIPALITY_ID;
import static se.sundsvall.partyoverview.TestFactory.PARTY_ID;

@ExtendWith(MockitoExtension.class)
class PartyOverviewServiceTest {

	@Mock
	private AssetRepository assetRepositoryMock;

	@InjectMocks
	private PartyOverviewService service;

	@Test
	void getOverview() {
		when(assetRepositoryMock.findByMunicipalityIdAndPartyId(MUNICIPALITY_ID, PARTY_ID))
			.thenReturn(List.of(AssetEntity.create().withSourceId("1").withTitle("title")));

		final var result = service.getOverview(MUNICIPALITY_ID, PARTY_ID);

		assertThat(result.getAssets())
			.extracting(Asset::getSourceId, Asset::getTitle)
			.containsExactly(tuple("1", "title"));
		verify(assetRepositoryMock).findByMunicipalityIdAndPartyId(MUNICIPALITY_ID, PARTY_ID);
		verifyNoMoreInteractions(assetRepositoryMock);
	}

	@Test
	void getOverviewForPartyWithoutAssets() {
		when(assetRepositoryMock.findByMunicipalityIdAndPartyId(MUNICIPALITY_ID, PARTY_ID)).thenReturn(List.of());

		final var result = service.getOverview(MUNICIPALITY_ID, PARTY_ID);

		assertThat(result.getAssets()).isEmpty();
		verify(assetRepositoryMock).findByMunicipalityIdAndPartyId(MUNICIPALITY_ID, PARTY_ID);
		verifyNoMoreInteractions(assetRepositoryMock);
	}
}
