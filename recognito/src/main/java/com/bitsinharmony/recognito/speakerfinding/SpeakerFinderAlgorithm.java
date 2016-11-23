package com.bitsinharmony.recognito.speakerfinding;

import com.bitsinharmony.recognito.VoicePrint;
import com.bitsinharmony.recognito.utils.AudioConverter;

import java.io.File;
import java.util.List;

public abstract class SpeakerFinderAlgorithm {

    abstract static class Match implements Comparable<Match> {
        final File audioFile;

        public Match(File audioFile) {
            this.audioFile = audioFile;
        }

        public abstract String logicalDistanceDescription();

    }

    protected final PreprocessorAndFeatureExtractor preprocessorAndFeatureExtractor;
    protected final float sampleRate;

    public SpeakerFinderAlgorithm(PreprocessorAndFeatureExtractor preprocessorAndFeatureExtractor, float sampleRate) {
        this.preprocessorAndFeatureExtractor = preprocessorAndFeatureExtractor;
        this.sampleRate = sampleRate;
    }

    abstract void initialize(String[] args);

    abstract int noParams();

    abstract String getParamsListForUsage();

    public List<? extends Match> findAudioFilesContainingSpeaker(File speakerAudioFilesFolder, File learningAudioFilesFolder, File toBeScreenedForAudioFilesWithSpeakerFolder) throws Exception {
        VoicePrint speakerVoicePrint = null;
        for (File speakerAudioFile : speakerAudioFilesFolder.listFiles()) {
            double[] audio = AudioConverter.convertFileToDoubleArray(speakerAudioFile, sampleRate);
            double[] features = preprocessorAndFeatureExtractor.preProcessAndextractFeatures(audio);
            if (speakerVoicePrint == null) speakerVoicePrint = new VoicePrint(features);
            else speakerVoicePrint.merge(features);
        }

        return findAudioFilesContainingSpeaker(speakerVoicePrint, learningAudioFilesFolder, toBeScreenedForAudioFilesWithSpeakerFolder);
    }

    abstract public List<? extends Match> findAudioFilesContainingSpeaker(VoicePrint speakerVoicePrint, File learningAudioFilesFolder, File toBeScreenedForAudioFilesWithSpeakerFolder) throws Exception;

}
