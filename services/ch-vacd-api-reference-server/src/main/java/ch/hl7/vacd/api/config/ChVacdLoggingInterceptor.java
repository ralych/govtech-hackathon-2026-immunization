package ch.hl7.vacd.api.config;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Date;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.ResponseDetails;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.RestfulServerUtils;
import ca.uhn.fhir.rest.server.RestfulServerUtils.ResponseEncoding;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import ca.uhn.fhir.util.UrlUtil;
import jakarta.servlet.http.HttpServletRequest;

@Interceptor
public class ChVacdLoggingInterceptor {
	private Logger log;
	/** Message format for logging incoming requests with request body. */
	private String messageFormat1 = "Source[${remoteAddr}]\nOperation[${operationType} ${idOrResourceName}]\nUA[${requestHeader.user-agent}]\nParams[${requestParameters}]\nResource: ${requestBodyFhir}";
	/**
	 * Alternative message format for logging incoming requests without request
	 * body.
	 */
	private String messageFormat2 = "Source[${remoteAddr}]\nOperation[${operationType} ${idOrResourceName}]\nUA[${requestHeader.user-agent}]\nParams[${requestParameters}]";
	private FhirContext fhirContext;

	/**
	 * Default constructor initializing the logger.
	 */
	public ChVacdLoggingInterceptor(FhirContext fhirContext) {
		this.fhirContext = fhirContext;
		log = LoggerFactory.getLogger(getClass());
	}

	/**
	 * Sets a custom logger.
	 * 
	 * @param theLogger the logger to set
	 */
	public void setLogger(Logger theLogger) {
		Validate.notNull(theLogger, "Logger can not be null");
		log = theLogger;
	}

	/**
	 * Sets a custom logger by name.
	 * 
	 * @param theLoggerName the name of the logger to set
	 */
	public void setLoggerName(String theLoggerName) {
		Validate.notBlank(theLoggerName, "Logger name can not be null/empty");
		log = LoggerFactory.getLogger(theLoggerName);
	}

//	// 1
//	@Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_PROCESSED)
//	public void incomingRequest1(RequestDetails theRequestDetails, HttpServletRequest theRequest,
//			HttpServletResponse theResponse) {
//		log.trace("1 SERVER_INCOMING_REQUEST_PRE_PROCESSED");
//	}
//
//	// 2
//	@Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLER_SELECTED)
//	public void incomingRequest4(RequestDetails theRequestDetails, HttpServletRequest theRequest,
//			HttpServletResponse theResponse) {
//		log.trace("2 SERVER_INCOMING_REQUEST_PRE_HANDLER_SELECTED");
//	}

	/**
	 * Processing completed normally - log the incoming request.
	 * 
	 * @param theRequestDetails the request details
	 */
	// 3
	@Hook(Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED)
//	@Hook(Pointcut.SERVER_PROCESSING_COMPLETED_NORMALLY)
	public void processingCompletedNormally(ServletRequestDetails theRequestDetails) {
		// Perform any string substitutions from the message format
		String operation = theRequestDetails.getRestOperationType().getCode();
		String requestID = theRequestDetails.getRequestId();
		String path = theRequestDetails.getCompleteUrl();

		StringLookup lookup = new MyLookup(theRequestDetails.getServletRequest(), theRequestDetails);
		StringSubstitutor subs = new StringSubstitutor(lookup, "${", "}", '\\');

		// Actually log the line
		String line = "";
		try {
			line = subs.replace(messageFormat1);
		} catch (Exception e) {
			try {
				line = subs.replace(messageFormat2);
			} catch (Exception e2) {

			}
		}
		log.debug("Incoming request:\n{} {}\nRequest Id: {} - {}", path, operation, requestID, line);
	}

	/**
	 * Processing response - log the outgoing response.
	 * 
	 * @param theResponseDetails the response details
	 * @param theRequestDetails  the request details
	 */
	@Hook(Pointcut.SERVER_OUTGOING_RESPONSE)
	public void processingResponse(ResponseDetails theResponseDetails, ServletRequestDetails theRequestDetails) {
		try {

			// Perform any string substitutions from the message format
			String requestID = theRequestDetails.getRequestId();
			int code = theResponseDetails.getResponseCode();
			IBaseResource responseResource = theResponseDetails.getResponseResource();
			String response = "";
			if (responseResource != null) {
				response = fhirContext.newJsonParser().setPrettyPrint(false).encodeResourceToString(responseResource);
			}

			log.debug("Outgoing response:\nRequest Id: {} - Response code: {}\nResource: {}", requestID, code, response);
		} catch (Exception e) {

		}
	}

//	// 4
//	@Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLED)
//	public void incomingRequest3(RequestDetails theRequestDetails, HttpServletRequest theRequest,
//			HttpServletResponse theResponse) {
//		log.trace("4 SERVER_INCOMING_REQUEST_PRE_HANDLED");
//	}
//
//	// 5
//	@Hook(Pointcut.SERVER_PROCESSING_COMPLETED)
//	public void incomingRequest5() {
//		log.trace("5 SERVER_PROCESSING_COMPLETED");
//	}

	/**
	 * Custom string lookup for message formatting.
	 */
	private static final class MyLookup implements StringLookup {
		private final Throwable myException;
		private final HttpServletRequest myRequest;
		private final RequestDetails myRequestDetails;

		private MyLookup(HttpServletRequest theRequest, RequestDetails theRequestDetails) {
			myRequest = theRequest;
			myRequestDetails = theRequestDetails;
			myException = null;
		}

//		MyLookup(HttpServletRequest theServletRequest, BaseServerResponseException theException,
//				RequestDetails theRequestDetails) {
//			myException = theException;
//			myRequestDetails = theRequestDetails;
//			myRequest = theServletRequest;
//		}

		@Override
		public String lookup(String theKey) {

			/*
			 * TODO: this method could be made more efficient through some sort of lookup
			 * map
			 */

			if ("operationType".equals(theKey)) {
				if (myRequestDetails.getRestOperationType() != null) {
					return myRequestDetails.getRestOperationType().getCode();
				}
				return "";
			} else if ("operationName".equals(theKey)) {
				if (myRequestDetails.getRestOperationType() != null) {
					switch (myRequestDetails.getRestOperationType()) {
					case EXTENDED_OPERATION_INSTANCE:
					case EXTENDED_OPERATION_SERVER:
					case EXTENDED_OPERATION_TYPE:
						return myRequestDetails.getOperation();
					default:
						return "";
					}
				}
				return "";
			} else if ("id".equals(theKey)) {
				if (myRequestDetails.getId() != null) {
					return myRequestDetails.getId().getValue();
				}
				return "";
			} else if ("servletPath".equals(theKey)) {
				return StringUtils.defaultString(myRequest.getServletPath());
			} else if ("idOrResourceName".equals(theKey)) {
				if (myRequestDetails.getId() != null) {
					return myRequestDetails.getId().getValue();
				}
				if (myRequestDetails.getResourceName() != null) {
					return myRequestDetails.getResourceName();
				}
				return "";
			} else if (theKey.equals("requestParameters")) {
				StringBuilder b = new StringBuilder();
				for (Entry<String, String[]> next : myRequestDetails.getParameters().entrySet()) {
					for (String nextValue : next.getValue()) {
						if (b.length() == 0) {
							b.append('?');
						} else {
							b.append('&');
						}
						b.append(UrlUtil.escapeUrlParam(next.getKey()));
						b.append('=');
						b.append(UrlUtil.escapeUrlParam(nextValue));
					}
				}
				return b.toString();
			} else if (theKey.startsWith("requestHeader.")) {
				String val = myRequest.getHeader(theKey.substring("requestHeader.".length()));
				return StringUtils.defaultString(val);
			} else if (theKey.startsWith("remoteAddr")) {
				return StringUtils.defaultString(myRequest.getRemoteAddr());
			} else if (theKey.equals("responseEncodingNoDefault")) {
				ResponseEncoding encoding = RestfulServerUtils.determineResponseEncodingNoDefault(myRequestDetails,
						myRequestDetails.getServer().getDefaultResponseEncoding());
				if (encoding != null) {
					return encoding.getEncoding().name();
				}
				return "";
			} else if (theKey.equals("exceptionMessage")) {
				return myException != null ? myException.getMessage() : null;
			} else if (theKey.equals("requestUrl")) {
				return myRequest.getRequestURL().toString();
			} else if (theKey.equals("requestVerb")) {
				return myRequest.getMethod();
			} else if (theKey.equals("requestBodyFhir")) {
				String contentType = myRequest.getContentType();
				if (isNotBlank(contentType)) {
					int colonIndex = contentType.indexOf(';');
					if (colonIndex != -1) {
						contentType = contentType.substring(0, colonIndex);
					}
					contentType = contentType.trim();

					EncodingEnum encoding = EncodingEnum.forContentType(contentType);
					if (encoding != null) {
						byte[] requestContents = myRequestDetails.loadRequestContents();
						return new String(requestContents, Constants.CHARSET_UTF8);
					}
				}
				return "";
			} else if ("processingTimeMillis".equals(theKey)) {
				Date startTime = (Date) myRequest.getAttribute(RestfulServer.REQUEST_START_TIME);
				if (startTime != null) {
					long time = System.currentTimeMillis() - startTime.getTime();
					return Long.toString(time);
				}
			} else if ("requestId".equals(theKey)) {
				return myRequestDetails.getRequestId();
			}

			return "!VAL!";
		}
	}

	/**
	 * set the message format 1
	 * 
	 * @param format the format
	 */
	public void setMessageFormat1(String format) {
		messageFormat1 = format;
	}

	/**
	 * set the message format 2 - alternative
	 * 
	 * @param format the format
	 */
	public void setMessageFormat2(String format) {
		messageFormat2 = format;
	}
}
