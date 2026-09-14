package com.secureai;

import com.secureai.config.MailProperties;
import com.secureai.config.OAuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({OAuthProperties.class, MailProperties.class})
public class SecureAiApplication {

	public static void main(String[] args) {
		SpringApplication.run(SecureAiApplication.class, args);
	}

}
