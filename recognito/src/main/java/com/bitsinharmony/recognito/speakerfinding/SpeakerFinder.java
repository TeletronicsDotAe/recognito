package com.bitsinharmony.recognito.speakerfinding;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class SpeakerFinder {

    private static SpeakerFinderAlgorithm speakerFinderAlgorithm;

    public static void main(String[] args) throws Exception {
        speakerFinderAlgorithm = instantiateSpeakerFinderAlgorithm();

        if (args.length != (3 + speakerFinderAlgorithm.noParams())) printUsageAndExit();
        File speakerAudioFile = new File(args[0]);
        File learningAudioFilesFolder = new File(args[1]);
        File toBeScreenedForAudioFilesWithSpeakerFolder = new File(args[2]);
        if (!speakerAudioFile.exists() || !speakerAudioFile.isFile()) printUsageAndExit();
        if (!learningAudioFilesFolder.exists() || !learningAudioFilesFolder.isDirectory()) printUsageAndExit();
        if (!toBeScreenedForAudioFilesWithSpeakerFolder.exists() || !toBeScreenedForAudioFilesWithSpeakerFolder.isDirectory()) printUsageAndExit();

        speakerFinderAlgorithm.initialize(Arrays.asList(args).subList(3, args.length).toArray(new String[]{}));

        List<File> probablyContainingSpeaker = speakerFinderAlgorithm.findAudioFilesContainingSpeaker(speakerAudioFile, learningAudioFilesFolder, toBeScreenedForAudioFilesWithSpeakerFolder);
        for (File ct : probablyContainingSpeaker) {
            if (!ct.isFile() || !ct.getAbsolutePath().startsWith(toBeScreenedForAudioFilesWithSpeakerFolder.getAbsolutePath())) {
                System.err.println("Wrong implementation of " + SpeakerFinderAlgorithm.class.getSimpleName());
                System.exit(-1);
            }
        }
        System.out.println("Pointed out the following to contain speaker");
        for (File ct : probablyContainingSpeaker) {
            System.out.println(ct.getAbsolutePath());
        }
    }

    static SpeakerFinderAlgorithm instantiateSpeakerFinderAlgorithm() {
        // TODO change here to use a particular speaker-finder-algorithm
        return new AbsoluteEuclideanDistBelowThresholdIsAMatch(16000.0f);
    }

    static void printUsageAndExit() {
        System.err.println(SpeakerFinder.class.getSimpleName() + " <audio-files-containing-voice-of-a-speaker> <folder-containing-learning-audio-files> <folder-containing-audio-files-to-be-screened-for-speaker> " + speakerFinderAlgorithm.getParamsListForUsage());
        System.exit(-1);
    }

}
