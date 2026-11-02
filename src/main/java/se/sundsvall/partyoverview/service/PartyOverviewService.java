package se.sundsvall.partyoverview.service;

import org.springframework.stereotype.Service;
import se.sundsvall.partyoverview.api.model.PartyOverview;
import se.sundsvall.partyoverview.integration.db.AssetRepository;

import static se.sundsvall.partyoverview.service.mapper.AssetMapper.toAssets;

@Service
public class PartyOverviewService {

	private final AssetRepository assetRepository;

	public PartyOverviewService(final AssetRepository assetRepository) {
		this.assetRepository = assetRepository;
	}

	public PartyOverview getOverview(final String municipalityId, final String partyId) {
		return PartyOverview.create()
			.withAssets(toAssets(assetRepository.findByMunicipalityIdAndPartyId(municipalityId, partyId)));
	}
}
