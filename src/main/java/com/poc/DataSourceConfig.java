package com.poc;

import oracle.jdbc.OracleConnection;
import oracle.jdbc.pool.OracleDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Defines the OracleDataSource bean with NNE settings passed as connection properties.
 *
 * Why use setConnectionProperties() instead of embedding in the TNS descriptor?
 *   oracle.jdbc.pool.OracleDataSource uses the Oracle JDBC Thin driver, which is a
 *   pure-Java reimplementation of Oracle Net. Unlike the OCI driver (used by sqlplus),
 *   the Thin driver does NOT read NNE settings from the SECURITY section of a TNS
 *   connection descriptor. Embedding (SECURITY=(ENCRYPTION_CLIENT=REJECTED)...) in the
 *   URL is silently ignored for NNE purposes.
 *
 *   The Thin driver reads NNE settings from Java connection properties with the keys:
 *     oracle.net.encryption_client       — REQUIRED | REQUESTED | ACCEPTED | REJECTED
 *     oracle.net.encryption_types_client — algorithm list, e.g. (AES256)
 *     oracle.net.crypto_checksum_client
 *     oracle.net.crypto_checksum_types_client
 *
 *   These are passed via OracleDataSource.setConnectionProperties() and are applied
 *   during the TNS handshake when each connection is opened.
 */
@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.host}")
    private String host;

    @Value("${spring.datasource.port}")
    private String port;

    @Value("${spring.datasource.service}")
    private String service;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${app.name:oracle-app}")
    private String appName;

    @Value("${nne.encryption.client:ACCEPTED}")
    private String encryptionClient;

    @Value("${nne.encryption.types:(AES256)}")
    private String encryptionTypes;

    @Value("${nne.checksum.client:ACCEPTED}")
    private String checksumClient;

    @Value("${nne.checksum.types:(SHA256)}")
    private String checksumTypes;

    @Bean
    public DataSource dataSource() throws SQLException {
        String url = String.format("jdbc:oracle:thin:@//%s:%s/%s", host, port, service);
        //String url = "jdbc:oracle:thin:@//localhost:1521/FREEPDB1?oracle.net.encryption_client=REQUIRED&oracle.net.encryption_types_client=AES256&oracle.net.crypto_checksum_client=REQUIRED&oracle.net.crypto_checksum_types_client=SHA256";

        OracleDataSource ds = new OracleDataSource();
        ds.setURL(url);
        ds.setUser(username);
        ds.setPassword(password);

        Properties props = new Properties();
        // NNE client settings — read by the JDBC Thin driver during the TNS handshake
        props.setProperty("oracle.net.encryption_client",       encryptionClient);
        props.setProperty("oracle.net.encryption_types_client", encryptionTypes);
        props.setProperty("oracle.net.crypto_checksum_client",       checksumClient);
        props.setProperty("oracle.net.crypto_checksum_types_client", checksumTypes);
        // Sets V$SESSION.PROGRAM to identify this app in DB monitoring queries
        props.setProperty("v$session.program", appName);
        ds.setConnectionProperties(props);

        return ds;
    }
}
