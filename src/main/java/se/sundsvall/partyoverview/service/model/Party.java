package se.sundsvall.partyoverview.service.model;

import se.sundsvall.dept44.common.validators.annotation.ValidUuid;

/**
 * A party an object concerns, and in what role, e.g. APPLICANT.
 */
public record Party(
	@ValidUuid String partyId,
	String role) {
}
