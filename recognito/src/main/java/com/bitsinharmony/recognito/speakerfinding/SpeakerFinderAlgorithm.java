package com.bitsinharmony.recognito.speakerfinding;

import java.io.File;
import java.util.List;

public interface SpeakerFinderAlgorithm {

    class Match {
        final File audioFile;
        final double distance;

        public Match(File audioFile, double distance) {
            this.audioFile = audioFile;
            this.distance = distance;
        }

    }

    void initialize(String[] args);

    int noParams();

    String getParamsListForUsage();

    List<Match> findAudioFilesContainingSpeaker(File speakerAudioFile, File learningAudioFilesFolder, File toBeScreenedForAudioFilesWithSpeakerFolder) throws Exception;

}
