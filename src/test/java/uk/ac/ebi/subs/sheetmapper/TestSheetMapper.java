package uk.ac.ebi.subs.sheetmapper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import uk.ac.ebi.subs.CoreProcessingApp;
import uk.ac.ebi.subs.data.component.Attribute;
import uk.ac.ebi.subs.data.component.Attributes;
import uk.ac.ebi.subs.processing.dispatcher.DispatchTestSubmissionSetup;
import uk.ac.ebi.subs.repository.model.Sample;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.model.sheets.Sheet;
import uk.ac.ebi.subs.repository.model.sheets.SheetStatusEnum;
import uk.ac.ebi.subs.repository.model.templates.AttributeCapture;
import uk.ac.ebi.subs.repository.model.templates.Capture;
import uk.ac.ebi.subs.repository.model.templates.FieldCapture;
import uk.ac.ebi.subs.repository.model.templates.JsonFieldType;
import uk.ac.ebi.subs.repository.model.templates.Template;
import uk.ac.ebi.subs.repository.repos.SheetRepository;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;
import uk.ac.ebi.subs.repository.repos.submittables.SampleRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;


@RunWith(SpringRunner.class)
@EnableAutoConfiguration
@SpringBootTest(classes = {CoreProcessingApp.class})
public class TestSheetMapper {


    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private SampleRepository sampleRepository;

    @Autowired
    private SheetRepository sheetRepository;

    @Autowired
    private DispatchTestSubmissionSetup dispatchTestSubmissionSetup;

    private Submission submission;
    private Sheet sheet;

    @Autowired
    private SheetMapperService sheetMapperService;

    @Before
    public void buildUp() {
        clearDatabases();
        this.submission = dispatchTestSubmissionSetup.createSubmission();
        this.sheet = sheetRepository.save(sheet(submission));
    }

    @After
    public void clearDatabases() {
        submissionRepository.deleteAll();
        sampleRepository.deleteAll();
        sheetRepository.deleteAll();
    }

    @Test
    public void test(){
        sheetMapperService.mapSheet(sheet);

        List<Sample> samples = sampleRepository.findAll();

        assertThat(samples,hasSize(2));

        assertThat(samples.get(0).getAlias(),equalTo("s1"));
        assertThat(samples.get(1).getAlias(),equalTo("s2"));

        for (Sample sample : samples){
            assertNotNull(sample.getAttributes());
            assertNotNull(sample.getAttributes().get("height"));
            assertNotNull(sample.getAttributes().get("height").iterator().next());

            Attribute heightAttribute = sample.getAttributes().get("height").iterator().next();

            assertThat(heightAttribute.getValue(),equalTo(1.7));
            assertThat(heightAttribute.getUnits(),equalTo("meters"));

            assertThat(sample.getTaxon(),equalTo("Homo sapiens"));
            assertThat(sample.getTaxonId(),equalTo(9606L));
        }




    }


    private Sheet sheet(Submission submission) {
        Template template = template();

        Sheet sheet = new Sheet();

        sheet.setSubmission(submission);
        sheet.setTemplate(template);

        sheet.addRow(new String[]{"alias", "taxon id", "taxon", "height", "units"});
        sheet.addRow(new String[]{"s1", "9606", "Homo sapiens", "1.7", "meters"});
        sheet.addRow(new String[]{"s2", "9606", "Homo sapiens", "1.7", "meters"});

        sheet.setHeaderRowIndex(0);

        sheet.setStatus(SheetStatusEnum.Submitted);

        mapHeadings(sheet);

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

    public void mapHeadings(Sheet sheet) {
        Map<String, Capture> columnCaptures = sheet.getTemplate().getColumnCaptures();
        columnCaptures.entrySet().stream().forEach(entry ->
                entry.getValue().setDisplayName(entry.getKey())
        );


        Optional<Capture> defaultCapture = Optional.of(sheet.getTemplate().getDefaultCapture());

        if (sheet.getHeaderRowIndex() == null) return;

        List<String> headerRow = sheet.getRows().get(sheet.getHeaderRowIndex()).getCells();

        Capture[] emptyCaptures = new Capture[headerRow.size()];

        List<Capture> capturePositions = new ArrayList<>(Arrays.asList(emptyCaptures));


        int position = 0;

        while (position < headerRow.size()) {

            String currentHeader = headerRow.get(position);
            currentHeader = currentHeader.trim().toLowerCase();


            if (columnCaptures.containsKey(currentHeader)) {
                Capture capture = columnCaptures.get(currentHeader);

                position = capture.map(position, capturePositions, headerRow);
            } else if (defaultCapture.isPresent()) {
                Capture clonedCapture = defaultCapture.get().copy();
                clonedCapture.setDisplayName(currentHeader);

                position = clonedCapture.map(position, capturePositions, headerRow);
            } else {
                position++;
            }

        }

        sheet.setMappings(capturePositions);
    }
}
