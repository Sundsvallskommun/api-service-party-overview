package se.sundsvall.partyoverview.integration.source;

import java.util.Objects;
import java.util.stream.Stream;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.hibernate.exception.ConstraintViolationException.ConstraintKind.UNIQUE;

/**
 * Tells a collision with a concurrent change of the same object from other failures. Two consumers can handle the same
 * object at once for a short while around a redelivery, despite single active consumer, and the one that commits last
 * fails on the lock version of a known object or the unique key of a new one. Handling its message again reads what
 * the other one committed.
 */
public final class Collisions {

	private Collisions() {}

	/**
	 * Spring reports a duplicate key as a plain DataIntegrityViolationException, so the kind of constraint is read from
	 * Hibernate's exception in the cause chain.
	 */
	public static boolean isCollision(final Throwable throwable) {
		return Stream.iterate(throwable, Objects::nonNull, Throwable::getCause)
			.anyMatch(cause -> cause instanceof OptimisticLockingFailureException
				|| cause instanceof final ConstraintViolationException violation && violation.getKind() == UNIQUE);
	}
}
