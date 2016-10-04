package progressmonitor;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import uk.ac.ebi.subs.data.Submission;
import uk.ac.ebi.subs.repository.SubmissionRepository;
import uk.ac.ebi.subs.repository.SubmissionService;
import uk.ac.ebi.subs.repository.SubmissionServiceImpl;
import util.Helpers;

import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@EnableMongoRepositories(basePackageClasses = SubmissionRepository.class)
@SpringBootTest(classes = {ProgressMonitorTestConfiguration.class, SubmissionServiceImpl.class})
public class ProgressMonitorTest {

    @Autowired
    SubmissionService submissionService;

    @Autowired
    MongoTemplate mongoTemplate;

    @Before //This runs before each test
    public void setUp() {
        mongoTemplate.getCollection("submission").drop();
        submissionService.storeSubmission(Helpers.generateTestSubmission());
    }

    @Test
    public void testSaveSubmission() {
        submissionService.storeSubmission(Helpers.generateTestSubmission());
    }

    @Test
    public void getSubmissionById() {
        try {
            Submission sub1 = submissionService.fetchSubmissions(new PageRequest(0,100)).getContent().get(0);

            Submission sub2 = submissionService.fetchSubmission(sub1.getId());

            Assert.assertEquals(sub1, sub2);

        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
            Assert.fail("No submissions found.");
        }
    }

    @Test
    public void getSubmissionByDomainName() {
        try {
            Submission sub1 = submissionService.fetchSubmissions(new PageRequest(0,100)).getContent().get(0);
            List<Submission> submissionList = submissionService.fetchSubmissionsByDomainName(new PageRequest(0,100),"subs-test").getContent();

            Assert.assertTrue(submissionList.contains(sub1));

        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
            Assert.fail("No submissions found.");
        }
    }
}
