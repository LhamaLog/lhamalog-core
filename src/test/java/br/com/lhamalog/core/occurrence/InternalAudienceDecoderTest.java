package br.com.lhamalog.core.occurrence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import br.com.lhamalog.core.config.InternalServiceSecurityConfigurationAccess;
import br.com.lhamalog.core.occurrence.application.OccurrenceModuleProperties;

/** Spec 016: {@code aud} is accepted when it contains the audience required by the route. */
class InternalAudienceDecoderTest {

	@Test
	void eachDecoderRequiresItsOwnAudienceByContains() {
		var auth = new OccurrenceModuleProperties.InternalAuth(List.of(InternalTokens.CURRENT_SECRET), null, null, null,
				null);
		JwtDecoder module = InternalServiceSecurityConfigurationAccess.decoder(auth, "lhamalog-modulo-ocorrencia");
		JwtDecoder core = InternalServiceSecurityConfigurationAccess.decoder(auth, "lhamalog-core");
		String both = InternalTokens.finder()
			.claim("aud", List.of("lhamalog-modulo-ocorrencia", "lhamalog-core"))
			.sign();
		String onlyCore = InternalTokens.finder().claim("aud", List.of("lhamalog-core")).sign();
		String onlyModule = InternalTokens.finder().sign();

		assertThat(module.decode(both).getAudience()).contains("lhamalog-modulo-ocorrencia", "lhamalog-core");
		assertThat(core.decode(both).getAudience()).contains("lhamalog-core");
		assertThat(core.decode(onlyCore).getAudience()).containsExactly("lhamalog-core");
		assertThat(module.decode(onlyModule).getAudience()).containsExactly("lhamalog-modulo-ocorrencia");
		assertThatThrownBy(() -> module.decode(onlyCore)).isInstanceOf(JwtException.class);
		assertThatThrownBy(() -> core.decode(onlyModule)).isInstanceOf(JwtException.class);
	}

}
