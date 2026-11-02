package se.sundsvall.partyoverview.service.model;

import java.time.LocalDate;

/**
 * An asset, the data of an {@link ObjectChange} with objectType ASSET.
 */
public record AssetData(
	String assetId,
	String origin,
	String type,
	String title,
	String description,
	String status,
	String statusReason,
	LocalDate issued,
	LocalDate validTo)
	implements
	ObjectData {
}
