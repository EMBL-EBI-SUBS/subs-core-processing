package uk.ac.ebi.subs.sheetmapper;

import org.apache.http.entity.ContentType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import uk.ac.ebi.subs.CoreProcessingApp;
import uk.ac.ebi.subs.data.component.Team;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.model.sheets.Sheet;
import uk.ac.ebi.subs.repository.model.sheets.SheetStatusEnum;
import uk.ac.ebi.subs.repository.model.templates.AttributeCapture;
import uk.ac.ebi.subs.repository.model.templates.Capture;
import uk.ac.ebi.subs.repository.model.templates.FieldCapture;
import uk.ac.ebi.subs.repository.model.templates.JsonFieldType;
import uk.ac.ebi.subs.repository.model.templates.Template;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@RunWith(SpringRunner.class)
@EnableAutoConfiguration
@SpringBootTest(classes = {CoreProcessingApp.class})
public class TestSheetMapper {


    @Autowired
    private SheetMapperService sheetMapperService;

    @Autowired
    private RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    @Before
    public void setUp() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    private Sheet sheet;


    @Before
    public void buildUp() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
        Submission submission = new Submission();
        submission.setTeam(Team.build("test"));
        submission.setId("1234");
        this.sheet = sheet(submission);
    }

    @Test
    public void test() {
        mockServer.expect(
                requestTo("http://localhost:8080/api/samples/search/by-submissionId-and-alias?submissionId=1234&alias=s1")
        )
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        mockServer.expect(
                requestTo("http://localhost:8080/api/submissions/1234/contents/samples")
        )
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK));

        mockServer.expect(
                requestTo("http://localhost:8080/api/samples/search/by-submissionId-and-alias?submissionId=1234&alias=s2")
        )
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(sampleQueryResponse(), MediaTypes.HAL_JSON));

        mockServer.expect(
                requestTo("http://localhost:8080/api/samples/b02923ec-a9c2-4de8-95e4-a65deb1e0029")
        )
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());



        sheetMapperService.mapSheet(sheet);

        mockServer.verify();


    }

    private String sampleQueryResponse(){
        return "{\n" +
                "  \"alias\": \"s2\",\n" +
                "  \"team\": {\n" +
                "    \"name\": \"self.usi-user\",\n" +
                "    \"_links\": {\n" +
                "      \"submissions\": {\n" +
                "        \"href\": \"http://localhost:8080/api/submissions/search/by-team?teamName=self.usi-user\"\n" +
                "      },\n" +
                "      \"submissions:create\": {\n" +
                "        \"href\": \"http://localhost:8080/api/teams/self.usi-user/submissions\"\n" +
                "      },\n" +
                "      \"items\": {\n" +
                "        \"href\": \"http://localhost:8080/api/teams/self.usi-user/items\"\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"attributes\": {\n" +
                "    \"height\": [\n" +
                "      {\n" +
                "        \"value\": \"1.7\"\n" +
                "      }\n" +
                "    ]\n" +
                "  },\n" +
                "  \"sampleRelationships\": [\n" +
                "\n" +
                "  ],\n" +
                "  \"taxonId\": 9606,\n" +
                "  \"taxon\": \"Homo sapiens\",\n" +
                "  \"createdDate\": \"2017-10-24T09:09:50.372+0000\",\n" +
                "  \"lastModifiedDate\": \"2017-10-24T09:09:50.372+0000\",\n" +
                "  \"createdBy\": \"usr-412e4db6-0323-4911-bb3c-3376f9c59e0b\",\n" +
                "  \"lastModifiedBy\": \"usr-412e4db6-0323-4911-bb3c-3376f9c59e0b\",\n" +
                "  \"_embedded\": {\n" +
                "    \"processingStatus\": {\n" +
                "      \"lastModifiedDate\": \"2017-10-24T09:09:50.337+0000\",\n" +
                "      \"lastModifiedBy\": \"usr-412e4db6-0323-4911-bb3c-3376f9c59e0b\",\n" +
                "      \"status\": \"Draft\",\n" +
                "      \"alias\": \"s2\",\n" +
                "      \"submittableType\": \"Sample\",\n" +
                "      \"_links\": {\n" +
                "        \"self\": {\n" +
                "          \"href\": \"http://localhost:8080/api/processingStatuses/f302bf46-eced-4cfd-a553-a938a82cb4f7{?projection}\",\n" +
                "          \"templated\": true\n" +
                "        }\n" +
                "      }\n" +
                "    },\n" +
                "    \"validationResult\": {\n" +
                "      \"validationStatus\": \"Complete\",\n" +
                "      \"_links\": {\n" +
                "        \"self\": {\n" +
                "          \"href\": \"http://localhost:8080/api/validationResults/c479ce5c-876a-4f67-8f7c-1503c10607c1{?projection}\",\n" +
                "          \"templated\": true\n" +
                "        }\n" +
                "      }\n" +
                "    },\n" +
                "    \"submission\": {\n" +
                "      \"lastModifiedDate\": \"2017-10-18T15:05:36.789+0000\",\n" +
                "      \"lastModifiedBy\": \"usr-412e4db6-0323-4911-bb3c-3376f9c59e0b\",\n" +
                "      \"submitter\": \"api-tester@ebi.ac.uk\",\n" +
                "      \"team\": \"self.usi-user\",\n" +
                "      \"createdBy\": \"usr-412e4db6-0323-4911-bb3c-3376f9c59e0b\",\n" +
                "      \"createdDate\": \"2017-10-18T15:05:36.789+0000\",\n" +
                "      \"submissionStatus\": \"Draft\",\n" +
                "      \"_links\": {\n" +
                "        \"self\": {\n" +
                "          \"href\": \"http://localhost:8080/api/submissions/8e9ac291-eeea-4ae7-a9d2-f9e636dc72c1{?projection}\",\n" +
                "          \"templated\": true\n" +
                "        },\n" +
                "        \"submissionStatus\": {\n" +
                "          \"href\": \"http://localhost:8080/api/submissions/8e9ac291-eeea-4ae7-a9d2-f9e636dc72c1/submissionStatus\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"_links\": {\n" +
                "    \"self\": {\n" +
                "      \"href\": \"http://localhost:8080/api/samples/b02923ec-a9c2-4de8-95e4-a65deb1e0029\"\n" +
                "    },\n" +
                "    \"sample\": {\n" +
                "      \"href\": \"http://localhost:8080/api/samples/b02923ec-a9c2-4de8-95e4-a65deb1e0029{?projection}\",\n" +
                "      \"templated\": true\n" +
                "    },\n" +
                "    \"history\": {\n" +
                "      \"href\": \"http://localhost:8080/api/samples/search/history?teamName=self.usi-user&alias=D1\"\n" +
                "    },\n" +
                "    \"current-version\": {\n" +
                "      \"href\": \"http://localhost:8080/api/samples/search/current-version?teamName=self.usi-user&alias=D1\"\n" +
                "    },\n" +
                "    \"self:update\": {\n" +
                "      \"href\": \"http://localhost:8080/api/samples/b02923ec-a9c2-4de8-95e4-a65deb1e0029\"\n" +
                "    },\n" +
                "    \"self:delete\": {\n" +
                "      \"href\": \"http://localhost:8080/api/samples/b02923ec-a9c2-4de8-95e4-a65deb1e0029\"\n" +
                "    },\n" +
                "    \"processingStatus\": {\n" +
                "      \"href\": \"http://localhost:8080/api/samples/b02923ec-a9c2-4de8-95e4-a65deb1e0029/processingStatus\"\n" +
                "    },\n" +
                "    \"submission\": {\n" +
                "      \"href\": \"http://localhost:8080/api/samples/b02923ec-a9c2-4de8-95e4-a65deb1e0029/submission\"\n" +
                "    },\n" +
                "    \"validationResult\": {\n" +
                "      \"href\": \"http://localhost:8080/api/samples/b02923ec-a9c2-4de8-95e4-a65deb1e0029/validationResult\"\n" +
                "    }\n" +
                "  }\n" +
                "}";
    }


    private Sheet sheet(Submission submission) {
        Template template = template();
        Map<String, Capture> columnCaptures = template.getColumnCaptures();
        Sheet sheet = new Sheet();

        sheet.setSubmission(submission);
        sheet.setTemplate(template);

        sheet.addRow(new String[]{"alias", "taxon id", "taxon", "height", "units"});
        sheet.addRow(new String[]{"s1", "9606", "Homo sapiens", "1.7", "meters"});
        sheet.addRow(new String[]{"s2", "9606", "Homo sapiens", "1.7", "meters"});

        List<Capture> captures = Arrays.asList(
                columnCaptures.get("alias"),
                columnCaptures.get("taxon id"),
                columnCaptures.get("taxon"),
                template.getDefaultCapture().copy()
        );
        sheet.setMappings(captures);
        sheet.setHeaderRowIndex(0);
        sheet.setStatus(SheetStatusEnum.Submitted);

        return sheet;
    }

    private Template template() {
        Template template = Template.builder().name("test-template").targetType("samples").build();
        template
                .add(
                        "alias",
                        FieldCapture.builder().fieldName("alias").build()
                )
                .add(
                        "taxon id",
                        FieldCapture.builder().fieldName("taxonId").fieldType(JsonFieldType.IntegerNumber).build()
                )
                .add(
                        "taxon",
                        FieldCapture.builder().fieldName("taxon").build()
                );

        template.setDefaultCapture(
                AttributeCapture.builder().build()
        );

        return template;
    }

}
