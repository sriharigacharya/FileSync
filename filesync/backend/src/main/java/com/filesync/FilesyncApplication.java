package com.filesync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.awt.Desktop;
import java.net.URI;

@SpringBootApplication
public class FilesyncApplication {

	public static void main(String[] args) {
		System.setProperty("java.awt.headless", "false");
		SpringApplication.run(FilesyncApplication.class, args);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onReady() {
		String openBrowser = System.getProperty("filesync.open-browser", "true");
		if ("true".equalsIgnoreCase(openBrowser)) {
			try {
				if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
					Desktop.getDesktop().browse(new URI("http://localhost:8080"));
				}
			} catch (Exception ignored) {
			}
		}
	}

}
