package br.com.lhamalog.core.member;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import br.com.lhamalog.core.shared.CompanySessionResolver;

@Configuration
public class MemberAccessConfig {
	@Bean
	@ConditionalOnBean(CompanyMemberService.class)
	WebMvcConfigurer memberAccessWebMvc(CompanySessionResolver sessions, CompanyMemberService members) {
		MemberAccessInterceptor interceptor = new MemberAccessInterceptor(sessions, members);
		return new WebMvcConfigurer() {
			@Override
			public void addInterceptors(InterceptorRegistry registry) {
				registry.addInterceptor(interceptor).addPathPatterns("/api/**");
			}
		};
	}
}
