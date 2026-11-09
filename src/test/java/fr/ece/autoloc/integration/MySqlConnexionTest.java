package fr.ece.autoloc.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the application against a real MySQL server and checks that Hibernate created the schema.
 * Only runs when AUTOLOC_DB_URL is set: the Jenkins pipeline starts a disposable MySQL container for it.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=${AUTOLOC_DB_URL}",
        "spring.datasource.username=${AUTOLOC_DB_USER:root}",
        "spring.datasource.password=${AUTOLOC_DB_PASSWORD}",
        "spring.jpa.show-sql=false"
})
@EnabledIfEnvironmentVariable(named = "AUTOLOC_DB_URL", matches = ".+")
class MySqlConnexionTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void laBaseRepond() {
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.");
    }

    @Test
    void leSchemaEstCree() {
        List<String> tables = jdbc.queryForList(
                "SELECT LOWER(table_name) FROM information_schema.tables WHERE table_schema = DATABASE()", String.class);
        assertThat(tables).contains("agence", "client", "vehicule", "reservation", "contrat", "paiement");
    }
}
