package se.sundsvall.partyoverview.integration.db.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

@Embeddable
public class PartyEmbeddable {

	@Column(name = "party_id", nullable = false)
	private String partyId;

	@Column(name = "role", length = 64)
	private String role;

	public static PartyEmbeddable create() {
		return new PartyEmbeddable();
	}

	public String getPartyId() {
		return partyId;
	}

	public void setPartyId(final String partyId) {
		this.partyId = partyId;
	}

	public PartyEmbeddable withPartyId(final String partyId) {
		this.partyId = partyId;
		return this;
	}

	public String getRole() {
		return role;
	}

	public void setRole(final String role) {
		this.role = role;
	}

	public PartyEmbeddable withRole(final String role) {
		this.role = role;
		return this;
	}

	@Override
	public boolean equals(final Object o) {
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final PartyEmbeddable that = (PartyEmbeddable) o;
		return Objects.equals(partyId, that.partyId) && Objects.equals(role, that.role);
	}

	@Override
	public int hashCode() {
		return Objects.hash(partyId, role);
	}

	@Override
	public String toString() {
		return "PartyEmbeddable{" +
			"partyId='" + partyId + '\'' +
			", role='" + role + '\'' +
			'}';
	}
}
