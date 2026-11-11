package br.com.lhamalog.core.usage.infrastructure;

import br.com.lhamalog.core.usage.application.QuotaReservation;
import br.com.lhamalog.core.usage.application.QuotaReservationRepository;
import br.com.lhamalog.core.usage.application.ReservationState;
import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/** Mongo implementation using conditional updates so parallel reservations cannot overdraw a cycle. */
@Repository
public class MongoQuotaReservationRepository implements QuotaReservationRepository {
	private final MongoTemplate mongo;

	public MongoQuotaReservationRepository(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	@Override
	public Optional<QuotaReservation> find(String reservationId) {
		return Optional.ofNullable(mongo.findById(reservationId, QuotaReservationDocument.class)).map(this::fromDocument);
	}

	@Override
	public boolean reserve(CycleBalance balance, QuotaReservation reservation) {
		String balanceId = balanceId(balance.tenantId(), balance.productId(), balance.cycleStart());
		mongo.upsert(Query.query(Criteria.where("_id").is(balanceId)),
				new Update().setOnInsert("tenantId", balance.tenantId()).setOnInsert("productId", balance.productId())
						.setOnInsert("cycleStart", balance.cycleStart()).setOnInsert("allowanceBytes", balance.allowanceBytes())
						.setOnInsert("reservedBytes", 0L).setOnInsert("consumedBytes", 0L),
				QuotaBalanceDocument.class);
		Document expression = new Document("$expr", new Document("$gte", List.of(
				new Document("$subtract", List.of("$allowanceBytes", new Document("$add", List.of("$reservedBytes", "$consumedBytes")))),
				reservation.requestedBytes())));
		Document criteria = new Document("_id", balanceId);
		criteria.putAll(expression);
		UpdateResult retained = mongo.updateFirst(new BasicQuery(criteria),
				new Update().inc("reservedBytes", reservation.requestedBytes()), QuotaBalanceDocument.class);
		if (retained.getModifiedCount() != 1) return false;
		if (persistReservation(reservation)) {
			return true;
		}
		mongo.updateFirst(Query.query(Criteria.where("_id").is(balanceId)),
				new Update().inc("reservedBytes", -reservation.requestedBytes()), QuotaBalanceDocument.class);
		return false;
	}

	@Override
	public boolean saveUnlimited(QuotaReservation reservation) {
		return persistReservation(reservation);
	}

	/**
	 * Inserts a new reservation or replaces one that already finished
	 * ({@code RELEASED}/{@code EXPIRED}/{@code REFUNDED}) so exporters can reuse the stable id.
	 */
	private boolean persistReservation(QuotaReservation reservation) {
		try {
			mongo.insert(toDocument(reservation));
			return true;
		}
		catch (DuplicateKeyException duplicate) {
			Query terminal = Query.query(Criteria.where("_id").is(reservation.reservationId())
					.and("state").in(ReservationState.RELEASED, ReservationState.EXPIRED, ReservationState.REFUNDED));
			return mongo.findAndReplace(terminal, toDocument(reservation)) != null;
		}
	}

	@Override
	public boolean transition(String reservationId, ReservationState expected, ReservationState next, Instant now) {
		return mongo.updateFirst(Query.query(Criteria.where("_id").is(reservationId).and("state").is(expected)),
				new Update().set("state", next).set("updatedAt", now), QuotaReservationDocument.class).getModifiedCount() == 1;
	}

	@Override
	public boolean moveReservedToConsumed(QuotaReservation reservation, Instant now) {
		if (!transition(reservation.reservationId(), ReservationState.RESERVED, ReservationState.CONFIRMED, now)) return false;
		if (!reservation.unlimited()) {
			mongo.updateFirst(Query.query(Criteria.where("_id").is(balanceId(reservation.tenantId(), reservation.productId(),
					reservation.cycleStart()))), new Update().inc("reservedBytes", -reservation.requestedBytes())
							.inc("consumedBytes", reservation.requestedBytes()), QuotaBalanceDocument.class);
		}
		return true;
	}

	@Override
	public boolean releaseReserved(QuotaReservation reservation, ReservationState next, Instant now) {
		if (!transition(reservation.reservationId(), ReservationState.RESERVED, next, now)) return false;
		if (!reservation.unlimited()) {
			mongo.updateFirst(Query.query(Criteria.where("_id").is(balanceId(reservation.tenantId(), reservation.productId(),
					reservation.cycleStart()))), new Update().inc("reservedBytes", -reservation.requestedBytes()),
					QuotaBalanceDocument.class);
		}
		return true;
	}

	@Override
	public boolean refundConsumed(QuotaReservation reservation, Instant now) {
		if (!transition(reservation.reservationId(), ReservationState.CONFIRMED, ReservationState.REFUNDED, now)) return false;
		if (!reservation.unlimited()) {
			mongo.updateFirst(Query.query(Criteria.where("_id").is(balanceId(reservation.tenantId(), reservation.productId(),
					reservation.cycleStart()))), new Update().inc("consumedBytes", -reservation.requestedBytes()),
					QuotaBalanceDocument.class);
		}
		return true;
	}

	@Override
	public List<QuotaReservation> findExpiredReserved(Instant now) {
		return mongo.find(Query.query(Criteria.where("state").is(ReservationState.RESERVED).and("expiresAt").lt(now)),
				QuotaReservationDocument.class).stream().map(this::fromDocument).toList();
	}

	@Override
	public Optional<CycleBalanceSnapshot> findBalance(String tenantId, String productId, Instant cycleStart) {
		return Optional.ofNullable(mongo.findById(balanceId(tenantId, productId, cycleStart), QuotaBalanceDocument.class))
				.map(value -> new CycleBalanceSnapshot(value.allowanceBytes(), value.reservedBytes(), value.consumedBytes(),
						value.cycleStart()));
	}

	private static String balanceId(String tenantId, String productId, Instant cycleStart) {
		return tenantId + ":" + productId + ":" + cycleStart;
	}

	private static QuotaReservationDocument toDocument(QuotaReservation value) {
		return new QuotaReservationDocument(value.reservationId(), value.tenantId(), value.productId(), value.cycleStart(),
				value.requestedBytes(), value.domain(), value.batchId(), value.unlimited(), value.state(), value.createdAt(),
				value.expiresAt(), value.updatedAt());
	}

	private QuotaReservation fromDocument(QuotaReservationDocument value) {
		return new QuotaReservation(value.reservationId(), value.tenantId(), value.productId(), value.cycleStart(),
				value.requestedBytes(), value.domain(), value.batchId(), value.unlimited(), value.state(), value.createdAt(),
				value.expiresAt(), value.updatedAt());
	}
}
