package progressmonitor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import uk.ac.ebi.subs.data.status.ProcessingStatusEnum;
import uk.ac.ebi.subs.processing.ProcessingCertificate;
import uk.ac.ebi.subs.processing.ProcessingCertificateEnvelope;
import uk.ac.ebi.subs.progressmonitor.ProgressMonitorService;
import uk.ac.ebi.subs.progressmonitor.SubmittablesBulkOperations;
import uk.ac.ebi.subs.repository.config.SubmittableConfig;
import uk.ac.ebi.subs.repository.model.ProcessingStatus;
import uk.ac.ebi.subs.repository.model.StoredSubmittable;
import uk.ac.ebi.subs.repository.processing.SupportingSampleRepository;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import util.Helpers;

import java.util.Arrays;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@EnableMongoRepositories(basePackageClasses = {
        ProcessingStatusRepository.class,
        SubmittablesBulkOperations.class,
        SupportingSampleRepository.class
})
@EnableAutoConfiguration
@SpringBootTest(classes = {
        ProgressMonitorService.class,
        SubmittablesBulkOperations.class,
        StoredSubmittable.class,
        StoredSubmittable.class,
        SubmittableConfig.class
})
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
