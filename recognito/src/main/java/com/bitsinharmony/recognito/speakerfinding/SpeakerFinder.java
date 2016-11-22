package com.bitsinharmony.recognito.speakerfinding;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
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

        List<SpeakerFinderAlgorithm.Match> probablyContainingSpeaker = speakerFinderAlgorithm.findAudioFilesContainingSpeaker(speakerAudioFile, learningAudioFilesFolder, toBeScreenedForAudioFilesWithSpeakerFolder);
        for (SpeakerFinderAlgorithm.Match m : probablyContainingSpeaker) {
            if (!m.audioFile.isFile() || !m.audioFile.getAbsolutePath().startsWith(toBeScreenedForAudioFilesWithSpeakerFolder.getAbsolutePath())) {
                System.err.println("Wrong implementation of " + SpeakerFinderAlgorithm.class.getSimpleName());
                System.exit(-1);
            }
        }

        probablyContainingSpeaker.sort(new Comparator<SpeakerFinderAlgorithm.Match>() {
            @Override
            public int compare(SpeakerFinderAlgorithm.Match o1, SpeakerFinderAlgorithm.Match o2) {
                return Double.compare(o1.distance, o2.distance);
            }
        });
        System.out.println("Pointed out the following to contain speaker (distance)");
        for (SpeakerFinderAlgorithm.Match m : probablyContainingSpeaker) {
            System.out.println(m.audioFile.getAbsolutePath() + " (" + m.distance + ")");
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
