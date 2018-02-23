package uk.ac.ebi.subs.sheetloader;

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
import uk.ac.ebi.subs.repository.model.sheets.Row;
import uk.ac.ebi.subs.repository.model.sheets.Sheet;
import uk.ac.ebi.subs.repository.model.sheets.SheetStatusEnum;
import uk.ac.ebi.subs.repository.model.templates.Capture;
import uk.ac.ebi.subs.repository.model.templates.Template;
import uk.ac.ebi.subs.repository.repos.SheetRepository;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SheetLoaderService {

    private static final Logger logger = LoggerFactory.getLogger(SheetLoaderService.class);

    public SheetLoaderService(TokenService tokenService, UniRestWrapper uniRestWrapper, SheetRepository sheetRepository) {
        this.tokenService = tokenService;
        this.uniRestWrapper = uniRestWrapper;
        this.sheetRepository = sheetRepository;
    }

    @Value("${usi.apiRootUrl}")
    private String rootApiUrl;

    public void setRootApiUrl(String rootApiUrl) {
        this.rootApiUrl = rootApiUrl;
    }

    private final TokenService tokenService;
    private final UniRestWrapper uniRestWrapper;

    private SheetRepository sheetRepository;

    public void loadSheet(Sheet sheet) {
        Assert.notNull(sheet);
        Assert.notNull(sheet.getSubmission());
        Assert.notNull(sheet.getRows());
        Assert.notNull(sheet.getTemplate());

        Template template = sheet.getTemplate();
        String targetType = template.getTargetType().toLowerCase();
        String submissionId = sheet.getSubmission().getId();

        logger.info("mapping {} for submission {} from sheet {}", targetType, submissionId, sheet.getId());

        List<Capture> columnMappings = mapColumns(
                sheet.getHeaderRow(),
                template.getColumnCaptures(),
                Optional.of(template.getDefaultCapture())
        );

        try {
            submitByHttp(sheet,columnMappings);
        } catch (HttpClientErrorException e) {
            logger.error("HttpClientErrorException {} {}", e.getRawStatusCode(), e.getResponseBodyAsString());
            throw e;
        }

    }

    protected List<Capture> mapColumns(Row headerRow, Map<String,Capture> columnCaptures, Optional<Capture> optionalDefaultCapture)  {
        logger.debug("Mapping by headers {} to captures {}, with default {}",headerRow,columnCaptures,optionalDefaultCapture);

        columnCaptures.entrySet().stream().forEach(entry ->
                entry.getValue().setDisplayName(entry.getKey())
        );

        List<Capture> capturePositions = new ArrayList<>(Collections.nCopies(headerRow.getCells().size(),null));

        List<String> headerRowCells = headerRow.getCells();
        int position = 0;

        while (position < headerRowCells.size()) {

            String currentHeader = headerRowCells.get(position);
            currentHeader = currentHeader.trim().toLowerCase();

            if (columnCaptures.containsKey(currentHeader)) {
                Capture capture = columnCaptures.get(currentHeader);

                position = capture.map(position, capturePositions, headerRowCells);
            } else if (optionalDefaultCapture.isPresent()) {
                Capture clonedCapture = optionalDefaultCapture.get().copy();
                clonedCapture.setDisplayName(currentHeader);
                position = clonedCapture.map(position, capturePositions, headerRowCells);
            } else {
                position++;
            }
        }

        capturePositions.forEach(capture ->
                capture.setDisplayName(null)
        );

       return capturePositions;
    }

    protected JSONObject rowToDocument(Row row, List<Capture> mappings, List<String> headers) {
        JSONObject jsonObject = new JSONObject();
        row.getErrors().clear();

        List<String> cells = row.getCells();
        ListIterator<Capture> captureIterator = mappings.listIterator();

        while (captureIterator.hasNext()) {
            int position = captureIterator.nextIndex();
            Capture capture = captureIterator.next();

            if (capture != null) {
                try {
                    capture.capture(position, headers, cells, jsonObject);
                } catch (NumberFormatException e) {
                    String errorMessage = capture.getDisplayName() + " must be a number";
                    row.getErrors().add(errorMessage);
                }
            }

        }

        if (!hasStringAlias(jsonObject)) {
            row.getErrors().add("Please provide an alias");
        }

        if (row.getErrors().isEmpty()){
            row.setProcessed(true);
        }

        return jsonObject;
    }

    /**
     * all submittables must have an alias, which must be a non-null string
     *
     * @param json
     * @return
     */
    protected static boolean hasStringAlias(JSONObject json) {
        if (!json.has("alias")) return false;

        Object alias = json.get("alias");

        if (alias == null) return false;

        if (String.class.isAssignableFrom(alias.getClass()) &&
                !alias.toString().trim().isEmpty()) {
            return true;
        }

        return false;
    }

    private void submitByHttp(Sheet sheet, List<Capture> columnMappings) {
        //TODO add row to Json step

        UriTemplate searchUriTemplate = new UriTemplate(rootApiUrl + "/{type}/search/by-submissionId-and-alias{?submissionId,alias,projection}");
        UriTemplate createUriTemplate = new UriTemplate(rootApiUrl + "/submissions/{submissionId}/contents/{type}");
        UriTemplate submissionUriTemplate = new UriTemplate(rootApiUrl + "/submissions/{submissionId}");

        String targetType = sheet.getTemplate().getTargetType().toLowerCase();
        String submissionId = sheet.getSubmission().getId();

        Map<String, String> submissionExpansionParams = new HashMap<>();
        submissionExpansionParams.put("submissionId", submissionId);

        String submissionUrl = submissionUriTemplate.expand(submissionExpansionParams).toString();

        List<Row> rowsToLoad = sheet.getRows().stream()
                .filter(row -> !row.isProcessed())
                .collect(Collectors.toList());

        int numberProcessed = 0;

        for (Row row : rowsToLoad) {
            JSONObject json = rowToDocument(row,columnMappings,sheet.getHeaderRow().getCells());

            if (!row.getErrors().isEmpty()){
                continue;
            }

            String alias = json.getString("alias");

            if (alias == null || alias.isEmpty()) {
                row.getErrors().add("Please provide an alias");
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
                updateExistingSubmittable(row, json, queryResponse);
            } else if (HttpStatus.NOT_FOUND.value() == queryResponse.getStatus()) {
                createNewSubmittable(row, createUriTemplate, json, expansionParams);
            } else {
                throw new HttpClientErrorException(
                        HttpStatus.valueOf(queryResponse.getStatus()),
                        queryResponse.getBody().toString()
                );
            }

            numberProcessed++;
            if (numberProcessed % 100 == 0) {
                sheet.setVersion(sheet.getVersion() + 1);
                sheetRepository.save(sheet);
            }

        }

        sheet.setStatus(SheetStatusEnum.Completed);
        sheet.setVersion(sheet.getVersion() + 1);
        sheetRepository.save(sheet);
    }

    private void createNewSubmittable(Row row, UriTemplate createUriTemplate, JSONObject json, Map<String, String> expansionParams) {
        URI createUri = createUriTemplate.expand(expansionParams);

        HttpResponse<JsonNode> response = uniRestWrapper.postJson(
                createUri.toString(),
                requestHeaders(),
                json
        );

        if (response.getStatus() > 201) {
            row.getErrors().add("Error while loading document: " + response.getBody().toString());
        } else {
            row.setProcessed(true);
        }

    }

    private void updateExistingSubmittable(Row row, JSONObject json, HttpResponse<JsonNode> queryResponse) {
        JSONObject linksObject = queryResponse.getBody().getObject().getJSONObject("_links");

        String selfURI = linksObject.getJSONObject("self").getString("href");

        HttpResponse<JsonNode> response = uniRestWrapper.putJson(
                selfURI, requestHeaders(), json
        );

        if (response.getStatus() > 201) {
            row.getErrors().add("Error while loading document: " + response.getBody().toString());
        } else {
            row.setProcessed(true);
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
