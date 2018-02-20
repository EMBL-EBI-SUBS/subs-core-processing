package uk.ac.ebi.subs.batchloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import org.apache.http.ProtocolVersion;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringRunner;
import uk.ac.ebi.subs.data.component.Team;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.model.SubmittablesBatch;
import uk.ac.ebi.subs.repository.model.sheets.Sheet;
import uk.ac.ebi.subs.repository.model.sheets.SheetStatusEnum;
import uk.ac.ebi.subs.repository.model.templates.AttributeCapture;
import uk.ac.ebi.subs.repository.model.templates.FieldCapture;
import uk.ac.ebi.subs.repository.model.templates.JsonFieldType;
import uk.ac.ebi.subs.repository.model.templates.Template;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class BatchLoaderTest {


    private SubmittablesBatchLoaderService submittablesBatchLoaderService;

    @MockBean
    private UniRestWrapper uniRestWrapper;

    @MockBean
    private TokenService tokenService;

    private final static String TOKEN_PLACEHOLDER = "mytoken";
    private final static Map<String, String> HEADERS = new HashMap<>();

    @Before
    public void setUp() {
        submittablesBatchLoaderService = new SubmittablesBatchLoaderService(tokenService, uniRestWrapper);
        submittablesBatchLoaderService.setRootApiUrl("http://localhost:8080/api");

        Submission submission = new Submission();
        submission.setTeam(Team.build("test"));
        submission.setId("1234");
        this.batch = batch(submission);

        HEADERS.put("Authorization", "Bearer " + TOKEN_PLACEHOLDER);
        HEADERS.put("Content-Type", "application/json");
        HEADERS.put("Accept", "application/hal+json");


        given(tokenService.aapToken()).willReturn(TOKEN_PLACEHOLDER);
    }

    private SubmittablesBatch batch;

    private HttpResponse<JsonNode> response(HttpStatus status) {
        return response(status, new JSONObject());
    }

    private HttpResponse<JsonNode> response(HttpStatus status, JSONObject jsonBody) {
        ProtocolVersion pv = new ProtocolVersion("HTTP", 1, 1);
        org.apache.http.HttpResponse response = new BasicHttpResponse(pv, status.value(), status.getReasonPhrase());

        try {
            response.setEntity(new StringEntity(jsonBody.toString()));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        HttpResponse<JsonNode> unirestResponse = new HttpResponse<>(response, JsonNode.class);
        return unirestResponse;
    }


    @Test
    public void test() {

        when(uniRestWrapper.getJson(
                "http://localhost:8080/api/samples/search/by-submissionId-and-alias?submissionId=1234&alias=s1",
                HEADERS
        )).thenReturn(
                response(HttpStatus.NOT_FOUND)
        );

        when(uniRestWrapper.postJson(
                eq("http://localhost:8080/api/submissions/1234/contents/samples"),
                eq(HEADERS),
                any(JSONObject.class)
        )).thenReturn(
                response(HttpStatus.OK)
        );

        JSONObject existingSampleJsonPayload = new JSONObject();
        Map<String, Object> links = mapOf("self", mapOf("href", "http://localhost:8080/api/samples/b02923ec-a9c2-4de8-95e4-a65deb1e0029"));
        existingSampleJsonPayload.put("_links", links);

        when(uniRestWrapper.getJson(
                "http://localhost:8080/api/samples/search/by-submissionId-and-alias?submissionId=1234&alias=s2",
                HEADERS
        )).thenReturn(
                response(HttpStatus.OK, existingSampleJsonPayload) //TODO needs body
        );

        when(uniRestWrapper.putJson(
                eq("http://localhost:8080/api/samples/b02923ec-a9c2-4de8-95e4-a65deb1e0029"),
                eq(HEADERS),
                any(JSONObject.class)
        )).thenReturn(
                response(HttpStatus.OK)
        );

        submittablesBatchLoaderService.loadBatch(batch);
    }

    private Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }


    private SubmittablesBatch batch(Submission submission) {
        SubmittablesBatch batch = new SubmittablesBatch();

        batch.setSubmission(submission);
        batch.setTargetType("samples");

        batch.addDocument(new SubmittablesBatch.Document());
        batch.addDocument(new SubmittablesBatch.Document());

        batch.getDocuments().get(0).setDocument(
                stringToJsonNode(
                "{\n" +
                "  \"alias\": \"s1\",\n" +
                "  \"taxon\": \"Homo sapiens\",\n" +
                "  \"taxonId\": 9606,\n" +
                "  \"attributes\": {\n" +
                "    \"height\": [\n" +
                "      {\n" +
                "        \"value\": \"1.7\",\n" +
                "        \"units\": \"meters\"\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}"))
        ;
        batch.getDocuments().get(1).setDocument(
                stringToJsonNode("{\n" +
                "  \"alias\": \"s2\",\n" +
                "  \"taxon\": \"Homo sapiens\",\n" +
                "  \"taxonId\": 9606,\n" +
                "  \"attributes\": {\n" +
                "    \"height\": [\n" +
                "      {\n" +
                "        \"value\": \"1.7\",\n" +
                "        \"units\": \"meters\"\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}"))
        ;
        batch.setStatus("Submitted");

        return batch;
    }

    public static com.fasterxml.jackson.databind.JsonNode stringToJsonNode(String jsonContent){
        ObjectMapper mapper = new ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode actualObj = null;
        try {
            actualObj = mapper.readTree(jsonContent);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return actualObj;
    }

}
