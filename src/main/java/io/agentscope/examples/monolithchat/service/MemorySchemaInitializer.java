package io.agentscope.examples.monolithchat.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class MemorySchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public MemorySchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS user_memory_tag (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                        "user_id VARCHAR(64) NOT NULL," +
                        "tag_key VARCHAR(128) NOT NULL," +
                        "tag_value VARCHAR(255) NOT NULL," +
                        "weight DOUBLE DEFAULT 1," +
                        "source VARCHAR(128) DEFAULT ''," +
                        "updated_time DATETIME NOT NULL," +
                        "INDEX idx_user_updated (user_id, updated_time)," +
                        "UNIQUE KEY uk_user_tag (user_id, tag_key, tag_value)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS user_behavior_memory (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                        "user_id VARCHAR(64) NOT NULL," +
                        "session_id VARCHAR(128) NOT NULL," +
                        "behavior_type VARCHAR(128) NOT NULL," +
                        "behavior_value TEXT," +
                        "trigger_message_id VARCHAR(128) DEFAULT ''," +
                        "create_time DATETIME NOT NULL," +
                        "INDEX idx_user_time (user_id, create_time)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }
}
