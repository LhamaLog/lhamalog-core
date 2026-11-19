package br.com.lhamalog.core.dashboard.infrastructure;

import br.com.lhamalog.core.dashboard.application.DashboardAggregateRepository;
import br.com.lhamalog.core.dashboard.application.LogCountBucket;
import br.com.lhamalog.core.dashboard.application.LogSeverity;
import br.com.lhamalog.core.product.Product;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MongoDashboardAggregateRepository implements DashboardAggregateRepository {

	private final MongoTemplate mongo;

	public MongoDashboardAggregateRepository(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	@Override
	public void upsert(LogCountBucket bucket, String tenantId, String productId, Instant consolidatedAt) {
		String id = DashboardLogHourlyAggregateDocument.composeId(tenantId, productId, bucket.severity().name(),
				bucket.serviceName(), bucket.hourStart());
		Query query = Query.query(Criteria.where("_id").is(id));
		Update update = new Update()
				.set("tenantId", tenantId)
				.set("productId", productId)
				.set("severity", bucket.severity().name())
				.set("serviceName", bucket.serviceName())
				.set("hourStart", bucket.hourStart())
				.set("count", bucket.count())
				.set("consolidatedAt", consolidatedAt)
				.set("schemaVersion", DashboardLogHourlyAggregateDocument.SCHEMA_VERSION);
		mongo.upsert(query, update, DashboardLogHourlyAggregateDocument.class);
	}

	@Override
	public List<StoredAggregate> findInRange(String tenantId, String productId, Instant fromInclusive,
			Instant toExclusive) {
		Query query = Query.query(Criteria.where("tenantId").is(tenantId)
				.and("productId").is(productId)
				.and("hourStart").gte(fromInclusive).lt(toExclusive));
		return mongo.find(query, DashboardLogHourlyAggregateDocument.class).stream()
				.map(MongoDashboardAggregateRepository::toStored)
				.toList();
	}

	@Override
	public Optional<Instant> findWatermark(String tenantId) {
		DashboardAggregationWatermarkDocument document = mongo.findById(tenantId,
				DashboardAggregationWatermarkDocument.class);
		return document == null ? Optional.empty() : Optional.ofNullable(document.dadosConsolidadosAte());
	}

	@Override
	public void saveWatermark(String tenantId, Instant dadosConsolidadosAte, Instant updatedAt) {
		mongo.save(new DashboardAggregationWatermarkDocument(tenantId, dadosConsolidadosAte, updatedAt));
	}

	@Override
	public List<ProductScope> listActiveProductScopes() {
		Query query = Query.query(Criteria.where("archivedAt").isNull());
		query.fields().include("_id").include("tenantId");
		return mongo.find(query, Product.class).stream()
				.map(product -> new ProductScope(product.tenantId(), product.id()))
				.toList();
	}

	private static StoredAggregate toStored(DashboardLogHourlyAggregateDocument document) {
		return new StoredAggregate(document.tenantId(), document.productId(),
				LogSeverity.valueOf(document.severity()), document.serviceName(), document.hourStart(),
				document.count(), document.consolidatedAt());
	}
}
