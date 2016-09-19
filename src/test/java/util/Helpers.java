package util;

import uk.ac.ebi.subs.data.component.Domain;
import uk.ac.ebi.subs.data.submittable.Sample;
import uk.ac.ebi.subs.data.submittable.Submission;

import java.util.ArrayList;
import java.util.List;

public class Helpers {

    public static List<Sample> generateTestSamples() {
        List<Sample> samples = new ArrayList<>();
        Sample sample2 = new Sample();
        sample2.setAccession("S2");
        sample2.setDescription("Test sample 2.");
        samples.add(sample2);

        Sample sample3 = new Sample();
        sample3.setAccession("S3");
        samples.add(sample3);
        sample3.setDescription("Test sample 3.");

        return samples;
    }

    public static Submission generateTestSubmission() {
        Submission sub = new Submission();
        Domain d = new Domain();
        d.setName("subs-test");
        sub.setDomain(d);
        sub.setSamples(generateTestSamples());
        return sub;
    }
}
