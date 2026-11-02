package se.sundsvall.partyoverview.integration.db.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.UuidGenerator;

import static jakarta.persistence.FetchType.EAGER;
import static org.hibernate.Length.LONG32;
import static org.hibernate.annotations.FetchMode.SUBSELECT;

/**
 * The current state of an asset, from the newest event received for it. Once the source has deleted the asset, the row
 * stays as a tombstone, marked deleted and emptied of everything but the asset's identity.
 * <p>
 * The row is also what keeps two consumers handling the same asset apart: a new asset collides on the unique key, a
 * known
 * one on lockVersion.
 */
@Entity
@Table(name = "asset",
	uniqueConstraints = {
		@UniqueConstraint(name = "uq_asset_municipality_id_source_source_id", columnNames = {
			"municipality_id", "source", "source_id"
		})
	})
public class AssetEntity {

	@Id
	@UuidGenerator
	private String id;

	@Column(name = "municipality_id", nullable = false)
	private String municipalityId;

	@Column(name = "source", length = 64, nullable = false)
	private String source;

	@Column(name = "source_id", nullable = false)
	private String sourceId;

	@Column(name = "version", nullable = false)
	private Long version;

	@Version
	@Column(name = "lock_version", nullable = false)
	private Long lockVersion;

	@Column(name = "deleted", nullable = false)
	private Boolean deleted;

	@Column(name = "authority")
	private String authority;

	@ElementCollection(fetch = EAGER)
	@Fetch(SUBSELECT)
	@CollectionTable(name = "asset_party",
		joinColumns = @JoinColumn(name = "asset_id", referencedColumnName = "id", foreignKey = @ForeignKey(name = "fk_asset_party_asset_id")),
		indexes = {
			@Index(name = "idx_asset_party_party_id", columnList = "party_id")
		})
	private List<PartyEmbeddable> parties;

	@Column(name = "asset_id")
	private String assetId;

	@Column(name = "origin")
	private String origin;

	@Column(name = "`type`")
	private String type;

	@Column(name = "title")
	private String title;

	@Column(name = "description", length = LONG32)
	private String description;

	@Column(name = "status", length = 32)
	private String status;

	@Column(name = "status_reason")
	private String statusReason;

	@Column(name = "issued")
	private LocalDate issued;

	@Column(name = "valid_to")
	private LocalDate validTo;

	public static AssetEntity create() {
		return new AssetEntity();
	}

	public String getId() {
		return id;
	}

	public void setId(final String id) {
		this.id = id;
	}

	public AssetEntity withId(final String id) {
		this.id = id;
		return this;
	}

	public String getMunicipalityId() {
		return municipalityId;
	}

	public void setMunicipalityId(final String municipalityId) {
		this.municipalityId = municipalityId;
	}

	public AssetEntity withMunicipalityId(final String municipalityId) {
		this.municipalityId = municipalityId;
		return this;
	}

	public String getSource() {
		return source;
	}

	public void setSource(final String source) {
		this.source = source;
	}

	public AssetEntity withSource(final String source) {
		this.source = source;
		return this;
	}

	public String getSourceId() {
		return sourceId;
	}

	public void setSourceId(final String sourceId) {
		this.sourceId = sourceId;
	}

	public AssetEntity withSourceId(final String sourceId) {
		this.sourceId = sourceId;
		return this;
	}

	public Long getVersion() {
		return version;
	}

	public void setVersion(final Long version) {
		this.version = version;
	}

	public AssetEntity withVersion(final Long version) {
		this.version = version;
		return this;
	}

	public Long getLockVersion() {
		return lockVersion;
	}

	public void setLockVersion(final Long lockVersion) {
		this.lockVersion = lockVersion;
	}

	public AssetEntity withLockVersion(final Long lockVersion) {
		this.lockVersion = lockVersion;
		return this;
	}

	public Boolean getDeleted() {
		return deleted;
	}

	public void setDeleted(final Boolean deleted) {
		this.deleted = deleted;
	}

	public AssetEntity withDeleted(final Boolean deleted) {
		this.deleted = deleted;
		return this;
	}

	public String getAuthority() {
		return authority;
	}

	public void setAuthority(final String authority) {
		this.authority = authority;
	}

	public AssetEntity withAuthority(final String authority) {
		this.authority = authority;
		return this;
	}

	public List<PartyEmbeddable> getParties() {
		return parties;
	}

	public void setParties(final List<PartyEmbeddable> parties) {
		this.parties = parties;
	}

	public AssetEntity withParties(final List<PartyEmbeddable> parties) {
		this.parties = parties;
		return this;
	}

	public String getAssetId() {
		return assetId;
	}

	public void setAssetId(final String assetId) {
		this.assetId = assetId;
	}

	public AssetEntity withAssetId(final String assetId) {
		this.assetId = assetId;
		return this;
	}

	public String getOrigin() {
		return origin;
	}

	public void setOrigin(final String origin) {
		this.origin = origin;
	}

	public AssetEntity withOrigin(final String origin) {
		this.origin = origin;
		return this;
	}

	public String getType() {
		return type;
	}

	public void setType(final String type) {
		this.type = type;
	}

	public AssetEntity withType(final String type) {
		this.type = type;
		return this;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(final String title) {
		this.title = title;
	}

	public AssetEntity withTitle(final String title) {
		this.title = title;
		return this;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(final String description) {
		this.description = description;
	}

	public AssetEntity withDescription(final String description) {
		this.description = description;
		return this;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(final String status) {
		this.status = status;
	}

	public AssetEntity withStatus(final String status) {
		this.status = status;
		return this;
	}

	public String getStatusReason() {
		return statusReason;
	}

	public void setStatusReason(final String statusReason) {
		this.statusReason = statusReason;
	}

	public AssetEntity withStatusReason(final String statusReason) {
		this.statusReason = statusReason;
		return this;
	}

	public LocalDate getIssued() {
		return issued;
	}

	public void setIssued(final LocalDate issued) {
		this.issued = issued;
	}

	public AssetEntity withIssued(final LocalDate issued) {
		this.issued = issued;
		return this;
	}

	public LocalDate getValidTo() {
		return validTo;
	}

	public void setValidTo(final LocalDate validTo) {
		this.validTo = validTo;
	}

	public AssetEntity withValidTo(final LocalDate validTo) {
		this.validTo = validTo;
		return this;
	}

	@Override
	public boolean equals(final Object o) {
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final AssetEntity that = (AssetEntity) o;
		return Objects.equals(id, that.id) && Objects.equals(municipalityId, that.municipalityId) && Objects.equals(source, that.source) && Objects.equals(sourceId, that.sourceId)
			&& Objects.equals(version, that.version) && Objects.equals(lockVersion, that.lockVersion) && Objects.equals(deleted, that.deleted) && Objects.equals(authority, that.authority)
			&& Objects.equals(parties, that.parties) && Objects.equals(assetId, that.assetId)
			&& Objects.equals(origin, that.origin) && Objects.equals(type, that.type) && Objects.equals(title, that.title) && Objects.equals(description, that.description)
			&& Objects.equals(status, that.status) && Objects.equals(statusReason, that.statusReason) && Objects.equals(issued, that.issued) && Objects.equals(validTo, that.validTo);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, municipalityId, source, sourceId, version, lockVersion, deleted, authority, parties, assetId, origin, type, title, description, status, statusReason, issued, validTo);
	}

	@Override
	public String toString() {
		return "AssetEntity{" +
			"id='" + id + '\'' +
			", municipalityId='" + municipalityId + '\'' +
			", source='" + source + '\'' +
			", sourceId='" + sourceId + '\'' +
			", version=" + version +
			", lockVersion=" + lockVersion +
			", deleted=" + deleted +
			", authority='" + authority + '\'' +
			", parties=" + parties +
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
