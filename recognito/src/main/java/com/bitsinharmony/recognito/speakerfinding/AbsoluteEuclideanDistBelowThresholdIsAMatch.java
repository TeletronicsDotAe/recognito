package com.bitsinharmony.recognito.speakerfinding;

import com.bitsinharmony.recognito.VoicePrint;
import com.bitsinharmony.recognito.distances.DistanceCalculator;
import com.bitsinharmony.recognito.distances.EuclideanDistanceCalculator;
import com.bitsinharmony.recognito.utils.AudioConverter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AbsoluteEuclideanDistBelowThresholdIsAMatch extends SpeakerFinderAlgorithm {

    private final DistanceCalculator distanceCalculator;

    private double distanceThreshold;

    public AbsoluteEuclideanDistBelowThresholdIsAMatch(PreprocessorAndFeatureExtractor preprocessorAndFeatureExtractor, float sampleRate) {
        super(preprocessorAndFeatureExtractor, sampleRate);
        distanceCalculator = new EuclideanDistanceCalculator();
    }

    @Override
    public void initialize(String[] args) {
        distanceThreshold = Double.parseDouble(args[0]);
        System.out.println("Distance threshold: " + distanceThreshold);
    }

    @Override
    public int noParams() {
        return 1;
    }

    @Override
    public String getParamsListForUsage() {
        return "<distance-threshold>";
    }

    @Override
    public List<MyMatch> findAudioFilesContainingSpeaker(VoicePrint speakerVoicePrint, File learningAudioFilesFolder, File toBeScreenedForAudioFilesWithSpeakerFolder) throws Exception {
        // We do not care about the learning material - no usage of Universal Model

        List<MyMatch> result = new ArrayList<MyMatch>();
        for (File f : toBeScreenedForAudioFilesWithSpeakerFolder.listFiles()) {
            double[] audio = AudioConverter.convertFileToDoubleArray(f, sampleRate);
            double[] features = preprocessorAndFeatureExtractor.preProcessAndextractFeatures(audio);
            VoicePrint fVoicePrint = new VoicePrint(features);
            double fDistance = fVoicePrint.getDistance(distanceCalculator, speakerVoicePrint);
            if (fDistance < distanceThreshold) {
                result.add(new MyMatch(f, fDistance));
            }
        }

        return result;
    }

    public static class MyMatch extends Match {

        final double distance;

        public MyMatch(File audioFile, double distance) {
            super(audioFile);
            this.distance = distance;
        }

        @Override
        public int compareTo(Match m) {
            if (m != null && !(m instanceof MyMatch)) throw new RuntimeException("Cannot compare " + this.getClass().getSimpleName() + " with " + m.getClass().getSimpleName());
            return Double.compare(((MyMatch)m).distance, this.distance);
        }

        @Override
        public String logicalDistanceDescription() {
            return "distance " + distance;
        }
    }

}
