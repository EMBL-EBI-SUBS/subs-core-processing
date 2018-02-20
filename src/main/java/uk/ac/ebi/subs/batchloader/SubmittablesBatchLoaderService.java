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
import uk.ac.ebi.subs.repository.repos.SubmittablesBatchRepository;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SubmittablesBatchLoaderService {

    private static final Logger logger = LoggerFactory.getLogger(SubmittablesBatchLoaderService.class);

    public SubmittablesBatchLoaderService(TokenService tokenService, UniRestWrapper uniRestWrapper, SubmittablesBatchRepository submittablesBatchRepository) {
        this.tokenService = tokenService;
        this.uniRestWrapper = uniRestWrapper;
        this.submittablesBatchRepository = submittablesBatchRepository;
    }

    @Value("${usi.apiRootUrl}")
    private String rootApiUrl;

    public void setRootApiUrl(String rootApiUrl) {
        this.rootApiUrl = rootApiUrl;
    }

    private final TokenService tokenService;
    private final UniRestWrapper uniRestWrapper;

    private SubmittablesBatchRepository submittablesBatchRepository;

    public void loadBatch(SubmittablesBatch batch) {
        Assert.notNull(batch);
        Assert.notNull(batch.getSubmission());
        Assert.notNull(batch.getDocuments());
        Assert.notNull(batch.getTargetType());


        String targetType = batch.getTargetType().toLowerCase();
        String submissionId = batch.getSubmission().getId();

        logger.info("mapping {} for submission {} from sheet {}", targetType, submissionId, batch.getId());

        try {
            submitByHttp(batch);
        } catch (HttpClientErrorException e) {
            logger.error("HttpClientErrorException {} {}", e.getRawStatusCode(), e.getResponseBodyAsString());
            throw e;
        }

    }

    private void submitByHttp(SubmittablesBatch batch) {
        UriTemplate searchUriTemplate = new UriTemplate(rootApiUrl + "/{type}/search/by-submissionId-and-alias{?submissionId,alias,projection}");
        UriTemplate createUriTemplate = new UriTemplate(rootApiUrl + "/submissions/{submissionId}/contents/{type}");
        UriTemplate submissionUriTemplate = new UriTemplate(rootApiUrl + "/submissions/{submissionId}");

        String targetType = batch.getTargetType();
        String submissionId = batch.getSubmission().getId();

        Map<String, String> submissionExpansionParams = new HashMap<>();
        submissionExpansionParams.put("submissionId", submissionId);

        String submissionUrl = submissionUriTemplate.expand(submissionExpansionParams).toString();

        List<SubmittablesBatch.Document> documentsToLoad = batch.getDocuments().stream()
                .filter(document -> !document.isProcessed())
                .collect(Collectors.toList());

        int numberProcessed = 0;

        for (SubmittablesBatch.Document document : documentsToLoad) {
            JSONObject json = new JSONObject(document.getDocument());

            String alias = json.getString("alias");

            if (alias == null || alias.isEmpty()){
                document.addError("Please provide an alias");
                document.setProcessed(true);
            }

            Map<String, String> expansionParams = new HashMap<>();
            expansionParams.put("submissionId", submissionId);
            expansionParams.put("alias", alias);
            expansionParams.put("type", targetType);

            json.put("submission", submissionUrl);


            URI queryUri = searchUriTemplate.expand(expansionParams);

            logger.debug("mapping submittable {} {}", targetType, alias);

            HttpResponse<JsonNode> queryResponse = uniRestWrapper.getJson(queryUri.toString(), requestHeaders());

            logger.debug("query response code {} {}");

            if (HttpStatus.OK.value() == queryResponse.getStatus()) {
                updateExistingSubmittable(document, json, queryResponse);
            } else if (HttpStatus.NOT_FOUND.value() == queryResponse.getStatus()) {
                createNewSubmittable(document, createUriTemplate, json, expansionParams);
            } else {
                throw new HttpClientErrorException(
                        HttpStatus.valueOf(queryResponse.getStatus()),
                        queryResponse.getBody().toString()
                );
            }

            numberProcessed++;
            if (numberProcessed % 100 == 0){
                batch.setVersion(batch.getVersion() + 1);
                submittablesBatchRepository.save(batch);
            }

        }

        batch.setStatus("Completed");
        batch.setVersion(batch.getVersion() + 1);
        submittablesBatchRepository.save(batch);
    }

    private void createNewSubmittable(SubmittablesBatch.Document document, UriTemplate createUriTemplate, JSONObject json, Map<String, String> expansionParams) {
        URI createUri = createUriTemplate.expand(expansionParams);

        HttpResponse<JsonNode> response = uniRestWrapper.postJson(
                createUri.toString(),
                requestHeaders(),
                json
        );

        if (response.getStatus() > 201) {
            document.addError("Error while loading document: "+response.getBody().toString());
        }
        else {
            document.setProcessed(true);
        }

    }

    private void updateExistingSubmittable(SubmittablesBatch.Document document, JSONObject json, HttpResponse<JsonNode> queryResponse) {
        JSONObject linksObject = queryResponse.getBody().getObject().getJSONObject("_links");

        String selfURI = linksObject.getJSONObject("self").getString("href");

        HttpResponse<JsonNode> response = uniRestWrapper.putJson(
                selfURI, requestHeaders(), json
        );

        if (response.getStatus() > 201) {
            document.addError("Error while loading document: "+response.getBody().toString());
        }
        else {
            document.setProcessed(true);
        }

    }

    private Map<String, String> requestHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + tokenService.aapToken());
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/hal+json");
        return headers;
    }

}
