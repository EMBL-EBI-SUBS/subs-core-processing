package progressmonitor;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import uk.ac.ebi.subs.data.submittable.Submission;
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

    private static MongoClient client;

    @Before //This runs before each test
    public void setUp() {
        client = new MongoClient();
        MongoDatabase db = client.getDatabase("test");
        MongoCollection collection = db.getCollection("submission");
        collection.drop();

        submissionService.storeSubmission(Helpers.generateTestSubmission());
    }

    @Test
    public void testSaveSubmission() {
        submissionService.storeSubmission(Helpers.generateTestSubmission());
    }

    @Test
    public void getSubmissionById() {
        try {
            Submission sub1 = submissionService.fetchSubmissions().get(0);

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
            Submission sub1 = submissionService.fetchSubmissions().get(0);
            List<Submission> submissionList = submissionService.fetchSubmissionsByDomainName("subs-test");

            Assert.assertTrue(submissionList.contains(sub1));

        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
            Assert.fail("No submissions found.");
        }
    }
}
