package uk.ac.ebi.subs.sheetmapper;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.hateoas.UriTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.client.HttpClientErrorException;
import uk.ac.ebi.subs.repository.model.sheets.Row;
import uk.ac.ebi.subs.repository.model.sheets.Sheet;
import uk.ac.ebi.subs.repository.model.templates.Capture;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class SheetMapperService {

    private static final Logger logger = LoggerFactory.getLogger(SheetMapperService.class);

    public SheetMapperService(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Value("${usi.apiRootUrl}")
    private String rootApiUrl;


    private final TokenService tokenService;

    public void mapSheet(Sheet sheet) {
        Assert.notNull(sheet);
        Assert.notNull(sheet.getTemplate());
        Assert.notNull(sheet.getSubmission());
        Assert.notNull(sheet.getRows());
        Assert.notNull(sheet.getMappings());

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

                    Map<String, String> headers = postHeaders();
                    logger.debug("mapping submittable {} {}", targetType, alias);
                    try {
                        HttpResponse<JsonNode> queryResponse = Unirest.get(queryUri.toString()).headers(headers).asJson();

                        logger.debug("query response code {} {}");

                        if (HttpStatus.OK.value() == queryResponse.getStatus()) {
                            updateExistingSubmittable(json, headers, queryResponse);
                        } else if (HttpStatus.NOT_FOUND.value() == queryResponse.getStatus()) {
                            createNewSubmittable(createUriTemplate, json, expansionParams);
                        } else {
                            throw new HttpClientErrorException(
                                    HttpStatus.valueOf(queryResponse.getStatus()),
                                    queryResponse.getBody().toString()
                            );
                        }
                    } catch (UnirestException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private void createNewSubmittable(UriTemplate createUriTemplate, JSONObject json, Map<String, String> expansionParams) throws UnirestException {
        URI createUri = createUriTemplate.expand(expansionParams);

        String requestBodyJson = json.toString();

        HttpResponse<String> response = Unirest.post(createUri.toString())
                .headers(postHeaders())
                .body(requestBodyJson)
                .asString();

        if (response.getStatus() > 201) {
            throw new HttpClientErrorException(
                    HttpStatus.valueOf(response.getStatus()),
                    response.getBody()
            );
        }
    }

    private void updateExistingSubmittable(JSONObject json, Map<String, String> headers, HttpResponse<JsonNode> queryResponse) {
        JSONObject linksObject = queryResponse.getBody().getObject().getJSONObject("_links");

        String selfURI = linksObject.getJSONObject("self").getString("href");

        String requestBodyJson = json.toString();
        try {
            HttpResponse<String> response = Unirest.put(selfURI)
                    .headers(postHeaders())
                    .body(requestBodyJson)
                    .asString();

            if (response.getStatus() > 201) {
                throw new HttpClientErrorException(
                        HttpStatus.valueOf(response.getStatus()),
                        response.getBody()
                );
            }
        } catch (UnirestException e1) {
            throw new RuntimeException(e1);
        }
        Unirest.put(selfURI.toString()).headers(headers).body(json.toString());
    }

    private Map<String, String> postHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + tokenService.aapToken());
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/hal+json");
        return headers;
    }

    public Stream<JSONObject> buildSubmittableJson(Sheet sheet, URI submissionUrl) {
        List<String> headers = sheet.getRows().get(sheet.getHeaderRowIndex()).getCells();

        List<Row> rows = sheet.getRows();
        List<Row> rowsToParse = rows.subList(sheet.getHeaderRowIndex() + 1, rows.size());


        return rowsToParse.stream()
                .filter(row -> !row.isIgnored())
                .map(row -> {
                    JSONObject document = new JSONObject();

                    List<String> cells = row.getCells();

                    ListIterator<Capture> captureIterator = sheet.getMappings().listIterator();

                    while (captureIterator.hasNext()) {
                        int position = captureIterator.nextIndex();
                        Capture capture = captureIterator.next();

                        if (capture != null) {
                            capture.capture(position, headers, cells, document);
                        }
                    }

                    return document;
                })
                .map(jsonObject -> {
                    jsonObject.put("submission", submissionUrl.toString());
                    return jsonObject;
                });

    }

}
