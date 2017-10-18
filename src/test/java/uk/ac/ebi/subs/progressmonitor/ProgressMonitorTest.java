package uk.ac.ebi.subs.progressmonitor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import uk.ac.ebi.subs.CoreProcessingApp;
import uk.ac.ebi.subs.data.status.ProcessingStatusEnum;
import uk.ac.ebi.subs.processing.ProcessingCertificate;
import uk.ac.ebi.subs.processing.ProcessingCertificateEnvelope;
import uk.ac.ebi.subs.repository.model.ProcessingStatus;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import uk.ac.ebi.subs.util.Helpers;

import java.util.Arrays;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@EnableAutoConfiguration
@SpringBootTest(classes = { CoreProcessingApp.class } )
public class ProgressMonitorTest {

    private String submittableId = "1234-5678";

    @Autowired
    private ProcessingStatusRepository processingStatusRepository;

    @Autowired
    private ProgressMonitorService progressMonitorService;

    @Before
    public void setUp() throws Exception {
        ProcessingStatus ps = Helpers.generateProcessingStatus(submittableId, ProcessingStatusEnum.Processing);
        processingStatusRepository.insert(ps);
    }

    @Test
    public void updateSubmittablesFromCertificatesTest() {
        ProcessingCertificate pc = Helpers.generateProcessingCertificate(submittableId, ProcessingStatusEnum.Submitted);
        ProcessingCertificateEnvelope pce = Helpers.generateProcessingCertificateEnvelope(Arrays.asList(pc));

        progressMonitorService.updateSubmittablesFromCertificates(pce);

        assertThat(
                processingStatusRepository.findBySubmittableId(submittableId).getStatus(), equalTo(ProcessingStatusEnum.Submitted.name())
        );

        assertThat(
                processingStatusRepository.findBySubmittableId(submittableId).getAccession(), equalTo("SAMEA12345")
        );
    }

    @After
    public void tearDown() throws Exception {
        processingStatusRepository.deleteAll();
    }
}
