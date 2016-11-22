package com.bitsinharmony.recognito.speakerfinding;

import com.bitsinharmony.recognito.Recognito;
import com.bitsinharmony.recognito.VoicePrint;
import com.bitsinharmony.recognito.distances.DistanceCalculator;
import com.bitsinharmony.recognito.distances.EuclideanDistanceCalculator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AbsoluteEuclideanDistBelowThresholdIsAMatch implements SpeakerFinderAlgorithm {

    private final Recognito<String> recognito;
    private final DistanceCalculator distanceCalculator;

    private double threshold;

    public AbsoluteEuclideanDistBelowThresholdIsAMatch(float sampleRate) {
        recognito = new Recognito<String>(sampleRate);
        distanceCalculator = new EuclideanDistanceCalculator();
    }

    @Override
    public void initialize(String[] args) {
        threshold = Float.parseFloat(args[0]);
        System.out.println("Threshold: " + threshold);
    }

    @Override
    public int noParams() {
        return 1;
    }

    @Override
    public String getParamsListForUsage() {
        return "<threshold>";
    }

    @Override
    public List<File> findAudioFilesContainingSpeaker(File speakerAudioFile, File learningAudioFilesFolder, File toBeScreenedForAudioFilesWithSpeakerFolder) throws Exception {
        // We do not care about the learning material - no usage of Universal Model

        VoicePrint speakerVoicePrint = recognito.createVoicePrint("favoriteSpeaker", speakerAudioFile);
        List<File> result = new ArrayList<File>();
        for (File f : toBeScreenedForAudioFilesWithSpeakerFolder.listFiles()) {
            VoicePrint fVoicePrint = recognito.createVoicePrint(UUID.randomUUID().toString(), f);
            if (fVoicePrint.getDistance(distanceCalculator, speakerVoicePrint) < threshold) {
                result.add(f);
            }
        }

        return result;
    }

}
