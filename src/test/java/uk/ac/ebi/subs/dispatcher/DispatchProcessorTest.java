package uk.ac.ebi.subs.dispatcher;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.test.context.junit4.SpringRunner;
import uk.ac.ebi.subs.DispatcherApplication;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.data.submittable.Sample;
import uk.ac.ebi.subs.data.submittable.Study;
import uk.ac.ebi.subs.data.submittable.Submission;
import uk.ac.ebi.subs.messaging.Exchanges;
import uk.ac.ebi.subs.messaging.Queues;
import uk.ac.ebi.subs.messaging.Topics;

import java.util.Date;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = DispatcherApplication.class)
public class DispatchProcessorTest {

    int messagesToEna = 0;
    int messagesToBioSamples = 0;
    int messagesToAe = 0;

    Submission sub;
    Sample sample;
    Study enaStudy;
    Study aeStudy;

    @Autowired
    RabbitMessagingTemplate rabbitMessagingTemplate;

    @Autowired
    MessageConverter messageConverter;

    @Before
    public void setUp() {
        this.rabbitMessagingTemplate.setMessageConverter(this.messageConverter);

        this.messagesToEna = 0;
        this.messagesToBioSamples = 0;
        this.messagesToAe = 0;

        sub = new Submission();
        sub.setId("DispatchTestSub");
        sub.getSubmitter().setEmail("test@ebi.ac.uk");
        sub.getDomain().setName("testDomain");
        sub.setSubmissionDate(new Date());

        sample = new Sample();
        sample.setArchive(Archive.Usi);

        sub.getSamples().add(sample);

        enaStudy = new Study();
        enaStudy.setArchive(Archive.Ena);

        sub.getStudies().add(enaStudy);

        aeStudy = new Study();
        aeStudy.setArchive(Archive.ArrayExpress);

        sub.getStudies().add(aeStudy);
    }

    @Test
    public void testTheLoop() throws InterruptedException {
        rabbitMessagingTemplate.convertAndSend(Exchanges.SUBMISSIONS,Topics.EVENT_SUBMISSION_SUBMITTED, sub);


        sample.setAccession("SAMPLE1");

        rabbitMessagingTemplate.convertAndSend(Exchanges.SUBMISSIONS,Topics.EVENT_SUBMISSION_PROCESSED, sub);


        enaStudy.setAccession("ENA1");

        rabbitMessagingTemplate.convertAndSend(Exchanges.SUBMISSIONS,Topics.EVENT_SUBMISSION_PROCESSED, sub);

        aeStudy.setAccession("AE1");

        rabbitMessagingTemplate.convertAndSend(Exchanges.SUBMISSIONS,Topics.EVENT_SUBMISSION_PROCESSED, sub);


        Thread.sleep(500);
        System.out.println("BioSamples messages: " + messagesToBioSamples);
        System.out.println("ENA messages: " + messagesToEna);
        System.out.println("AE messages: " + messagesToAe);

    }




    @RabbitListener(queues = Queues.ENA_AGENT)
    public void handleEna(Submission submission) {
        synchronized (this) {
            this.messagesToEna++;
            System.out.println("ENA!");
        }
    }

    @RabbitListener(queues = Queues.BIOSAMPLES_AGENT)
    public void handleSamples(Submission submission) {
        synchronized (this) {
            this.messagesToBioSamples++;
            System.out.println("BioSamples!");
        }
    }

    @RabbitListener(queues = Queues.AE_AGENT)
    public void handleAe(Submission submission) {
        synchronized (this) {
            this.messagesToAe++;
            System.out.println("AE!");
        }
    }


}
