package uk.ac.ebi.subs.batchloader;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.repository.repos.SubmittablesBatchRepository;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

@Component
/**
 * The batch loading process leaves processed batches in the db
 * We need them in the short term, so the UI can look up progress, but we don't need them in the long term.
 *
 * Every few hours, we can remove old completed batches from the db.
 *
 * At the moment, it's every 4 hours, remove completed records over a week old
 */
public class BatchCleanupTask {

    private static final long FOUR_HOUR_IN_MILLIS = 1000l * 60 * 60 * 4;

    private SubmittablesBatchRepository submittablesBatchRepository;


    @Scheduled(fixedDelay = FOUR_HOUR_IN_MILLIS, initialDelay = FOUR_HOUR_IN_MILLIS)
    public void cleanUpOldSubmittableBatches(){

        Calendar cal = new GregorianCalendar();
        cal.add(Calendar.DAY_OF_MONTH, -7);
        Date sevenDaysAgo = cal.getTime();

        submittablesBatchRepository.removeByLastModifiedDateBeforeAndStatus(
                sevenDaysAgo,
                "Completed"
        );

    }
}
