package br.com.lhamalog.core.logging;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void keepsValidHeaderAndEchoesIt() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(CorrelationId.HEADER, "req-abc_123");
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<String> mdcDuring = new AtomicReference<>();

		filter().doFilter(request, response, capturingChain(mdcDuring));

		assertThat(mdcDuring.get()).isEqualTo("req-abc_123");
		assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("req-abc_123");
		assertThat(MDC.get(CorrelationId.KEY)).isNull();
	}

	@Test
	void replacesInvalidHeader() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(CorrelationId.HEADER, "bad id with spaces!");
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<String> mdcDuring = new AtomicReference<>();

		filter().doFilter(request, response, capturingChain(mdcDuring));

		assertThat(mdcDuring.get()).isNotBlank().matches("^[A-Za-z0-9._-]{1,64}$")
				.isNotEqualTo("bad id with spaces!");
		assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo(mdcDuring.get());
		assertThat(MDC.get(CorrelationId.KEY)).isNull();
	}

	@Test
	void generatesWhenMissing() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<String> mdcDuring = new AtomicReference<>();

		filter().doFilter(request, response, capturingChain(mdcDuring));

		assertThat(mdcDuring.get()).isNotBlank().matches("^[A-Za-z0-9._-]{1,64}$");
		assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo(mdcDuring.get());
		assertThat(MDC.get(CorrelationId.KEY)).isNull();
	}

	@Test
	void clearsMdcAfterChainEvenWhenChainFails() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(CorrelationId.HEADER, "keep-me");
		MockHttpServletResponse response = new MockHttpServletResponse();

		try {
			filter().doFilter(request, response, (req, res) -> {
				assertThat(MDC.get(CorrelationId.KEY)).isEqualTo("keep-me");
				throw new ServletException("boom");
			});
		}
		catch (ServletException expected) {
			assertThat(expected.getMessage()).isEqualTo("boom");
		}

		assertThat(MDC.get(CorrelationId.KEY)).isNull();
		assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("keep-me");
	}

	private static CorrelationIdFilter filter() {
		return new CorrelationIdFilter();
	}

	private static FilterChain capturingChain(AtomicReference<String> mdcDuring) {
		return (request, response) -> mdcDuring.set(MDC.get(CorrelationId.KEY));
	}
}
