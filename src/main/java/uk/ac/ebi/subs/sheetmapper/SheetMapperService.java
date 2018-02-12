package uk.ac.ebi.subs.sheetmapper;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.web.JsonPath;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.UriTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
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


    public SheetMapperService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Value("${usi.apiRootUrl}")
    private String rootApiUrl;

    private final RestTemplate restTemplate;

    public void mapSheet(Sheet sheet) {
        Assert.notNull(sheet);
        Assert.notNull(sheet.getTemplate());
        Assert.notNull(sheet.getSubmission());
        Assert.notNull(sheet.getRows());
        Assert.notNull(sheet.getMappings());


        UriTemplate submissionUriTemplate  = new UriTemplate(rootApiUrl + "/submissions/{submissionId}");
        UriTemplate searchUriTemplate  = new UriTemplate(rootApiUrl + "/{type}/search/by-submissionId-and-alias{?submissionId,alias,projection}");
        UriTemplate createUriTemplate  = new UriTemplate(rootApiUrl + "/submissions/{submissionId}/contents/{type}");

        String targetType = sheet.getTemplate().getTargetType().toLowerCase();
        String submissionId = sheet.getSubmission().getId();

        Map<String, String> submissionExpansionParams = new HashMap<>();
        submissionExpansionParams.put("submissionId", submissionId);

        URI submissionUri = submissionUriTemplate.expand(submissionExpansionParams);

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

                    try {
                        ResponseEntity<SimpleResource> queryResult = restTemplate.getForEntity(queryUri, SimpleResource.class);

                        //update to existing record
                        if (HttpStatus.OK.equals(queryResult.getStatusCode())) {
                            SimpleResource submittable = queryResult.getBody();
                            String selfURI = submittable.getLink("self").expand().getHref();

                            restTemplate.put(selfURI,json.toString());
                        }
                    }
                    catch(HttpClientErrorException e){
                        //create new record
                        if (e.getRawStatusCode() == HttpStatus.NOT_FOUND.value()){
                            URI createUri = createUriTemplate.expand(expansionParams);
                            restTemplate.postForLocation(createUri, json.toString());
                        }
                        else {
                            //actual error
                            throw e;
                        }
                    }



                    //TODO error handling


                });


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
