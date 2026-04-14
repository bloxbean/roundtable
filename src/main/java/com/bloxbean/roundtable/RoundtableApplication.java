package com.bloxbean.roundtable;

import com.bloxbean.roundtable.config.HibernateAnnotationRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints(HibernateAnnotationRuntimeHints.class)
public class RoundtableApplication {

	public static void main(String[] args) {
		SpringApplication.run(RoundtableApplication.class, args);
	}

}
