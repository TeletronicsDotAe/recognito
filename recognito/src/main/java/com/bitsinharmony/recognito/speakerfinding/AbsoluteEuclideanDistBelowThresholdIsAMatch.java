package com.bitsinharmony.recognito.speakerfinding;

import com.bitsinharmony.recognito.Recognito;
import com.bitsinharmony.recognito.VoicePrint;
import com.bitsinharmony.recognito.distances.DistanceCalculator;
import com.bitsinharmony.recognito.distances.EuclideanDistanceCalculator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AbsoluteEuclideanDistBelowThresholdIsAMatch implements SpeakerFinderAlgorithm {

    private final Recognito<String> recognito;
    private final DistanceCalculator distanceCalculator;

    private double distanceThreshold;

    public AbsoluteEuclideanDistBelowThresholdIsAMatch(float sampleRate) {
        recognito = new Recognito<String>(sampleRate);
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
    public List<Match> findAudioFilesContainingSpeaker(File speakerAudioFile, File learningAudioFilesFolder, File toBeScreenedForAudioFilesWithSpeakerFolder) throws Exception {
        // We do not care about the learning material - no usage of Universal Model

        VoicePrint speakerVoicePrint = recognito.constructVoicePrint(speakerAudioFile);
        List<Match> result = new ArrayList<Match>();
        for (File f : toBeScreenedForAudioFilesWithSpeakerFolder.listFiles()) {
            VoicePrint fVoicePrint = recognito.constructVoicePrint(f);
            double fDistance = fVoicePrint.getDistance(distanceCalculator, speakerVoicePrint);
            if (fDistance < distanceThreshold) {
                result.add(new Match(f, fDistance));
            }
        }

        return result;
    }

}
