package com.secureai.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Render provides DATABASE_URL as postgresql://user:pass@hostname:port/db.
 * Using the full hostname avoids UnknownHostException from short internal hostnames.
 */
@Configuration
@ConditionalOnProperty(name = "DATABASE_URL")
public class RenderDatabaseConfig {

    @Bean
    @Primary
    public DataSource renderDataSource(@Value("${DATABASE_URL}") String databaseUrl) {
        String normalized = databaseUrl.startsWith("jdbc:") ? databaseUrl.substring(5) : databaseUrl;
        URI uri = URI.create(normalized);

        String[] userInfo = uri.getUserInfo() != null ? uri.getUserInfo().split(":", 2) : new String[]{"", ""};
        String username = URLDecoder.decode(userInfo[0], StandardCharsets.UTF_8);
        String password = userInfo.length > 1
                ? URLDecoder.decode(userInfo[1], StandardCharsets.UTF_8)
                : "";

        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(5);
        return new HikariDataSource(config);
    }
}
