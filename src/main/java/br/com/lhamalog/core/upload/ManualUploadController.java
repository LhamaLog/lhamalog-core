package br.com.lhamalog.core.upload;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.lhamalog.core.shared.CompanySessionResolver;
import br.com.lhamalog.core.upload.ManualUploadResponses.Body;

/** Public manual upload: {@code POST /api/manual/upload}, authenticated with a Clerk session token. */
@RestController
@RequestMapping("/api/manual")
public class ManualUploadController {

	private final ManualUploadService service;

	private final CompanySessionResolver sessions;

	public ManualUploadController(ManualUploadService service, CompanySessionResolver sessions) {
		this.service = service;
		this.sessions = sessions;
	}

	@PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Body> upload(@RequestParam("productId") String productId,
			@RequestParam("files") List<MultipartFile> files) {
		return service.upload(sessions.current(), productId, files);
	}
}
