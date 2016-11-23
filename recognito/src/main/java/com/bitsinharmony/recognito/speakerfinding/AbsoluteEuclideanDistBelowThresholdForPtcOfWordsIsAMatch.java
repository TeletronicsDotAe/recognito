package com.bitsinharmony.recognito.speakerfinding;

import com.bitsinharmony.recognito.VoicePrint;
import com.bitsinharmony.recognito.distances.DistanceCalculator;
import com.bitsinharmony.recognito.distances.EuclideanDistanceCalculator;
import com.bitsinharmony.recognito.utils.AudioConverter;
import com.bitsinharmony.recognito.vad.AutocorrellatedVoiceActivityDetector;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AbsoluteEuclideanDistBelowThresholdForPtcOfWordsIsAMatch extends SpeakerFinderAlgorithm {

    private final DistanceCalculator distanceCalculator;
    private final AutocorrellatedVoiceActivityDetector voiceDetector;

    private double distanceThreshold;
    private double wordsPctThreshold;

    public AbsoluteEuclideanDistBelowThresholdForPtcOfWordsIsAMatch(PreprocessorAndFeatureExtractor preprocessorAndFeatureExtractor, float sampleRate) {
        super(preprocessorAndFeatureExtractor, sampleRate);
        distanceCalculator = new EuclideanDistanceCalculator();
        voiceDetector = new AutocorrellatedVoiceActivityDetector();
    }

    @Override
    public void initialize(String[] args) {
        distanceThreshold = Double.parseDouble(args[0]);
        System.out.println("Word distance threshold: " + distanceThreshold);
        wordsPctThreshold = Double.parseDouble(args[1]);
        System.out.println("Words within distance percentage threshold: " + wordsPctThreshold);
    }

    @Override
    public int noParams() {
        return 2;
    }

    @Override
    public String getParamsListForUsage() {
        return "<word-distance-threshold> <words-within-distance-pct-threshold>";
    }

    @Override
    public List<Match> findAudioFilesContainingSpeaker(VoicePrint speakerVoicePrint, File learningAudioFilesFolder, File toBeScreenedForAudioFilesWithSpeakerFolder) throws Exception {
        // We do not care about the learning material - no usage of Universal Model

        List<Match> result = new ArrayList<Match>();
        for (File f : toBeScreenedForAudioFilesWithSpeakerFolder.listFiles()) {
            double[][] words = voiceDetector.splitBySilence(AudioConverter.convertFileToDoubleArray(f, sampleRate), sampleRate);
            int wordsWithinThreshold = 0;
            for (int i = 0; i < words.length; i++) {
                double[] features = preprocessorAndFeatureExtractor.preProcessAndextractFeatures(words[i]);
                VoicePrint wordVoicePrint = new VoicePrint(features);
                double wordDistance = wordVoicePrint.getDistance(distanceCalculator, speakerVoicePrint);
                if (wordDistance < distanceThreshold) {
                    wordsWithinThreshold++;
                }
            }
            MyMatch myMatch = new MyMatch(f, words.length, wordsWithinThreshold);
            // System.out.println(f.getAbsolutePath() + " " + words.length + " " + wordsWithinThreshold + " " + myMatch.pctOfWordsWithinThreshold());
            if (myMatch.pctOfWordsWithinThreshold() > wordsPctThreshold) {
                result.add(myMatch);
            }
        }

        return result;
    }

    public static class MyMatch extends Match {

        final int totalWords;
        final int wordsWithinThreshold;

        public MyMatch(File audioFile, int totalWords, int wordsWithinThreshold) {
            super(audioFile);
            this.totalWords = totalWords;
            this.wordsWithinThreshold = wordsWithinThreshold;
        }

        @Override
        public int compareTo(Match m) {
            if (m != null && !(m instanceof MyMatch)) throw new RuntimeException("Cannot compare " + this.getClass().getSimpleName() + " with " + m.getClass().getSimpleName());
            return Double.compare(this.pctOfWordsWithinThreshold(), ((MyMatch)m).pctOfWordsWithinThreshold());
        }

        @Override
        public String logicalDistanceDescription() {
            return "" + wordsWithinThreshold + "/" + totalWords + " (" + pctOfWordsWithinThreshold() + "%) words with distance below threshold";
        }

        public double pctOfWordsWithinThreshold() {
            return (totalWords != 0)?(100.0 * ((double)wordsWithinThreshold / totalWords)):0.0;
        }
    }


}
