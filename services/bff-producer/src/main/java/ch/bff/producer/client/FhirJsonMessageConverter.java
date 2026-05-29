package ch.bff.producer.client;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class FhirJsonMessageConverter extends AbstractHttpMessageConverter<IBaseResource> {

    private static final MediaType FHIR_JSON = new MediaType("application", "fhir+json");

    private final IParser jsonParser;

    public FhirJsonMessageConverter() {
        super(FHIR_JSON);
        this.jsonParser = FhirContext.forR4().newJsonParser();
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return IBaseResource.class.isAssignableFrom(clazz);
    }

    @Override
    protected IBaseResource readInternal(Class<? extends IBaseResource> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        var body = new String(inputMessage.getBody().readAllBytes(), StandardCharsets.UTF_8);
        return jsonParser.parseResource(clazz, body);
    }

    @Override
    protected void writeInternal(IBaseResource resource, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        outputMessage.getBody().write(
                jsonParser.encodeResourceToString(resource).getBytes(StandardCharsets.UTF_8)
        );
    }
}
