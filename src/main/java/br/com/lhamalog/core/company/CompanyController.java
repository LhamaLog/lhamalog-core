package br.com.lhamalog.core.company;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import br.com.lhamalog.core.company.CompanyResponses.CompanyResponse;
import br.com.lhamalog.core.shared.CompanySession;
import br.com.lhamalog.core.shared.CompanySessionResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/company")
public class CompanyController {
	private final CompanyService service;
	private final CompanySessionResolver sessions;

	public CompanyController(CompanyService service, CompanySessionResolver sessions) {
		this.service = service;
		this.sessions = sessions;
	}

	@GetMapping
	public CompanyResponse get(@AuthenticationPrincipal Jwt jwt) {
		return service.get(session(jwt).tenantId());
	}

	@PutMapping
	public CompanyResponse save(@AuthenticationPrincipal Jwt jwt, @RequestBody String body) {
		CompanySession session = session(jwt);
		return service.save(session.tenantId(), session.userId(), json(body));
	}

	@PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public CompanyResponse uploadLogo(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Long version,
			@RequestParam("file") MultipartFile file) throws java.io.IOException {
		CompanySession session = session(jwt);
		return service.saveLogo(session.tenantId(), session.userId(), version, file.getOriginalFilename(), file.getBytes());
	}

	@DeleteMapping("/logo")
	public CompanyResponse deleteLogo(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Long version) {
		CompanySession session = session(jwt);
		return service.deleteLogo(session.tenantId(), session.userId(), version);
	}

	@GetMapping("/logo")
	public ResponseEntity<byte[]> logo(@AuthenticationPrincipal Jwt jwt) {
		CompanyLogo logo = service.logo(session(jwt).tenantId());
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(logo.contentType()))
				.header("X-Content-Type-Options", "nosniff")
				.header("Content-Disposition", "attachment; filename=\"" + logo.fileName() + "\"")
				.header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
				.cacheControl(CacheControl.noStore()).body(logo.content());
	}

	@GetMapping("/postal-code/{cep}")
	public void postalCode(@AuthenticationPrincipal Jwt jwt, @PathVariable String cep) {
		session(jwt);
		if (cep.isBlank()) {
			throw unavailable("A consulta de CEP não está disponível neste serviço.");
		}
		throw unavailable("A consulta de CEP não está disponível neste serviço.");
	}

	@PostMapping("/actions/export-pdf")
	public void exportPdf(@AuthenticationPrincipal Jwt jwt) {
		session(jwt);
		throw unavailable("A exportação de PDF não está disponível neste serviço.");
	}

	@PostMapping("/actions/sync")
	public void sync(@AuthenticationPrincipal Jwt jwt) {
		session(jwt);
		throw unavailable("A sincronização entre áreas não está disponível neste serviço.");
	}

	private CompanySession session(Jwt jwt) {
		return sessions.resolve(jwt);
	}

	private static ResponseStatusException unavailable(String message) {
		return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, message);
	}

	private static JsonNode json(String body) {
		try {
			return JsonMapper.shared().readTree(body);
		}
		catch (RuntimeException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Os dados enviados são inválidos.");
		}
	}
}
