package com.bitsinharmony.recognito.speakerfinding;

import com.bitsinharmony.recognito.Recognito;
import com.bitsinharmony.recognito.VoicePrint;
import com.bitsinharmony.recognito.distances.DistanceCalculator;
import com.bitsinharmony.recognito.distances.EuclideanDistanceCalculator;
import com.bitsinharmony.recognito.utils.AudioConverter;
import com.bitsinharmony.recognito.vad.AutocorrellatedVoiceActivityDetector;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AbsoluteEuclideanDistBelowThresholdForPtcOfWordsIsAMatch implements SpeakerFinderAlgorithm {

    private final float sampleRate;
    private final Recognito<String> recognito;
    private final DistanceCalculator distanceCalculator;
    private final AutocorrellatedVoiceActivityDetector voiceDetector;

    private double distanceThreshold;
    private double wordsPctThreshold;

    public AbsoluteEuclideanDistBelowThresholdForPtcOfWordsIsAMatch(float sampleRate) {
        this.sampleRate = sampleRate;
        recognito = new Recognito<String>(sampleRate);
        distanceCalculator = new EuclideanDistanceCalculator();
        voiceDetector = new AutocorrellatedVoiceActivityDetector();
    }

    @Override
    public void initialize(String[] args) {
        distanceThreshold = Double.parseDouble(args[0]);
        System.out.println("Distance threshold: " + distanceThreshold);
        wordsPctThreshold = Double.parseDouble(args[1]);
        System.out.println("Word percent distanceThreshold: " + wordsPctThreshold);
    }

    @Override
    public int noParams() {
        return 2;
    }

    @Override
    public String getParamsListForUsage() {
        return "<distance-threshold> <word-pct-threshold>";
    }

    @Override
    public List<Match> findAudioFilesContainingSpeaker(File speakerAudioFile, File learningAudioFilesFolder, File toBeScreenedForAudioFilesWithSpeakerFolder) throws Exception {
        // We do not care about the learning material - no usage of Universal Model

        VoicePrint speakerVoicePrint = recognito.constructVoicePrint(speakerAudioFile);
        List<Match> result = new ArrayList<Match>();
        for (File f : toBeScreenedForAudioFilesWithSpeakerFolder.listFiles()) {
            double[][] words = voiceDetector.splitBySilence(AudioConverter.convertFileToDoubleArray(f, sampleRate), sampleRate);
            int wordsWithinThreshold = 0;
            for (int i = 0; i < words.length; i++) {
                VoicePrint wordVoicePrint = recognito.constructVoicePrint(words[i]);
                double wordDistance = wordVoicePrint.getDistance(distanceCalculator, speakerVoicePrint);
                if (wordDistance < distanceThreshold) {
                    wordsWithinThreshold++;
                }
            }
            if ((100.0 * (wordsWithinThreshold / words.length)) > wordsPctThreshold) {
                VoicePrint fVoicePrint = recognito.constructVoicePrint(f);
                double fDistance = fVoicePrint.getDistance(distanceCalculator, speakerVoicePrint);
                if (fDistance < distanceThreshold) {
                    result.add(new Match(f, fDistance));
                }
            }
        }

        return result;
    }

}
