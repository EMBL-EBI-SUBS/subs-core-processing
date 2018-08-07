package uk.ac.ebi.subs.processing.dispatcher;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import uk.ac.ebi.subs.data.submittable.AssayData;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;
import uk.ac.ebi.subs.processing.fileupload.UploadedFile;
import uk.ac.ebi.subs.repository.model.Analysis;
import uk.ac.ebi.subs.repository.model.Submission;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest
public class DispatcherServiceTest {

    @Autowired
    private DispatcherService dispatcherService;

    @Autowired
    private DispatchTestSubmissionSetup dispatchTestSubmissionSetup;

    private static final String ASSAYDATA_ALIAS = "test-assayData";
    private static final String ANALYSIS_ALIAS = "test-analysis";
    private Submission submission;


    @Test
    public void whenSubmissionReferencesFiles_thenUploadedFilesAddedToEnvelope() {
        submission = dispatchTestSubmissionSetup.createSubmission();

        AssayData assayDataWith2Files = dispatchTestSubmissionSetup.createAssayDataWithNbOfFiles(
                ASSAYDATA_ALIAS+"1", submission, 2);
        AssayData assayDataWith3Files = dispatchTestSubmissionSetup.createAssayDataWithNbOfFiles(
                ASSAYDATA_ALIAS+"2", submission, 3);

        Analysis analysisWith1File = dispatchTestSubmissionSetup.createAnalysisWithNbOfFiles(
                ANALYSIS_ALIAS+"1", submission, 1
        );

        SubmissionEnvelope submissionEnvelope = new SubmissionEnvelope();

        submissionEnvelope.setSubmission(submission);
        submissionEnvelope.getAssayData().addAll(Arrays.asList(assayDataWith2Files, assayDataWith3Files));
        submissionEnvelope.getAnalyses().add(analysisWith1File);

        dispatcherService.insertUploadedFiles(submissionEnvelope);

        assertThat(submissionEnvelope.getUploadedFiles().size(), is(equalTo(6)));

        List<String> uploadedFilenames =
                submissionEnvelope.getUploadedFiles().stream().map(UploadedFile::getFilename).collect(Collectors.toList());
        submissionEnvelope.getAssayData().forEach( assayData -> {
            assayData.getFiles().forEach(file -> {
                assertTrue(uploadedFilenames.contains(file.getName()));
            });
        });
    }
}
