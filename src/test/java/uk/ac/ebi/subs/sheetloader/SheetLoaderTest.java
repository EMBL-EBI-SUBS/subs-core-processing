package uk.ac.ebi.subs.sheetloader;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import org.apache.http.ProtocolVersion;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringRunner;
import uk.ac.ebi.subs.data.component.Team;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.model.sheets.Row;
import uk.ac.ebi.subs.repository.model.sheets.Sheet;
import uk.ac.ebi.subs.repository.model.sheets.SheetStatusEnum;
import uk.ac.ebi.subs.repository.model.templates.AttributeCapture;
import uk.ac.ebi.subs.repository.model.templates.Capture;
import uk.ac.ebi.subs.repository.model.templates.FieldCapture;
import uk.ac.ebi.subs.repository.model.templates.JsonFieldType;
import uk.ac.ebi.subs.repository.model.templates.NoOpCapture;
import uk.ac.ebi.subs.repository.model.templates.Template;
import uk.ac.ebi.subs.repository.repos.SheetRepository;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class SheetLoaderTest {


    private SheetLoaderService sheetLoaderService;

    @MockBean
    private UniRestWrapper uniRestWrapper;

    @MockBean
    private TokenService tokenService;

    @MockBean
    private SheetRepository sheetRepository;

    private final static String TOKEN_PLACEHOLDER = "mytoken";
    private final static Map<String, String> HEADERS = new HashMap<>();


    @Before
    public void setUp() {
        sheetLoaderService = new SheetLoaderService(tokenService, uniRestWrapper, sheetRepository);
        sheetLoaderService.setRootApiUrl("http://localhost:8080/api");

        Submission submission = new Submission();
        submission.setTeam(Team.build("test"));
        submission.setId("1234");
        this.sheet = sheet(submission);

        HEADERS.put("Authorization", "Bearer " + TOKEN_PLACEHOLDER);
        HEADERS.put("Content-Type", "application/json");
        HEADERS.put("Accept", "application/hal+json");


        given(tokenService.aapToken()).willReturn(TOKEN_PLACEHOLDER);

        Template template = template();
        Map<String, Capture> captureMap = template.getColumnCaptures();

        expectedCaptures = Arrays.asList(
                captureMap.get("unique name"),
                captureMap.get("title"),
                captureMap.get("description"),
                captureMap.get("taxon"),
                captureMap.get("taxon id"),
                AttributeCapture.builder().build(),
                NoOpCapture.builder().build()
        );
    }

    private Sheet sheet;
    private List<Capture> expectedCaptures;

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
    public void testMappingHeadersToColumnCaptures() {

        List<Capture> actualColumnMappings = sheetLoaderService.mapColumns(
                sheet.getHeaderRow(),
                sheet.getTemplate().getColumnCaptures(),
                Optional.of(sheet.getTemplate().getDefaultCapture())
        );

        assertThat(actualColumnMappings, equalTo(expectedCaptures));
    }

    @Test
    public void testRowToDocumentConversion() {
        JSONObject expectedJson = stringToJsonObject(
                "{\n" +
                        "  \"alias\": \"s1\",\n" +
                        "  \"taxon\": \"Homo sapiens\",\n" +
                        "  \"taxonId\": 9606,\n" +
                        "  \"description\": \"\",\n" +
                        "  \"title\": \"\",\n" +
                        "  \"attributes\": {\n" +
                        "    \"height\": [\n" +
                        "      {\n" +
                        "        \"value\": \"1.7\",\n" +
                        "        \"units\": \"meters\"\n" +
                        "      }\n" +
                        "    ]\n" +
                        "  }\n" +
                        "}");


        JSONObject actualJson = sheetLoaderService.rowToDocument(
                sheet.getRows().get(0),
                expectedCaptures,
                sheet.getHeaderRow().getCells()
        );

        JSONAssert.assertEquals(expectedJson, actualJson, true);
        assertTrue(sheet.getRows().get(0).isProcessed());
    }

    @Test
    public void testLoadingOfDocumentsToMockApi() {

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

        sheetLoaderService.loadSheet(sheet);
    }

    private Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }


    private Sheet sheet(Submission submission) {
        Sheet sheet = new Sheet();

        sheet.setSubmission(submission);
        sheet.setTemplate(template());

        sheet.setVersion(0L);

        sheet.setHeaderRow(new Row(new String[]{"unique name", "title", "description", "taxon", "taxon id", "height", "units"}));
        sheet.addRow(new String[]{
                "s1", "", "", "Homo sapiens", "9606", "1.7", "meters"
        });
        sheet.addRow(new String[]{
                "s2", "", "", "Homo sapiens", "9606", "1.7", "meters"
        });

        sheet.setStatus(SheetStatusEnum.Submitted);

        return sheet;
    }

    private Template template() {
        Template template = Template.builder()
                .name("samples-template")
                .targetType("samples")
                .build();

        template
                .add(
                        "unique name",
                        FieldCapture.builder().fieldName("alias").build()
                )
                .add("title",
                        FieldCapture.builder().fieldName("title").build()
                )
                .add(
                        "description",
                        FieldCapture.builder().fieldName("description").build()
                )
                .add("taxon",
                        FieldCapture.builder().fieldName("taxon").build()
                )
                .add("taxon id",
                        FieldCapture.builder().fieldName("taxonId").fieldType(JsonFieldType.IntegerNumber).build()
                );

        template.setDefaultCapture(
                AttributeCapture.builder().build()
        );
        return template;
    }


    private JSONObject stringToJsonObject(String jsonContent) {
        return new JSONObject(jsonContent);
    }

}
