package se.sundsvall.partyoverview.integration.db.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.UuidGenerator;

import static jakarta.persistence.EnumType.STRING;
import static java.time.OffsetDateTime.now;
import static java.time.ZoneId.systemDefault;
import static java.time.temporal.ChronoUnit.MILLIS;
import static org.hibernate.Length.LONG32;
import static org.hibernate.annotations.TimeZoneStorageType.NORMALIZE;
import static org.hibernate.type.SqlTypes.VARCHAR;

/**
 * One version of an object, kept as its history. A version received again replaces the row, so the history holds each
 * version once. The history of an object is removed when the source deletes it.
 */
@Entity
@Table(name = "event",
	uniqueConstraints = {
		@UniqueConstraint(name = "uq_event_object_version", columnNames = {
			"municipality_id", "source", "object_type", "source_id", "version"
		})
	})
public class EventEntity {

	@Id
	@UuidGenerator
	private String id;

	@Column(name = "event_id", nullable = false)
	private String eventId;

	@Column(name = "municipality_id", nullable = false)
	private String municipalityId;

	@Column(name = "source", length = 64, nullable = false)
	private String source;

	@Enumerated(STRING)
	@JdbcTypeCode(VARCHAR)
	@Column(name = "object_type", length = 32, nullable = false)
	private ObjectType objectType;

	@Column(name = "source_id", nullable = false)
	private String sourceId;

	@Column(name = "version", nullable = false)
	private Long version;

	@TimeZoneStorage(NORMALIZE)
	@Column(name = "occurred_at", nullable = false)
	private OffsetDateTime occurredAt;

	@TimeZoneStorage(NORMALIZE)
	@Column(name = "received_at", nullable = false)
	private OffsetDateTime receivedAt;

	@Column(name = "authority")
	private String authority;

	@Column(name = "payload", length = LONG32)
	private String payload;

	public static EventEntity create() {
		return new EventEntity();
	}

	@PrePersist
	@PreUpdate
	void onReceive() {
		receivedAt = now(systemDefault()).truncatedTo(MILLIS);
	}

	public String getId() {
		return id;
	}

	public void setId(final String id) {
		this.id = id;
	}

	public EventEntity withId(final String id) {
		this.id = id;
		return this;
	}

	public String getEventId() {
		return eventId;
	}

	public void setEventId(final String eventId) {
		this.eventId = eventId;
	}

	public EventEntity withEventId(final String eventId) {
		this.eventId = eventId;
		return this;
	}

	public String getMunicipalityId() {
		return municipalityId;
	}

	public void setMunicipalityId(final String municipalityId) {
		this.municipalityId = municipalityId;
	}

	public EventEntity withMunicipalityId(final String municipalityId) {
		this.municipalityId = municipalityId;
		return this;
	}

	public String getSource() {
		return source;
	}

	public void setSource(final String source) {
		this.source = source;
	}

	public EventEntity withSource(final String source) {
		this.source = source;
		return this;
	}

	public ObjectType getObjectType() {
		return objectType;
	}

	public void setObjectType(final ObjectType objectType) {
		this.objectType = objectType;
	}

	public EventEntity withObjectType(final ObjectType objectType) {
		this.objectType = objectType;
		return this;
	}

	public String getSourceId() {
		return sourceId;
	}

	public void setSourceId(final String sourceId) {
		this.sourceId = sourceId;
	}

	public EventEntity withSourceId(final String sourceId) {
		this.sourceId = sourceId;
		return this;
	}

	public Long getVersion() {
		return version;
	}

	public void setVersion(final Long version) {
		this.version = version;
	}

	public EventEntity withVersion(final Long version) {
		this.version = version;
		return this;
	}

	public OffsetDateTime getOccurredAt() {
		return occurredAt;
	}

	public void setOccurredAt(final OffsetDateTime occurredAt) {
		this.occurredAt = occurredAt;
	}

	public EventEntity withOccurredAt(final OffsetDateTime occurredAt) {
		this.occurredAt = occurredAt;
		return this;
	}

	public OffsetDateTime getReceivedAt() {
		return receivedAt;
	}

	public void setReceivedAt(final OffsetDateTime receivedAt) {
		this.receivedAt = receivedAt;
	}

	public EventEntity withReceivedAt(final OffsetDateTime receivedAt) {
		this.receivedAt = receivedAt;
		return this;
	}

	public String getAuthority() {
		return authority;
	}

	public void setAuthority(final String authority) {
		this.authority = authority;
	}

	public EventEntity withAuthority(final String authority) {
		this.authority = authority;
		return this;
	}

	public String getPayload() {
		return payload;
	}

	public void setPayload(final String payload) {
		this.payload = payload;
	}

	public EventEntity withPayload(final String payload) {
		this.payload = payload;
		return this;
	}

	@Override
	public boolean equals(final Object o) {
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final EventEntity that = (EventEntity) o;
		return Objects.equals(id, that.id) && Objects.equals(eventId, that.eventId) && Objects.equals(municipalityId, that.municipalityId) && Objects.equals(source, that.source)
			&& objectType == that.objectType && Objects.equals(sourceId, that.sourceId) && Objects.equals(version, that.version) && Objects.equals(occurredAt, that.occurredAt)
			&& Objects.equals(receivedAt, that.receivedAt) && Objects.equals(authority, that.authority) && Objects.equals(payload, that.payload);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, eventId, municipalityId, source, objectType, sourceId, version, occurredAt, receivedAt, authority, payload);
	}

	@Override
	public String toString() {
		return "EventEntity{" +
			"id='" + id + '\'' +
			", eventId='" + eventId + '\'' +
			", municipalityId='" + municipalityId + '\'' +
			", source='" + source + '\'' +
			", objectType=" + objectType +
			", sourceId='" + sourceId + '\'' +
			", version=" + version +
			", occurredAt=" + occurredAt +
			", receivedAt=" + receivedAt +
			", authority='" + authority + '\'' +
			", payload='" + payload + '\'' +
			'}';
	}
}
