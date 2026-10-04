package com.quephoto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Profiles;

@SpringBootApplication
public class QuePhotoApplication {
	public static void main(String[] args) {
		var context = SpringApplication.run(QuePhotoApplication.class, args);
		if (context.getEnvironment().acceptsProfiles(Profiles.of("import"))) {
			int code = SpringApplication.exit(context);
			System.exit(code);
		}
	}
}