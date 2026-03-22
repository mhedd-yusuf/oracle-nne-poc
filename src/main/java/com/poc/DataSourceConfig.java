package com.poc;

import oracle.jdbc.pool.OracleDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Defines the OracleDataSource bean with Native Network Encryption (NNE) properties.
 *
 * Why a custom @Bean instead of Spring Boot auto-configuration?
 *   Spring Boot's DataSourceAutoConfiguration builds a HikariCP pool by default.
 *   NNE connection properties must be set directly on OracleDataSource before
 *   the first connection is made — they cannot be injected through a pool layer.
 *   Providing this @Bean causes Spring Boot to back off from auto-configuring
 *   its own DataSource, so JdbcTemplate is wired to our OracleDataSource instead.
 *
 * Property binding:
 *   spring.datasource.* — standard Spring Boot datasource properties
 *   nne.*               — custom NNE properties defined in application.properties
 */
@Configuration
public class DataSourceConfig {

    // Standard Spring Boot datasource keys
    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    // NNE client-side negotiation settings (defaults to ACCEPTED so the app
    // still starts when nne.* properties are omitted from application.properties)
    @Value("${nne.encryption.client:ACCEPTED}")
    private String encryptionClient;

    @Value("${nne.encryption.types:(AES256)}")
    private String encryptionTypes;

    @Value("${nne.checksum.client:ACCEPTED}")
    private String checksumClient;

    @Value("${nne.checksum.types:(SHA256)}")
    private String checksumTypes;

    /**
     * Creates an OracleDataSource with NNE connection properties.
     *
     * OracleDataSource setter methods (setURL, setUser, setPassword,
     * setConnectionProperties) declare throws SQLException, so the @Bean
     * method declares it too — Spring handles checked exceptions from @Bean
     * factory methods transparently.
     */
    @Bean
    public DataSource dataSource() throws SQLException {
        OracleDataSource ds = new OracleDataSource();
        ds.setURL(url);
        ds.setUser(username);
        ds.setPassword(password);

        /*
         * NNE is negotiated during the TNS handshake, before any SQL is sent.
         * These four properties control what the client proposes to the server:
         *
         *   oracle.net.encryption_client       — willingness to encrypt
         *   oracle.net.encryption_types_client — preferred cipher list
         *   oracle.net.crypto_checksum_client       — willingness to checksum
         *   oracle.net.crypto_checksum_types_client — preferred hash algorithm
         */
        Properties nne = new Properties();
        nne.setProperty("oracle.net.encryption_client",             encryptionClient);
        nne.setProperty("oracle.net.encryption_types_client",       encryptionTypes);
        nne.setProperty("oracle.net.crypto_checksum_client",        checksumClient);
        nne.setProperty("oracle.net.crypto_checksum_types_client",  checksumTypes);
        ds.setConnectionProperties(nne);

        return ds;
    }
}
