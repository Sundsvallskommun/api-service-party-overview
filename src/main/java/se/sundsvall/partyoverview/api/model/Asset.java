package se.sundsvall.partyoverview.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Objects;

@Schema(description = "Asset model, an asset a party holds or has been given by the municipality")
public class Asset {

	@Schema(description = "The system the asset comes from", examples = "PARTY_ASSETS")
	private String source;

	@Schema(description = "Id of the asset in the source system", examples = "1c8f38a6-b492-4037-b7dc-de5bc6c629f0")
	private String sourceId;

	@Schema(description = "External asset id used as an identifier by external systems", examples = "PRH-123456789")
	private String assetId;

	@Schema(description = "Where the asset originates, as the source system reports it", examples = "CASEDATA")
	private String origin;

	@Schema(description = "Asset type", examples = "PERMIT")
	private String type;

	@Schema(description = "Asset title, shown to the party", examples = "Stadigvarande tillstånd för servering av alkohol")
	private String title;

	@Schema(description = "Asset description", examples = "Asset description")
	private String description;

	@Schema(description = "Asset status", examples = "ACTIVE")
	private String status;

	@Schema(description = "Status reason", examples = "Status reason")
	private String statusReason;

	@Schema(description = "Issued date", examples = "2021-01-01")
	private LocalDate issued;

	@Schema(description = "Valid to date", examples = "2021-12-31")
	private LocalDate validTo;

	public static Asset create() {
		return new Asset();
	}

	public String getSource() {
		return source;
	}

	public void setSource(final String source) {
		this.source = source;
	}

	public Asset withSource(final String source) {
		this.source = source;
		return this;
	}

	public String getSourceId() {
		return sourceId;
	}

	public void setSourceId(final String sourceId) {
		this.sourceId = sourceId;
	}

	public Asset withSourceId(final String sourceId) {
		this.sourceId = sourceId;
		return this;
	}

	public String getAssetId() {
		return assetId;
	}

	public void setAssetId(final String assetId) {
		this.assetId = assetId;
	}

	public Asset withAssetId(final String assetId) {
		this.assetId = assetId;
		return this;
	}

	public String getOrigin() {
		return origin;
	}

	public void setOrigin(final String origin) {
		this.origin = origin;
	}

	public Asset withOrigin(final String origin) {
		this.origin = origin;
		return this;
	}

	public String getType() {
		return type;
	}

	public void setType(final String type) {
		this.type = type;
	}

	public Asset withType(final String type) {
		this.type = type;
		return this;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(final String title) {
		this.title = title;
	}

	public Asset withTitle(final String title) {
		this.title = title;
		return this;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(final String description) {
		this.description = description;
	}

	public Asset withDescription(final String description) {
		this.description = description;
		return this;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(final String status) {
		this.status = status;
	}

	public Asset withStatus(final String status) {
		this.status = status;
		return this;
	}

	public String getStatusReason() {
		return statusReason;
	}

	public void setStatusReason(final String statusReason) {
		this.statusReason = statusReason;
	}

	public Asset withStatusReason(final String statusReason) {
		this.statusReason = statusReason;
		return this;
	}

	public LocalDate getIssued() {
		return issued;
	}

	public void setIssued(final LocalDate issued) {
		this.issued = issued;
	}

	public Asset withIssued(final LocalDate issued) {
		this.issued = issued;
		return this;
	}

	public LocalDate getValidTo() {
		return validTo;
	}

	public void setValidTo(final LocalDate validTo) {
		this.validTo = validTo;
	}

	public Asset withValidTo(final LocalDate validTo) {
		this.validTo = validTo;
		return this;
	}

	@Override
	public boolean equals(final Object o) {
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final Asset that = (Asset) o;
		return Objects.equals(source, that.source) && Objects.equals(sourceId, that.sourceId) && Objects.equals(assetId, that.assetId) && Objects.equals(origin, that.origin)
			&& Objects.equals(type, that.type) && Objects.equals(title, that.title) && Objects.equals(description, that.description) && Objects.equals(status, that.status)
			&& Objects.equals(statusReason, that.statusReason) && Objects.equals(issued, that.issued) && Objects.equals(validTo, that.validTo);
	}

	@Override
	public int hashCode() {
		return Objects.hash(source, sourceId, assetId, origin, type, title, description, status, statusReason, issued, validTo);
	}

	@Override
	public String toString() {
		return "Asset{" +
			"source='" + source + '\'' +
			", sourceId='" + sourceId + '\'' +
			", assetId='" + assetId + '\'' +
			", origin='" + origin + '\'' +
			", type='" + type + '\'' +
			", title='" + title + '\'' +
			", description='" + description + '\'' +
			", status='" + status + '\'' +
			", statusReason='" + statusReason + '\'' +
			", issued=" + issued +
			", validTo=" + validTo +
			'}';
	}
}
