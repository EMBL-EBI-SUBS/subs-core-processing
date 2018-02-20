package uk.ac.ebi.subs.batchloader;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.hateoas.UriTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.client.HttpClientErrorException;
import uk.ac.ebi.subs.repository.model.SubmittablesBatch;
import uk.ac.ebi.subs.repository.model.sheets.Sheet;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class SubmittablesBatchLoaderService {

    private static final Logger logger = LoggerFactory.getLogger(SubmittablesBatchLoaderService.class);

    public SubmittablesBatchLoaderService(TokenService tokenService, UniRestWrapper uniRestWrapper) {
        this.tokenService = tokenService;
        this.uniRestWrapper = uniRestWrapper;
    }

    @Value("${usi.apiRootUrl}")
    private String rootApiUrl;

    public void setRootApiUrl(String rootApiUrl) {
        this.rootApiUrl = rootApiUrl;
    }

    private final TokenService tokenService;
    private final UniRestWrapper uniRestWrapper;

    public void loadBatch(SubmittablesBatch batch) {
        Assert.notNull(batch);
        Assert.notNull(batch.getSubmission());
        Assert.notNull(batch.getDocuments());
        Assert.notNull(batch.getTargetType());

        UriTemplate submissionUriTemplate = new UriTemplate(rootApiUrl + "/submissions/{submissionId}");
        UriTemplate searchUriTemplate = new UriTemplate(rootApiUrl + "/{type}/search/by-submissionId-and-alias{?submissionId,alias,projection}");
        UriTemplate createUriTemplate = new UriTemplate(rootApiUrl + "/submissions/{submissionId}/contents/{type}");

        String targetType = batch.getTargetType().toLowerCase();
        String submissionId = batch.getSubmission().getId();

        logger.info("mapping {} for submission {} from sheet {}", targetType, submissionId, batch.getId());

        Map<String, String> submissionExpansionParams = new HashMap<>();
        submissionExpansionParams.put("submissionId", submissionId);

        URI submissionUri = submissionUriTemplate.expand(submissionExpansionParams);

        try {
            submitByHttp(batch, searchUriTemplate, createUriTemplate, targetType, submissionId, submissionUri);
        } catch (HttpClientErrorException e) {
            logger.error("HttpClientErrorException {} {}", e.getRawStatusCode(), e.getResponseBodyAsString());
            throw e;
        }

    }

    private void submitByHttp(SubmittablesBatch batch, UriTemplate searchUriTemplate, UriTemplate createUriTemplate, String targetType, String submissionId, URI submissionUri) {
        buildSubmittableJson(batch, submissionUri)
                .parallel()
                .filter(json -> json.has("alias"))
                .filter(json -> json.getString("alias") != null && !json.getString("alias").isEmpty())
                .forEach(json -> {

                    String alias = json.getString("alias");

                    Map<String, String> expansionParams = new HashMap<>();
                    expansionParams.put("submissionId", submissionId);
                    expansionParams.put("alias", alias);
                    expansionParams.put("type", targetType);

                    URI queryUri = searchUriTemplate.expand(expansionParams);

                    logger.debug("mapping submittable {} {}", targetType, alias);

                    HttpResponse<JsonNode> queryResponse = uniRestWrapper.getJson(queryUri.toString(), requestHeaders());

                    logger.debug("query response code {} {}");

                    if (HttpStatus.OK.value() == queryResponse.getStatus()) {
                        updateExistingSubmittable(json, queryResponse);
                    } else if (HttpStatus.NOT_FOUND.value() == queryResponse.getStatus()) {
                        createNewSubmittable(createUriTemplate, json, expansionParams);
                    } else {
                        throw new HttpClientErrorException(
                                HttpStatus.valueOf(queryResponse.getStatus()),
                                queryResponse.getBody().toString()
                        );
                    }

                });
    }

    private void createNewSubmittable(UriTemplate createUriTemplate, JSONObject json, Map<String, String> expansionParams) {
        URI createUri = createUriTemplate.expand(expansionParams);

        String requestBodyJson = json.toString();

        HttpResponse<JsonNode> response = uniRestWrapper.postJson(
                createUri.toString(),
                requestHeaders(),
                json
        );

        if (response.getStatus() > 201) {
            throw new HttpClientErrorException(
                    HttpStatus.valueOf(response.getStatus()),
                    response.getBody().toString()
            );
        }
    }

    private void updateExistingSubmittable(JSONObject json, HttpResponse<JsonNode> queryResponse) {
        JSONObject linksObject = queryResponse.getBody().getObject().getJSONObject("_links");

        String selfURI = linksObject.getJSONObject("self").getString("href");

        String requestBodyJson = json.toString();

        HttpResponse<JsonNode> response = uniRestWrapper.putJson(
                selfURI, requestHeaders(), json
        );

        if (response.getStatus() > 201) {
            throw new HttpClientErrorException(
                    HttpStatus.valueOf(response.getStatus()),
                    response.getBody().toString()
            );
        }

    }

    private Map<String, String> requestHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + tokenService.aapToken());
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/hal+json");
        return headers;
    }

    public Stream<JSONObject> buildSubmittableJson(SubmittablesBatch batch, URI submissionUrl) {
        return batch.getDocuments().stream()
                .filter(document -> !document.isProcessed())
                .filter(document -> document.getDocument() != null)
                .map(document -> document.getDocument())
                .map(d -> new JSONObject(d))
                .map(jsonObject -> {
                    jsonObject.put("submission", submissionUrl.toString());
                    return jsonObject;
                });
    }

}
