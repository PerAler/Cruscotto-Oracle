package com.example.cruscotto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Path;

@SpringBootApplication
@EnableScheduling
public class CruscottoOracleApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(CruscottoOracleApplication.class);
    }

    public static void main(String[] args) {
        System.out.println("[STARTUP] Working directory (user.dir): " + System.getProperty("user.dir"));
        System.out.println("[STARTUP] Config file atteso: " + Path.of("config", "cruscotto-oracle-connections.json").toAbsolutePath());
        ConfigurableApplicationContext context = SpringApplication.run(CruscottoOracleApplication.class, args);
        openBrowser(context);
    }

    private static void openBrowser(ConfigurableApplicationContext context) {
        try {
            String port = context.getEnvironment().getProperty("server.port", "8090");
            String url = "http://localhost:" + port + "/editor";
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
            new ProcessBuilder("cmd", "/c", "start", "", url).start();
        } catch (Exception ex) {
            System.out.println("Apri manualmente il browser su http://localhost:8090/editor");
        }
    }
}
