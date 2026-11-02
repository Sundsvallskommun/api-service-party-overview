package se.sundsvall.partyoverview.service;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.partyoverview.integration.db.AssetRepository;
import se.sundsvall.partyoverview.integration.db.EventRepository;
import se.sundsvall.partyoverview.integration.db.model.AssetEntity;
import se.sundsvall.partyoverview.integration.db.model.EventEntity;
import se.sundsvall.partyoverview.service.model.AssetData;
import se.sundsvall.partyoverview.service.model.ObjectChange;
import tools.jackson.databind.json.JsonMapper;

import static java.lang.Boolean.TRUE;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static se.sundsvall.partyoverview.service.mapper.AssetMapper.toDeletedAssetEntity;
import static se.sundsvall.partyoverview.service.mapper.AssetMapper.updateAssetEntity;
import static se.sundsvall.partyoverview.service.mapper.EventMapper.updateEventEntity;
import static se.sundsvall.partyoverview.service.model.Operation.DELETE;

/**
 * Stores the changes the translators of the source systems hand over. Each version of an object is kept once as
 * history, and the newest version is what the overview shows. A version received again replaces what is stored, so a
 * redelivery or a republish is harmless.
 */
@Service
@Validated
public class EventService {

	private static final Logger LOG = LoggerFactory.getLogger(EventService.class);

	private static final String MISSING_DATA = "Event %s has no data";
	private static final String MISSING_PARTIES = "Event %s concerns no party";

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
		if (change.operation() == DELETE) {
			switch (change.objectType()) {
				case ASSET -> deleteAsset(change);
			}
			return;
		}

		final var data = ofNullable(change.data()).orElseThrow(() -> Problem.valueOf(BAD_REQUEST, MISSING_DATA.formatted(change.eventId())));
		ofNullable(change.parties()).filter(not(List::isEmpty)).orElseThrow(() -> Problem.valueOf(BAD_REQUEST, MISSING_PARTIES.formatted(change.eventId())));
		switch (data) {
			case AssetData asset -> upsertAsset(change, asset);
		}
	}

	private void upsertAsset(final ObjectChange change, final AssetData data) {
		final var existing = findAsset(change);
		if (isDeleted(existing)) {
			LOG.info("Event {} concerns an asset that has been deleted, skipping it", change.eventId());
			return;
		}

		saveHistory(change);
		if (existing.map(AssetEntity::getVersion).filter(version -> version > change.version()).isPresent()) {
			LOG.info("Event {} is older than the stored asset and is kept as history only", change.eventId());
			return;
		}
		assetRepository.save(updateAssetEntity(existing.orElseGet(AssetEntity::create), change, data));
	}

	private void deleteAsset(final ObjectChange change) {
		final var existing = findAsset(change);
		if (isDeleted(existing)) {
			LOG.info("Event {} concerns an asset that has been deleted, skipping it", change.eventId());
			return;
		}

		eventRepository.deleteByMunicipalityIdAndSourceAndObjectTypeAndSourceId(change.municipalityId(), change.source(), change.objectType(), change.sourceId());
		assetRepository.save(toDeletedAssetEntity(existing.orElseGet(AssetEntity::create), change));
	}

	private Optional<AssetEntity> findAsset(final ObjectChange change) {
		return assetRepository.findByMunicipalityIdAndSourceAndSourceId(change.municipalityId(), change.source(), change.sourceId());
	}

	private static boolean isDeleted(final Optional<AssetEntity> asset) {
		return asset.map(AssetEntity::getDeleted).filter(TRUE::equals).isPresent();
	}

	private void saveHistory(final ObjectChange change) {
		final var entity = eventRepository.findByMunicipalityIdAndSourceAndObjectTypeAndSourceIdAndVersion(change.municipalityId(), change.source(), change.objectType(), change.sourceId(), change.version())
			.orElseGet(EventEntity::create);
		eventRepository.save(updateEventEntity(entity, change, jsonMapper.writeValueAsString(change)));
	}
}
