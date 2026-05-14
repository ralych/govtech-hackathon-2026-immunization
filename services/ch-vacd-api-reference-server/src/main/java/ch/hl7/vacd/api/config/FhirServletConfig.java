package ch.hl7.vacd.api.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.openapi.OpenApiInterceptor;
import ca.uhn.fhir.rest.server.IResourceProvider;
import jakarta.servlet.Servlet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class FhirServletConfig {
	
	@Value("${fhir.providers:}")
	private List<String> resourceProviderClassNames;

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    @Bean
    public ServletRegistrationBean<Servlet> fhirServlet(FhirContext fhirContext, java.util.Collection<IResourceProvider> providers) {
        RestfulServer server = new RestfulServer(fhirContext);
        // Register all discovered resource providers
        if(resourceProviderClassNames.isEmpty()) {
			server.setResourceProviders(providers);
		} else {
			List<IResourceProvider> filteredProviders = new java.util.ArrayList<>();
			providers.forEach(provider -> {
				String providerName = provider.getClass().getSimpleName();
				if(resourceProviderClassNames.contains(providerName)) {
					filteredProviders.add(provider);
				}
			});
			server.setResourceProviders(filteredProviders);
		}
        
        // Try to register the HAPI OpenAPI interceptor if present on the classpath
        try {
        	OpenApiInterceptor openApiInterceptor = new OpenApiInterceptor();
        	server.registerInterceptor(openApiInterceptor);
        	
//            if (ClassUtils.isPresent("ca.uhn.fhir.openapi.OpenApiInterceptor", this.getClass().getClassLoader())) {
//                Class<?> openApiClass = Class.forName("ca.uhn.fhir.openapi.OpenApiInterceptor");
//                Object interceptor = openApiClass.getDeclaredConstructor().newInstance();
//                server.registerInterceptor(interceptor);
//            }
        } catch (Exception ignored) {
            // ignore - openapi support is optional
        	System.out.println("OpenAPI interceptor not registered - OpenAPI support is not available on the classpath.");
        }
        ServletRegistrationBean<Servlet> registration = new ServletRegistrationBean<>(server, "/fhir/*");
        registration.setName("FhirServlet");
        return registration;
    }
}