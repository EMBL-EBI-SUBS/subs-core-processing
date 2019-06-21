package uk.ac.ebi.subs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.ac.ebi.subs.repository.model.StoredSubmittable;
import uk.ac.ebi.subs.repository.repos.submittables.SubmittableRepository;

import java.util.Map;

@SpringBootApplication
public class TestCoreProcessingApp {

    public static void main(String[] args) {
        SpringApplication.run(TestCoreProcessingApp.class, args);
    }

    @MockBean
    private Map<String, SubmittableRepository<? extends StoredSubmittable>> dataTyapeRepositoryMap;

}
