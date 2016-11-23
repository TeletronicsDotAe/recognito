package com.bitsinharmony.recognito.speakerfinding;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SpeakerFinder {

    private final static float sampleRate = 16000.0f;

    private static PreprocessorAndFeatureExtractor preprocessorAndFeatureExtractor;
    private static SpeakerFinderAlgorithm speakerFinderAlgorithm;

    public static void main(String[] args) throws Exception {
        if (args.length < 6) printUsageAndExit(null);
        try {
            preprocessorAndFeatureExtractor = new PreprocessorAndFeatureExtractor(args[0].split(","), args[1], sampleRate);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            printUsageAndExit(null);
        }

        speakerFinderAlgorithm = instantiateSpeakerFinderAlgorithm(args[2]);

        if (args.length != (6 + speakerFinderAlgorithm.noParams())) printUsageAndExit(args[2]);
        File speakerAudioFilesFolder = new File(args[3]);
        File learningAudioFilesFolder = new File(args[4]);
        File toBeScreenedForAudioFilesWithSpeakerFolder = new File(args[5]);
        if (!speakerAudioFilesFolder.exists() || !speakerAudioFilesFolder.isDirectory()) printUsageAndExit(args[2]);
        if (!learningAudioFilesFolder.exists() || !learningAudioFilesFolder.isDirectory()) printUsageAndExit(args[2]);
        if (!toBeScreenedForAudioFilesWithSpeakerFolder.exists() || !toBeScreenedForAudioFilesWithSpeakerFolder.isDirectory()) printUsageAndExit(args[2]);

        speakerFinderAlgorithm.initialize(Arrays.asList(args).subList(6, args.length).toArray(new String[]{}));

        List<? extends SpeakerFinderAlgorithm.Match> probablyContainingSpeaker = speakerFinderAlgorithm.findAudioFilesContainingSpeaker(speakerAudioFilesFolder, learningAudioFilesFolder, toBeScreenedForAudioFilesWithSpeakerFolder);
        for (SpeakerFinderAlgorithm.Match m : probablyContainingSpeaker) {
            if (!m.audioFile.isFile() || !m.audioFile.getAbsolutePath().startsWith(toBeScreenedForAudioFilesWithSpeakerFolder.getAbsolutePath())) {
                System.err.println("Wrong implementation of " + SpeakerFinderAlgorithm.class.getSimpleName());
                System.exit(-1);
            }
        }

        Collections.sort(probablyContainingSpeaker, new Comparator<SpeakerFinderAlgorithm.Match>() {
            @Override
            public int compare(SpeakerFinderAlgorithm.Match m1, SpeakerFinderAlgorithm.Match m2) {
                return -m1.compareTo(m2);
            }
        });

        System.out.println("Pointed out the following to contain speaker (logical distance description)");
        for (SpeakerFinderAlgorithm.Match m : probablyContainingSpeaker) {
            System.out.println(m.audioFile.getAbsolutePath() + " (" + m.logicalDistanceDescription() + ")");
        }
    }

    static SpeakerFinderAlgorithm instantiateSpeakerFinderAlgorithm(String finderAlgorithmKey) {
        if ("abs_euclid_dist_below_threshold".equals(finderAlgorithmKey)) {
            return new AbsoluteEuclideanDistBelowThresholdIsAMatch(preprocessorAndFeatureExtractor, sampleRate);
        } else if ("abs_euclid_dist_below_threshold_for_pct_of_words".equals(finderAlgorithmKey)) {
            return new AbsoluteEuclideanDistBelowThresholdForPtcOfWordsIsAMatch(preprocessorAndFeatureExtractor, sampleRate);
        } else {
            printUsageAndExit(null);
        }
        // Will never get to here
        return null;
    }

    static void printUsageAndExit(String finderAlgorithmKey) {
        StringBuilder sb = new StringBuilder();
        sb.append("Usage: " + SpeakerFinder.class.getSimpleName() + " <pre-processing> <feature-extraction> " + ((finderAlgorithmKey != null)?finderAlgorithmKey:"<finder-algorithm>") + " <folder-containing-audio-files-of-the-favorite-speaker> <folder-containing-learning-audio-files> <folder-containing-audio-files-to-be-screened-for-favorite-speaker> " + ((speakerFinderAlgorithm != null)?speakerFinderAlgorithm.getParamsListForUsage():"<finder-algorithm-params>") + "\n");
        sb.append("* <pre-processing> is a comma-separated list of pre-processing-key:\n");
        sb.append("** \"removesilencenotworkwithresult\": To remove silence, but not proceeding with the result of it, but living with the side-effects (what original Recognito does)\n");
        sb.append("** \"removesilence\": To remove silence\n");
        sb.append("** \"normalize\": To normalize\n");
        sb.append("* <feature-extraction> must be \"lpc\" (for now) and calculates \"Linear Predictive Codes\" voice-print\n");
        if (finderAlgorithmKey != null) {
            sb.append("* <finder-algorithm> is one of the values:\n");
            sb.append("** \"abs_euclid_dist_below_threshold\": Finding the matching files in <folder-containing-audio-files-to-be-screened-for-favorite-speaker>, by calculating voice-print for favorite speaker by considering all audio-files in <folder-containing-audio-files-of-the-favorite-speaker>. For each file in <folder-containing-audio-files-to-be-screened-for-favorite-speaker>, calculate the voice-print. Print out all files where the euclidean distance between the files voice-print and the favorite speakers voice-print is below <finder-algorithm-params#1>\n");
            sb.append("** \"abs_euclid_dist_below_threshold_for_pct_of_words\": Finding the matching files in <folder-containing-audio-files-to-be-screened-for-favorite-speaker>, by calculate voice-print for favorite speaker by considering all audio-files in <folder-containing-audio-files-of-the-favorite-speaker>. For each file in <folder-containing-audio-files-to-be-screened-for-favorite-speaker>, split into words and calculate voice-print for each word. Print out all files where <finder-algorithm-params#2>% of the words have an euclidean distance below <finder-algorithm-params#1> to the favorite speakers voice-print\n");
        }
        sb.append("* <folder-containing-audio-files-of-the-favorite-speaker>: Folder containing audio-files where the favorite speaker is known to speak. Used for calculating the voice-print for the favorite speaker\n");
        sb.append("* <folder-containing-learning-audio-files>: Not used\n");
        sb.append("* <folder-containing-audio-files-to-be-screened-for-favorite-speaker>: Folder containing audio-files to be screened for favorite speaker\n");
        sb.append("* <finder-algorithm-params>: Additional arguments to the <finder-algorithm>. See the description for each <finder-algorithm> above\n");
        sb.append("Voice-prints are calculated by: pre-processing | feature-extraction > voice-print");
        System.err.println(sb.toString());
        System.exit(-1);
    }

}
