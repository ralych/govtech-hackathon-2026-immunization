package ch.hl7.vacd.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
//@SpringBootApplication(excludeName = {
//		"org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration",
//		"org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
//		"org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
//		"org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
//		"org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
//})
@SpringBootApplication
public class ChVacdApiReferenceServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChVacdApiReferenceServerApplication.class, args);
	}

}
