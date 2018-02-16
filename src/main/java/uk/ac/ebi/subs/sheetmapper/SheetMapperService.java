package uk.ac.ebi.subs.sheetmapper;

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
import uk.ac.ebi.subs.repository.model.sheets.Sheet;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class SheetMapperService {

    private static final Logger logger = LoggerFactory.getLogger(SheetMapperService.class);

    public SheetMapperService(TokenService tokenService, UniRestWrapper uniRestWrapper) {
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

    public void mapSheet(Sheet sheet) {
        Assert.notNull(sheet);
        Assert.notNull(sheet.getSubmission());
        Assert.notNull(sheet.getRows());

        UriTemplate submissionUriTemplate = new UriTemplate(rootApiUrl + "/submissions/{submissionId}");
        UriTemplate searchUriTemplate = new UriTemplate(rootApiUrl + "/{type}/search/by-submissionId-and-alias{?submissionId,alias,projection}");
        UriTemplate createUriTemplate = new UriTemplate(rootApiUrl + "/submissions/{submissionId}/contents/{type}");

        String targetType = sheet.getTemplate().getTargetType().toLowerCase();
        String submissionId = sheet.getSubmission().getId();

        logger.info("mapping {} for submission {} from sheet {}", targetType, submissionId, sheet.getId());

        Map<String, String> submissionExpansionParams = new HashMap<>();
        submissionExpansionParams.put("submissionId", submissionId);

        URI submissionUri = submissionUriTemplate.expand(submissionExpansionParams);

        try {
            submitByHttp(sheet, searchUriTemplate, createUriTemplate, targetType, submissionId, submissionUri);
        } catch (HttpClientErrorException e) {
            logger.error("HttpClientErrorException {} {}", e.getRawStatusCode(), e.getResponseBodyAsString());
            throw e;
        }

    }

    private void submitByHttp(Sheet sheet, UriTemplate searchUriTemplate, UriTemplate createUriTemplate, String targetType, String submissionId, URI submissionUri) {
        buildSubmittableJson(sheet, submissionUri)
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

    public Stream<JSONObject> buildSubmittableJson(Sheet sheet, URI submissionUrl) {
        return sheet.getRows().stream()
                .filter(r -> !r.isIgnored())
                .filter(r -> r.getDocument() != null)
                .map(r -> r.getDocument())
                .map(d -> new JSONObject(d))
                .map(jsonObject -> {
                    jsonObject.put("submission", submissionUrl.toString());
                    return jsonObject;
                });
    }

}
