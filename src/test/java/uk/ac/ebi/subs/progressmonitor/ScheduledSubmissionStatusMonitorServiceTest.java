package uk.ac.ebi.subs.progressmonitor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit4.SpringRunner;
import uk.ac.ebi.subs.CoreProcessingApp;
import uk.ac.ebi.subs.data.status.SubmissionStatusEnum;
import uk.ac.ebi.subs.repository.model.SubmissionStatus;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;
import uk.ac.ebi.subs.repository.repos.status.SubmissionStatusRepository;
import uk.ac.ebi.subs.util.Helpers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static uk.ac.ebi.subs.processing.initialsubmissionprocessing.SubmissionStatusMessages.PROCESSING_IN_PROGRESS_MESSAGE;
import static uk.ac.ebi.subs.processing.initialsubmissionprocessing.SubmissionStatusMessages.SUBMITTED_MESSAGE;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = CoreProcessingApp.class)
public class ScheduledSubmissionStatusMonitorServiceTest {

    @Autowired
    private ScheduledSubmissionStatusMonitorService scheduledSubmissionStatusMonitorService;

    @Autowired
    private SubmissionRepository submissionRepository;
    @Autowired
    private SubmissionStatusRepository submissionStatusRepository;

    private static final String SUBMISSION1_UUID = UUID.randomUUID().toString();
    private static final String SUBMISSION2_UUID = UUID.randomUUID().toString();
    private static final String SUBMISSION3_UUID = UUID.randomUUID().toString();

    private static final String SUBMISSION_STATUS1_UUID = UUID.randomUUID().toString();
    private static final String SUBMISSION_STATUS2_UUID = UUID.randomUUID().toString();
    private static final String SUBMISSION_STATUS3_UUID = UUID.randomUUID().toString();

    @Before
    public void setup() {
        cleanUpRepositories();
        SubmissionStatus submissionStatus1 = submissionStatusRepository.save(
                Helpers.createSubmissionStatus(SUBMISSION_STATUS1_UUID, SubmissionStatusEnum.Processing));
        SubmissionStatus submissionStatus2 = submissionStatusRepository.save(
                Helpers.createSubmissionStatus(SUBMISSION_STATUS2_UUID, SubmissionStatusEnum.Submitted));
        SubmissionStatus submissionStatus3 = submissionStatusRepository.save(
                Helpers.createSubmissionStatus(SUBMISSION_STATUS3_UUID, SubmissionStatusEnum.Submitted));

        LocalDateTime nowMinus23Hours = LocalDateTime.now().minusHours(23L);
        LocalDateTime nowMinus23Days = LocalDateTime.now().minusDays(23L);
        LocalDateTime nowMinus11Hours = LocalDateTime.now().minusHours(11L);

        submissionRepository.save(Helpers.createSubmittedSubmission(
                SUBMISSION1_UUID, submissionStatus1,
                Date.from(nowMinus23Hours.toInstant(ZoneOffset.UTC))));
        submissionRepository.save(Helpers.createSubmittedSubmission(
                SUBMISSION2_UUID, submissionStatus2,
                Date.from(nowMinus23Days.toInstant(ZoneOffset.UTC))));
        submissionRepository.save(Helpers.createSubmittedSubmission(
                SUBMISSION3_UUID, submissionStatus3,
                Date.from(nowMinus11Hours.toInstant(ZoneOffset.UTC))));
    }

    @After
    public void tearDown() {
        cleanUpRepositories();
    }

    private void cleanUpRepositories() {
        submissionStatusRepository.deleteAll();
        submissionRepository.deleteAll();
    }

    @Test
    public void getBackAllSubmission_thatSubmissionStatusNotInFinishedStatus() {
        Pageable pageable = new PageRequest(0, 10);
        List<String> originalMessages = submissionStatusRepository.findAll(pageable).getContent().stream()
                .map(SubmissionStatus::getMessage)
                .collect(Collectors.toList());
        assertThat(originalMessages.size(), is(equalTo(3)));
        originalMessages.forEach(message ->
                assertThat(message, isEmptyOrNullString())
        );

        scheduledSubmissionStatusMonitorService.setSubmissionStatusMessageIfOldAndNotInFinishedStatus();

        List<String> modifiedMessages = submissionStatusRepository.findAll(pageable).getContent().stream()
                .map(SubmissionStatus::getMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        assertThat(modifiedMessages.size(), is(equalTo(2)));
        modifiedMessages.forEach(message ->
                assertThat(message, not(isEmptyOrNullString()))
        );

        final SubmissionStatus submissionStatus1 = submissionStatusRepository.findOne(SUBMISSION_STATUS1_UUID);
        assertNotNull(submissionStatus1);
        assertThat(submissionStatus1.getMessage(), is(equalTo(PROCESSING_IN_PROGRESS_MESSAGE)));

        final SubmissionStatus submissionStatus2 = submissionStatusRepository.findOne(SUBMISSION_STATUS2_UUID);
        assertNotNull(submissionStatus2);
        assertThat(submissionStatus2.getMessage(), is(equalTo(SUBMITTED_MESSAGE)));
    }
}
