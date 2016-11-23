package com.bitsinharmony.recognito.speakerfinding;

import com.bitsinharmony.recognito.enhancements.Normalizer;
import com.bitsinharmony.recognito.features.FeaturesExtractor;
import com.bitsinharmony.recognito.features.LpcFeaturesExtractor;
import com.bitsinharmony.recognito.vad.AutocorrellatedVoiceActivityDetector;

public class PreprocessorAndFeatureExtractor {

    private final AutocorrellatedVoiceActivityDetector voiceDetector;
    private final Normalizer normalizer;
    private final FeaturesExtractor<double[]> lpcExtractor;

    private final float sampleRate;
    private final String[] preProcessingKeys;
    private final String featureExtractorKey;

    public PreprocessorAndFeatureExtractor(String[] preProcessingKeys, String featureExtractorKey, float sampleRate) {
        for (String preProcessingKey : preProcessingKeys) {
            if (
                !"removesilencenotworkwithresult".equals(preProcessingKey) &&
                !"removesilence".equals(preProcessingKey) &&
                !"normalize".equals(preProcessingKey)) {
                throw new RuntimeException("Do not understand pre-processing key " + preProcessingKey);
            }
        }

        if (!"lpc".equals(featureExtractorKey)) {
            throw new RuntimeException("Do not understand feature-extraction key " + featureExtractorKey);
        }

        voiceDetector = new AutocorrellatedVoiceActivityDetector();
        normalizer = new Normalizer();
        lpcExtractor = new LpcFeaturesExtractor(sampleRate, 20);

        this.sampleRate = sampleRate;
        this.preProcessingKeys = preProcessingKeys;
        this.featureExtractorKey = featureExtractorKey;
    }

    public double[] preProcessAndextractFeatures(double[] voiceSample) {
        for (String preProcessingKey : preProcessingKeys) {
            if ("removesilencenotworkwithresult".equals(preProcessingKey)) {
                voiceDetector.removeSilence(voiceSample, sampleRate);
            } else if ("removesilence".equals(preProcessingKey)) {
                voiceSample = voiceDetector.removeSilence(voiceSample, sampleRate);
            } else if ("normalize".equals(preProcessingKey)) {
                normalizer.normalize(voiceSample, sampleRate);
            }
        }

        if ("lpc".equals(featureExtractorKey)) {
            return lpcExtractor.extractFeatures(voiceSample);
        } else {
            return null;
        }
    }

}
