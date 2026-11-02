package se.sundsvall.partyoverview.integration.source;

import java.sql.SQLException;
import java.util.stream.Stream;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.partyoverview.integration.source.Collisions.isCollision;

class CollisionsTest {

	@ParameterizedTest(name = "{0}")
	@MethodSource("failures")
	void isCollisionTellsCollisionsFromOtherFailures(final String description, final Throwable failure, final boolean expected) {
		assertThat(isCollision(failure)).isEqualTo(expected);
	}

	private static Stream<Arguments> failures() {
		return Stream.of(
			Arguments.of("stale lock version", new ObjectOptimisticLockingFailureException("AssetEntity", "id"), true),
			Arguments.of("duplicate key", integrityViolation(ConstraintKind.UNIQUE), true),
			Arguments.of("duplicate key further down the chain", new IllegalStateException("wrapped", integrityViolation(ConstraintKind.UNIQUE)), true),
			Arguments.of("missing value", integrityViolation(ConstraintKind.NOT_NULL), false),
			Arguments.of("data too long", new DataIntegrityViolationException("Data too long for column 'title'"), false),
			Arguments.of("anything else", new IllegalStateException("Something else"), false));
	}

	private static DataIntegrityViolationException integrityViolation(final ConstraintKind kind) {
		return new DataIntegrityViolationException("could not execute statement",
			new ConstraintViolationException("could not execute statement", new SQLException("violation"), "insert into asset", kind, "a_constraint"));
	}
}
