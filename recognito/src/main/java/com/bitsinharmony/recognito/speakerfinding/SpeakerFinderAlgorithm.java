package com.bitsinharmony.recognito.speakerfinding;

import java.io.File;
import java.util.List;

public interface SpeakerFinderAlgorithm {

    void initialize(String[] args);

    int noParams();

    String getParamsListForUsage();

    List<File> findAudioFilesContainingSpeaker(File speakerAudioFile, File learningAudioFilesFolder, File toBeScreenedForAudioFilesWithSpeakerFolder) throws Exception;

}
