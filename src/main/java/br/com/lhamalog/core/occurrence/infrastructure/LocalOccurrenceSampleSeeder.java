package br.com.lhamalog.core.occurrence.infrastructure;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import br.com.lhamalog.core.product.Product;

/**
 * Local profile only: sample occurrences for every product, so the occurrence screen works before the Occurrence
 * Module writes real ones (spec 012, US4). Each sample has a fixed id and is written with {@code setOnInsert}, so a
 * new startup adds nothing and never changes an existing document. All texts are fictitious and already masked.
 */
public class LocalOccurrenceSampleSeeder {

	static final int SAMPLES_PER_PRODUCT = 12;

	private static final Logger log = LoggerFactory.getLogger(LocalOccurrenceSampleSeeder.class);

	private static final String HOST = "host-local-1";

	private record Sample(String type, String status, String message, String service, String title, String summary,
			boolean recurring, long count, long firstHoursAgo, long lastHoursAgo, List<String> lines) {}

	private static final List<Sample> SAMPLES = List.of(
			new Sample("ERROR", "ABERTA", "jwt token is expired for user <EMAIL>", "auth-service",
					"Falha na autenticação de token", "Tokens JWT expirados em várias requisições do auth-service.",
					true, 128, 240, 1, List.of(
							"ERROR [auth-service] JWT token is expired for user <EMAIL>",
							"ERROR [auth-service] JWT token is expired for user <EMAIL> (token <TOKEN>)")),
			new Sample("ERROR", "EM_ANALISE", "connection timeout after <NUM>s to <IP>", "user-service",
					"Erro ao conectar com o banco", "Timeout na conexão com o banco de dados de usuários.", false, 42,
					72, 3, List.of("ERROR [user-service] Connection timeout after 30s to <IP>")),
			new Sample("ERROR", "FECHADA", "nullpointerexception in orderservice.calculate", "order-service", null,
					null, false, 7, 400, 300, List.of(
							"ERROR [order-service] java.lang.NullPointerException at OrderService.calculate(OrderService.java:<NUM>)")),
			new Sample("WARNING", "ABERTA", "queue backlog above <NUM> messages", "queue-worker",
					"Fila com alto volume de mensagens", "Mais de 10.000 mensagens na fila de processamento.", true,
					310, 48, 0, List.of("WARN [queue-worker] Queue backlog above 10000 messages")),
			new Sample("WARNING", "ABERTA", "response time above <NUM>ms", "payment-service",
					"Latência acima do esperado", "Tempo de resposta médio acima de 2 s nos últimos minutos.", false,
					56, 20, 2, List.of("WARN [payment-service] Response time above 2000ms on /api/pay")),
			new Sample("WARNING", "EM_ANALISE", "retrying request to <IP> attempt <NUM>", "notification", null,
					null, false, 19, 30, 10, List.of("WARN [notification] Retrying request to <IP> attempt 3")),
			new Sample("WARNING", "FECHADA", "disk usage above <NUM>%", "storage", "Uso de disco alto", null, false,
					4, 500, 480, List.of()),
			new Sample("INFO", "ABERTA", "user <EMAIL> registered", "frontend", "Novo usuário registrado",
					"Cadastro concluído com sucesso.", false, 3, 5, 5, List.of(
							"INFO [frontend] User <EMAIL> registered")),
			new Sample("INFO", "FECHADA", "deploy <NUM> finished", "auth-service", "Deploy concluído com sucesso",
					"Versão nova implantada em produção.", false, 1, 100, 100, List.of(
							"INFO [auth-service] Deploy 143 finished")),
			new Sample("INFO", "EM_ANALISE", "login from new device for user <EMAIL>", "auth-service", null,
					"Login de dispositivo novo.", true, 22, 160, 6, List.of(
							"INFO [auth-service] Login from new device for user <EMAIL>")),
			new Sample("ERROR", "ABERTA", "payment gateway returned <NUM> \\| retry later", "payment-service",
					null, null, true, 64, 36, 0, List.of(
							"ERROR [payment-service] Payment gateway returned 503 | retry later")),
			new Sample("WARNING", "ABERTA", "certificate expires in <NUM> days", "api-gateway",
					"Certificado perto de expirar", "O certificado TLS expira em menos de 15 dias.", false, 9, 700,
					24, List.of("WARN [api-gateway] Certificate expires in 14 days")));

	private final MongoTemplate mongo;

	public LocalOccurrenceSampleSeeder(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	/** @return how many occurrences were created now, over all products */
	public int seed() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		int created = 0;
		for (Product product : mongo.findAll(Product.class)) {
			int inserted = 0;
			for (int index = 0; index < SAMPLES.size(); index++) {
				if (upsert(product, index, SAMPLES.get(index), now)) inserted++;
			}
			created += inserted;
			log.info("event=occurrence.sample.seeded | Sample occurrences ready | tenantId={} productId={} inserted={}",
					product.tenantId(), product.id(), inserted);
		}
		return created;
	}

	private boolean upsert(Product product, int index, Sample sample, Instant now) {
		String id = "sample-" + product.id() + "-" + index;
		Instant firstSeen = now.minus(Duration.ofHours(sample.firstHoursAgo()));
		Instant lastSeen = now.minus(Duration.ofHours(sample.lastHoursAgo()));
		Map<String, String> extracted = new LinkedHashMap<>();
		extracted.put("http.route", "/api/v1/" + sample.service());
		List<OccurrenceDocument.StatusChange> history = "ABERTA".equals(sample.status())
				? List.of(created(firstSeen))
				: List.of(created(firstSeen), new OccurrenceDocument.StatusChange("ABERTA", sample.status(),
						firstSeen.plus(Duration.ofMinutes(30)),
						new OccurrenceDocument.ChangedBy("USER", "user_local_sample")));
		Update update = new Update()
			.setOnInsert("tenantId", product.tenantId())
			.setOnInsert("productId", product.id())
			.setOnInsert("signature", sample.type() + "|" + sample.message() + "|" + sample.service() + "|" + HOST)
			.setOnInsert("type", sample.type())
			.setOnInsert("title", sample.title())
			.setOnInsert("summary", sample.summary())
			.setOnInsert("status", sample.status())
			.setOnInsert("recurring", sample.recurring())
			.setOnInsert("count", sample.count())
			.setOnInsert("firstSeenAt", firstSeen)
			.setOnInsert("lastSeenAt", lastSeen)
			.setOnInsert("samples", sample.lines())
			.setOnInsert("hostName", HOST)
			.setOnInsert("traceId", "trace-sample-" + index)
			.setOnInsert("extractedFields", extracted)
			.setOnInsert("sensitiveData", sample.lines().stream().anyMatch(line -> line.contains("<")))
			.setOnInsert("rulesVersion", "regras-local-v1")
			.setOnInsert("revision", id + "-r1")
			.setOnInsert("statusHistory", mongo.getConverter().convertToMongoType(history));
		return mongo.upsert(Query.query(Criteria.where("_id").is(id)), update, OccurrenceDocument.class)
			.getUpsertedId() != null;
	}

	private static OccurrenceDocument.StatusChange created(Instant at) {
		return new OccurrenceDocument.StatusChange(null, "ABERTA", at,
				new OccurrenceDocument.ChangedBy("SYSTEM", "modulo-ocorrencia"));
	}
}
