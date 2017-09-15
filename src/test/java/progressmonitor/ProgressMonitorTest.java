package progressmonitor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import uk.ac.ebi.subs.progressmonitor.MonitorService;
import uk.ac.ebi.subs.progressmonitor.SubmittablesBulkOperations;
import uk.ac.ebi.subs.repository.config.SubmittableConfig;
import uk.ac.ebi.subs.repository.model.StoredSubmittable;
import uk.ac.ebi.subs.repository.processing.SupportingSampleRepository;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;

@RunWith(SpringJUnit4ClassRunner.class)
@EnableMongoRepositories(basePackageClasses = {
        ProcessingStatusRepository.class,
        SubmittablesBulkOperations.class,
        SupportingSampleRepository.class
})
@EnableAutoConfiguration
@SpringBootTest(classes = {
        MonitorService.class,
        SubmittablesBulkOperations.class,
        StoredSubmittable.class,
        StoredSubmittable.class,
        SubmittableConfig.class
})
public class ProgressMonitorTest {

    @Before
    public void setUp() { }

    @Test
    public void updateSubmittablesFromCertificatesTest() { }
}
