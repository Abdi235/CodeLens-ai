package com.secureai;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableRabbit
public class SecureAiApplication {

	public static void main(String[] args) {
		SpringApplication.run(SecureAiApplication.class, args);
	}

}
