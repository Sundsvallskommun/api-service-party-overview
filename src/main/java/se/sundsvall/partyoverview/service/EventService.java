package se.sundsvall.partyoverview.service;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.partyoverview.integration.db.AssetRepository;
import se.sundsvall.partyoverview.integration.db.EventRepository;
import se.sundsvall.partyoverview.integration.db.model.AssetEntity;
import se.sundsvall.partyoverview.service.model.AssetData;
import se.sundsvall.partyoverview.service.model.ObjectChange;
import tools.jackson.databind.json.JsonMapper;

import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static se.sundsvall.partyoverview.integration.db.model.Operation.DELETE;
import static se.sundsvall.partyoverview.service.mapper.AssetMapper.updateAssetEntity;
import static se.sundsvall.partyoverview.service.mapper.EventMapper.toEventEntity;
import static se.sundsvall.partyoverview.service.mapper.EventMapper.toTombstoneEntity;

/**
 * Stores the changes the translators of the source systems hand over. Every change is kept as history, and the newest
 * version of each object is what the overview shows.
 */
@Service
@Validated
public class EventService {

	private static final Logger LOG = LoggerFactory.getLogger(EventService.class);

	private static final String MISSING_DATA = "Event %s has no data";

	private final EventRepository eventRepository;
	private final AssetRepository assetRepository;
	private final JsonMapper jsonMapper;

	public EventService(final EventRepository eventRepository, final AssetRepository assetRepository, final JsonMapper jsonMapper) {
		this.eventRepository = eventRepository;
		this.assetRepository = assetRepository;
		this.jsonMapper = jsonMapper;
	}

	@Transactional
	public void handle(@Valid final ObjectChange change) {
		if (eventRepository.existsByEventId(change.eventId())) {
			LOG.info("Event {} has been handled before, skipping it", change.eventId());
			return;
		}
		if (isDeleted(change)) {
			LOG.info("Event {} concerns an object that has been deleted, skipping it", change.eventId());
			return;
		}
		if (change.operation() == DELETE) {
			delete(change);
			return;
		}

		final var data = ofNullable(change.data()).orElseThrow(() -> Problem.valueOf(BAD_REQUEST, MISSING_DATA.formatted(change.eventId())));
		eventRepository.save(toEventEntity(change, jsonMapper.writeValueAsString(change)));
		switch (data) {
			case AssetData asset -> upsertAsset(change, asset);
		}
	}

	private boolean isDeleted(final ObjectChange change) {
		return eventRepository.existsByMunicipalityIdAndSourceAndObjectTypeAndSourceIdAndOperation(change.municipalityId(), change.source(), change.objectType(), change.sourceId(), DELETE);
	}

	/**
	 * The same version replaces the stored asset, so that a source can correct the overview by publishing again.
	 */
	private void upsertAsset(final ObjectChange change, final AssetData data) {
		final var existing = assetRepository.findByMunicipalityIdAndSourceAndSourceId(change.municipalityId(), change.source(), change.sourceId());
		if (existing.map(AssetEntity::getVersion).filter(version -> version > change.version()).isPresent()) {
			LOG.info("Event {} is older than the stored asset and is kept as history only", change.eventId());
			return;
		}
		assetRepository.save(updateAssetEntity(existing.orElseGet(AssetEntity::create), change, data));
	}

	private void delete(final ObjectChange change) {
		assetRepository.deleteByMunicipalityIdAndSourceAndSourceId(change.municipalityId(), change.source(), change.sourceId());
		eventRepository.deleteByMunicipalityIdAndSourceAndObjectTypeAndSourceId(change.municipalityId(), change.source(), change.objectType(), change.sourceId());
		eventRepository.save(toTombstoneEntity(change));
	}
}
