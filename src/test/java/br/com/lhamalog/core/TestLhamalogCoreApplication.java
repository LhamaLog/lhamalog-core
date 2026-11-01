package br.com.lhamalog.core;

import org.springframework.boot.SpringApplication;

public class TestLhamalogCoreApplication {

	public static void main(String[] args) {
		SpringApplication.from(LhamalogCoreApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
