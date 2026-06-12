package ch.hl7.vacd.api.config;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.rest.openapi.OpenApiInterceptor;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;

public class ChVacdOpenApiInterceptor extends OpenApiInterceptor {

		private Logger logger = LoggerFactory.getLogger(getClass());

		/**
		 * Generates the OpenAPI specification and enhances it with examples and
		 * operation properties from an external YAML file.
		 *
		 * @param theRequestDetails the request details
		 * @return the enhanced OpenAPI specification
		 */
		protected OpenAPI generateOpenApi(ServletRequestDetails theRequestDetails) {
			OpenAPI openApi = super.generateOpenApi(theRequestDetails);

			try {
				OpenAPI openApiFromFile = Yaml.mapper().readValue(this.getClass().getResourceAsStream("/openapi.yaml"),
						OpenAPI.class);

				updateExamples(openApi, openApiFromFile);

				updateOperationProperties(openApi, openApiFromFile);

				if (logger.isDebugEnabled()) {
					logger.debug(Yaml.pretty(openApi));
				}

			} catch (Exception e) {
				logger.error("Error loading openapi examples", e);
			}

			return openApi;
		}

		/**
		 * Updates the operation properties of the generated OpenAPI specification based
		 * on the properties defined in the external OpenAPI file.
		 *
		 * @param geneOpenApi the generated OpenAPI specification
		 * @param fileOpenApi the OpenAPI specification from the external file
		 */
		private void updateOperationProperties(OpenAPI geneOpenApi, OpenAPI fileOpenApi) {

			Paths openApiPaths = geneOpenApi.getPaths();
			Paths fileOpenApiPaths = fileOpenApi.getPaths();

			openApiPaths.keySet().forEach(pathKey -> {
				Map<HttpMethod, Operation> openApiOpsMap = openApiPaths.get(pathKey).readOperationsMap();
				openApiOpsMap.forEach((openApiMethod, openApiOperation) -> {

					PathItem filePathItem = fileOpenApiPaths.get(pathKey);
					if (filePathItem != null) {
						Operation fileOpenApiOps = filePathItem.readOperationsMap().get(openApiMethod);
						if (fileOpenApiOps != null) {

							// fileOpenApiOps.getDeprecated();
							openApiOpsMap.get(openApiMethod).setDeprecated(fileOpenApiOps.getDeprecated());

							// fileOpenApiOps.getDescription();
							if (StringUtils.isNotEmpty(fileOpenApiOps.getDescription())) {
								openApiOpsMap.get(openApiMethod).setDescription(fileOpenApiOps.getDescription());
							}
							// fileOpenApiOps.getExtensions();
							if (fileOpenApiOps.getExtensions() != null) {
								if (openApiOpsMap.get(openApiMethod).getExtensions() != null) {
									openApiOpsMap.get(openApiMethod).getExtensions().putAll(fileOpenApiOps.getExtensions());
								} else {
									openApiOpsMap.get(openApiMethod).setExtensions(fileOpenApiOps.getExtensions());
								}
							}

							// fileOpenApiOps.getExternalDocs();
							if (fileOpenApiOps.getExternalDocs() != null) {
								openApiOpsMap.get(openApiMethod).setExternalDocs(fileOpenApiOps.getExternalDocs());
							}
//							fileOpenApiOps.getOperationId();
							if (StringUtils.isNotEmpty(fileOpenApiOps.getOperationId())) {
								openApiOpsMap.get(openApiMethod).setOperationId(fileOpenApiOps.getOperationId());
							}
//							fileOpenApiOps.getParameters();
							if (fileOpenApiOps.getParameters() != null) {
								if (openApiOpsMap.get(openApiMethod).getParameters() != null) {
									List<Parameter> existingParameters = openApiOpsMap.get(openApiMethod).getParameters();
									List<Parameter> definedParameters = fileOpenApiOps.getParameters();
									for (Parameter definedParameter : definedParameters) {
										Optional<Parameter> existingParam = existingParameters.stream()
												.filter(filter -> (StringUtils.isNotEmpty(filter.getIn())
														&& filter.getIn().equalsIgnoreCase(definedParameter.getIn())
														&& StringUtils.isNotEmpty(filter.getName())
														&& filter.getName().equalsIgnoreCase(definedParameter.getName())))
												.findAny();
										if (existingParam.isPresent()) {
											existingParam.get().setDescription(definedParameter.getDescription());
											existingParam.get().setSchema(definedParameter.getSchema());
										} else {
											existingParameters.add(definedParameter);
										}
									}

//									openApiOpsMap.get(openApiMethod).getParameters().addAll();
								} else {
									openApiOpsMap.get(openApiMethod).setParameters(fileOpenApiOps.getParameters());
								}
							}

//							fileOpenApiOps.getRequestBody();
							if (fileOpenApiOps.getRequestBody() != null && //
									fileOpenApiOps.getRequestBody().getContent() != null) {
								RequestBody requestBody = openApiOpsMap.get(openApiMethod).getRequestBody();
								if (requestBody != null) {
									if (requestBody.getContent() != null) {
										fileOpenApiOps.getRequestBody().getContent().forEach((key, value) -> {
											if (requestBody.getContent().containsKey(key)) {
												requestBody.getContent().get(key).setExample(value.getExample());
											} else {
												requestBody.getContent().addMediaType(key, value);
											}
										});
									} else {
										requestBody.setContent(fileOpenApiOps.getRequestBody().getContent());
									}
								} else {

									openApiOpsMap.get(openApiMethod).setRequestBody(fileOpenApiOps.getRequestBody());
								}
							}

//							fileOpenApiOps.getResponses();
							if (fileOpenApiOps.getResponses() != null) {
								openApiOpsMap.get(openApiMethod).setResponses(fileOpenApiOps.getResponses());
							}

//							fileOpenApiOps.getSecurity();
							fileOpenApiOps.getSecurity();
							if (fileOpenApiOps.getSecurity() != null) {
								if (openApiOpsMap.get(openApiMethod).getSecurity() != null) {
									openApiOpsMap.get(openApiMethod).getSecurity().addAll(fileOpenApiOps.getSecurity());
								} else {
									openApiOpsMap.get(openApiMethod).setSecurity(fileOpenApiOps.getSecurity());
								}
							}

//							fileOpenApiOps.getServers();
							if (fileOpenApiOps.getServers() != null) {
								if (openApiOpsMap.get(openApiMethod).getServers() != null) {
									openApiOpsMap.get(openApiMethod).getServers().addAll(fileOpenApiOps.getServers());
								} else {
									openApiOpsMap.get(openApiMethod).setServers(fileOpenApiOps.getServers());
								}
							}

//							fileOpenApiOps.getSummary();
							if (StringUtils.isNotEmpty(fileOpenApiOps.getSummary())) {
								openApiOpsMap.get(openApiMethod).setSummary(fileOpenApiOps.getSummary());
							}

							// fileOpenApiOps.getTags();
							if (fileOpenApiOps.getTags() != null) {
								if (openApiOpsMap.get(openApiMethod).getTags() != null) {
									openApiOpsMap.get(openApiMethod).getTags().addAll(fileOpenApiOps.getTags());
								} else {
									openApiOpsMap.get(openApiMethod).setTags(fileOpenApiOps.getTags());
								}
							}
						}

					}

				});
			});

		}

		/**
		 * Updates the examples in the generated OpenAPI specification based on the
		 * examples defined in the external OpenAPI file.
		 * 
		 * @param openApi         the generated OpenAPI specification
		 * 
		 * @param openApiFromFile the OpenAPI specification from the external file
		 */
		private void updateExamples(OpenAPI openApi, OpenAPI openApiFromFile) {
			Paths openApiPaths = openApi.getPaths();
//		logger.info("" + examples.getPaths());
			openApiFromFile.getPaths().keySet().forEach(key -> {
				if (logger.isDebugEnabled()) {
					logger.debug("key: " + key);
				}
				if (openApiPaths.containsKey(key)) {
					Map<HttpMethod, Operation> operationMap = openApiFromFile.getPaths().get(key).readOperationsMap();
					operationMap.forEach((method, operation) -> {
//						logger.info("Method: " + method + ", operation: " + operation);
						if (logger.isDebugEnabled()) {
							logger.debug("Method: " + method);
						}
						if (operation != null && operation.getRequestBody() != null
								&& operation.getRequestBody().getContent() != null) {
							Set<String> contentKeys = operation.getRequestBody().getContent().keySet();
							if (logger.isDebugEnabled()) {
								logger.debug("contentKey: " + contentKeys);
							}
							contentKeys.forEach(contentKey -> {
//						logger.info("contentKey: " + contentKey);

								PathItem openApiPath = openApiPaths.get(key);
								if (openApiPath != null) {
									Operation openApiOperation = openApiPath.readOperationsMap().get(method);
//							logger.info(""+openApiOperation.getRequestBody());
									MediaType openApiContent = openApiOperation.getRequestBody().getContent()
											.get(contentKey);
									if (openApiContent != null) {
										openApiContent.setExample(
												operation.getRequestBody().getContent().get(contentKey).getExample());
									}
								}

							});
						}

						if (operation.getResponses() != null) {
							operation.getResponses().keySet().forEach(responseKey -> {
								ApiResponse contentResp = operation.getResponses().get(responseKey);
								if (contentResp != null && contentResp.getContent() != null) {
									Set<String> contentKeysResp = contentResp.getContent().keySet();
									contentKeysResp.forEach(contentKeyResp -> {

										PathItem openApiPath = openApiPaths.get(key);
										if (openApiPath != null) {
											Operation openApiOperation = openApiPath.readOperationsMap().get(method);
											if (openApiOperation != null) {
												ApiResponse response = openApiOperation.getResponses().get(responseKey);
												if (response != null) {
													MediaType openApiContent = response.getContent().get(contentKeyResp);
													if (openApiContent != null) {
														openApiContent.setExample(
																contentResp.getContent().get(contentKeyResp).getExample());
													}
													if (StringUtils.isNotEmpty(contentResp.getDescription())) {
														response.setDescription(contentResp.getDescription());
													}

												} else {
													ApiResponse api201 = new ApiResponse();
													api201.setDescription(contentResp.getDescription());
													Content content = new Content();
													MediaType item = new MediaType();
													item.setExample(
															contentResp.getContent().get(contentKeyResp).getExample());
													content.addMediaType(contentKeyResp, item);
													api201.setContent(content);
													openApiOperation.getResponses().addApiResponse(responseKey, api201);
												}
											}
										}

									});
								}
							});
						}

					});
				}

			});

		}

}
