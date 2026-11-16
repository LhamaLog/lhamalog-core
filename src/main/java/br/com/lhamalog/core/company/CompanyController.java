package br.com.lhamalog.core.company;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
	public CompanyResponse get() {
		return service.get(session().tenantId());
	}

	@PutMapping
	public CompanyResponse save(@RequestBody String body) {
		CompanySession session = requireAdministrator();
		return service.save(session.tenantId(), session.userId(), json(body));
	}

	@DeleteMapping
	public CompanyResponse deleteRegistration() {
		CompanySession session = requireAdministrator();
		return service.deleteRegistration(session.tenantId());
	}

	@PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public CompanyResponse uploadLogo(@RequestParam(required = false) Long version,
			@RequestParam(value = "file", required = false) MultipartFile file) throws java.io.IOException {
		CompanySession session = requireAdministrator();
		if (file == null || file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie o arquivo do logotipo.");
		}
		return service.saveLogo(session.tenantId(), session.userId(), version, file.getOriginalFilename(), file.getBytes());
	}

	@DeleteMapping("/logo")
	public CompanyResponse deleteLogo(@RequestParam(required = false) Long version) {
		CompanySession session = requireAdministrator();
		return service.deleteLogo(session.tenantId(), session.userId(), version);
	}

	@GetMapping("/logo")
	public ResponseEntity<byte[]> logo() {
		CompanyLogo logo = service.logo(session().tenantId());
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(logo.contentType()))
				.header("X-Content-Type-Options", "nosniff")
				.header("Content-Disposition", "attachment; filename=\"" + logo.fileName() + "\"")
				.header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
				.cacheControl(CacheControl.noStore()).body(logo.content());
	}

	@GetMapping("/context/revisions")
	public CompanyContextViews.RevisionList contextRevisions() {
		return CompanyContextViews.RevisionList.from(service.contextRevisions(session().tenantId()));
	}

	@GetMapping("/context/revisions/{revision}")
	public CompanyContextViews.Revision contextRevision(@PathVariable long revision) {
		return CompanyContextViews.Revision.from(service.contextRevision(session().tenantId(), revision));
	}

	@GetMapping("/context/prompt")
	public CompanyContextPrompt contextPrompt() {
		return service.contextPrompt(session().tenantId());
	}

	@GetMapping("/postal-code/{cep}")
	public void postalCode(@PathVariable String cep) {
		session();
		if (cep.isBlank()) {
			throw unavailable("A consulta de CEP não está disponível neste serviço.");
		}
		throw unavailable("A consulta de CEP não está disponível neste serviço.");
	}

	@PostMapping("/actions/export-pdf")
	public void exportPdf() {
		session();
		throw unavailable("A exportação de PDF não está disponível neste serviço.");
	}

	@PostMapping("/actions/sync")
	public void sync() {
		session();
		throw unavailable("A sincronização entre áreas não está disponível neste serviço.");
	}

	private CompanySession session() {
		return sessions.current();
	}

	private CompanySession requireAdministrator() {
		CompanySession session = session();
		if (!session.administrator()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"Somente o administrador da empresa pode alterar este cadastro.");
		}
		return session;
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
