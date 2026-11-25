import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Generates the internal JWT (HS256) that a service uses on the Occurrence Module routes (spec 013, research R1).
 *
 * <pre>
 * $env:OCCURRENCE_INTERNAL_JWT_SECRET = "&lt;secret, at least 32 bytes&gt;"
 * java scripts/InternalJwt.java --sub lhamalog-finder --days 90
 * </pre>
 *
 * The secret is read from the environment, never from an argument, and only the token is printed. Options:
 * {@code --sub} (default lhamalog-finder), {@code --days} (default 90), {@code --iss} (default lhamalog-internal) and
 * {@code --aud} (default lhamalog-modulo-ocorrencia), matching the Core defaults.
 */
public class InternalJwt {

	public static void main(String[] args) throws Exception {
		Map<String, String> options = new LinkedHashMap<>(Map.of("--sub", "lhamalog-finder", "--days", "90", "--iss",
				"lhamalog-internal", "--aud", "lhamalog-modulo-ocorrencia"));
		for (int i = 0; i < args.length; i += 2) {
			if (!options.containsKey(args[i]) || i + 1 >= args.length) {
				System.err.println("Uso: java scripts/InternalJwt.java [--sub s] [--days n] [--iss i] [--aud a]");
				System.exit(2);
			}
			options.put(args[i], args[i + 1]);
		}
		String secret = System.getenv("OCCURRENCE_INTERNAL_JWT_SECRET");
		if (secret == null || secret.strip().getBytes(StandardCharsets.UTF_8).length < 32) {
			System.err.println("Defina OCCURRENCE_INTERNAL_JWT_SECRET com pelo menos 32 bytes (o mesmo do Core).");
			System.exit(2);
		}
		Instant now = Instant.now();
		long expires = now.plus(Duration.ofDays(Long.parseLong(options.get("--days")))).getEpochSecond();
		String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
		String payload = "{\"iss\":\"" + json(options.get("--iss")) + "\",\"aud\":[\"" + json(options.get("--aud"))
				+ "\"],\"sub\":\"" + json(options.get("--sub")) + "\",\"iat\":" + now.getEpochSecond() + ",\"nbf\":"
				+ (now.getEpochSecond() - 1) + ",\"exp\":" + expires + "}";
		String unsigned = base64(header.getBytes(StandardCharsets.UTF_8)) + "." + base64(payload.getBytes(StandardCharsets.UTF_8));
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secret.strip().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		System.out.println(unsigned + "." + base64(mac.doFinal(unsigned.getBytes(StandardCharsets.US_ASCII))));
	}

	private static String base64(byte[] bytes) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String json(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

}
