package br.com.lhamalog.core.profile;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.com.lhamalog.core.shared.CompanySessionResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/user/profile")
public class UserProfileController {
	private final UserProfileService service;
	private final CompanySessionResolver sessions;

	public UserProfileController(UserProfileService service, CompanySessionResolver sessions) {
		this.service = service;
		this.sessions = sessions;
	}

	@GetMapping
	public UserProfileView get(@AuthenticationPrincipal Jwt jwt) {
		return service.get(sessions.resolve(jwt).userId());
	}

	@PutMapping
	public UserProfileView save(@AuthenticationPrincipal Jwt jwt, @RequestBody String body) {
		String userId = sessions.resolve(jwt).userId();
		return service.save(userId, json(body));
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
