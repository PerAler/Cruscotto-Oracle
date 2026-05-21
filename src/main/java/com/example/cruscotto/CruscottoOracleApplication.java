package com.example.cruscotto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.Console;
import java.io.IOException;
import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.net.URI;

@SpringBootApplication
@EnableScheduling
public class CruscottoOracleApplication extends SpringBootServletInitializer {

    private static final Path LOCAL_CONFIG_FILE = Path.of("config", "cruscotto-oracle-local.properties");

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(CruscottoOracleApplication.class);
    }

    public static void main(String[] args) {
        loadLocalDatasourceOverrides();
        promptForDatasourceOverrides();
        ConfigurableApplicationContext context = SpringApplication.run(CruscottoOracleApplication.class, args);
        openBrowser(context);
    }

    private static void promptForDatasourceOverrides() {
        Console console = System.console();
        if (console == null) {
            return;
        }

        String currentUrl = firstNonBlank(
                System.getProperty("spring.datasource.url"),
                System.getenv("ORACLE_JDBC_URL"),
                "jdbc:oracle:thin:@//HOST:1521/SERVICE_NAME"
        );
        String currentUser = firstNonBlank(
                System.getProperty("spring.datasource.username"),
                System.getenv("ORACLE_JDBC_USERNAME"),
                "utente"
        );
        String currentPassword = firstNonBlank(
                System.getProperty("spring.datasource.password"),
                System.getenv("ORACLE_JDBC_PASSWORD"),
                "password"
        );

        console.printf("%n=== Configurazione connessione Oracle ===%n");
        console.printf("Ti verranno chiesti URL, username e password JDBC.%n");
        console.printf("Premi INVIO per mantenere il valore corrente.%n");
        console.printf("Esempio URL: jdbc:oracle:thin:@//host:1521/service%n%n");

        String url = readLine(console, "1) URL JDBC", currentUrl);
        String username = readLine(console, "2) Username JDBC", currentUser);
        String password = readPassword(console, "3) Password JDBC", currentPassword);

        setIfNotBlank("spring.datasource.url", url);
        setIfNotBlank("spring.datasource.username", username);
        setIfNotBlank("spring.datasource.password", password);
        persistLocalDatasourceOverrides(url, username, password);
    }

    private static String readLine(Console console, String label, String currentValue) {
        String prompt = String.format("%s [%s]: ", label, currentValue);
        String value = console.readLine(prompt);
        if (value == null || value.isBlank()) {
            return currentValue;
        }
        return value.trim();
    }

    private static String readPassword(Console console, String label, String currentValue) {
        char[] value = console.readPassword("%s [nascosta, INVIO per mantenere il valore]: ", label);
        if (value == null || value.length == 0) {
            return currentValue;
        }
        String password = new String(value);
        java.util.Arrays.fill(value, '\0');
        return password;
    }

    private static void setIfNotBlank(String key, String value) {
        if (value != null && !value.isBlank()) {
            System.setProperty(key, value);
        }
    }

    private static void loadLocalDatasourceOverrides() {
        if (!Files.exists(LOCAL_CONFIG_FILE)) {
            return;
        }

        Properties properties = new Properties();
        try (var inputStream = Files.newInputStream(LOCAL_CONFIG_FILE)) {
            properties.load(inputStream);
            setIfNotBlank("spring.datasource.url", properties.getProperty("spring.datasource.url"));
            setIfNotBlank("spring.datasource.username", properties.getProperty("spring.datasource.username"));
            setIfNotBlank("spring.datasource.password", properties.getProperty("spring.datasource.password"));
        } catch (IOException ex) {
            System.err.println("Impossibile leggere la configurazione locale Oracle: " + ex.getMessage());
        }
    }

    private static void persistLocalDatasourceOverrides(String url, String username, String password) {
        Properties properties = new Properties();
        properties.setProperty("spring.datasource.url", url);
        properties.setProperty("spring.datasource.username", username);
        properties.setProperty("spring.datasource.password", password);

        try {
            Files.createDirectories(LOCAL_CONFIG_FILE.getParent());
            try (var outputStream = Files.newOutputStream(LOCAL_CONFIG_FILE)) {
                properties.store(outputStream, "Cruscotto Oracle local datasource overrides");
            }
        } catch (IOException ex) {
            System.err.println("Impossibile salvare la configurazione locale Oracle: " + ex.getMessage());
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static void openBrowser(ConfigurableApplicationContext context) {
        try {
            String port = context.getEnvironment().getProperty("server.port", "8090");
            String url = "http://localhost:" + port + "/dashboard";
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
            new ProcessBuilder("cmd", "/c", "start", "", url).start();
        } catch (Exception ex) {
            System.out.println("Apri manualmente il browser su http://localhost:8090/dashboard");
        }
    }
}
