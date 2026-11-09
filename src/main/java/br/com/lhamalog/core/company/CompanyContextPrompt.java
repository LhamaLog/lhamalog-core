package br.com.lhamalog.core.company;

public record CompanyContextPrompt(String prompt, boolean executed) {
	public static CompanyContextPrompt from(CompanyContextSnapshot context) {
		return new CompanyContextPrompt(context.toPromptSection(), false);
	}
}
