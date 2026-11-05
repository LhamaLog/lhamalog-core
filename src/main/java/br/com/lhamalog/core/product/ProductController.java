package br.com.lhamalog.core.product;

import java.util.List;
import br.com.lhamalog.core.shared.CompanySessionResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {
	private final ProductService service;
	private final CompanySessionResolver sessions;
	public ProductController(ProductService service, CompanySessionResolver sessions) {
		this.service = service;
		this.sessions = sessions;
	}
	public record CreateProduct(@NotBlank @Size(max = 200) String name, @Size(max = 10000) String description,
			@Size(max = 200) String category, @Size(max = 200) String productVersion,
			@NotNull @Valid Product.Context context) {}
	public record UpdateContext(@NotNull @Valid Product.Context context, @NotNull Long version) {}

	@GetMapping
	public List<Product> list(@AuthenticationPrincipal Jwt jwt) { return service.list(sessions.resolve(jwt)); }
	@GetMapping("/{id}")
	public Product get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
		return service.get(sessions.resolve(jwt), id);
	}
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Product create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateProduct request) {
		return service.create(sessions.resolve(jwt), request);
	}
	@PutMapping("/{id}/context")
	public Product updateContext(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
			@Valid @RequestBody UpdateContext request) {
		return service.updateContext(sessions.resolve(jwt), id, request);
	}
}
