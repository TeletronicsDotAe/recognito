package com.bitsinharmony.recognito.vad.manualtest;

import com.bitsinharmony.recognito.utils.AudioConverter;
import com.bitsinharmony.recognito.utils.FileHelper;
import com.bitsinharmony.recognito.vad.AutocorrellatedVoiceActivityDetector;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.File;

public class PlayAudioFileAndThenWordByWord {

    public static void main(String[] args) throws Exception {
        AudioInputStream sample = AudioSystem.getAudioInputStream(new File(args[0]));
        Clip clip = AudioSystem.getClip();
        clip.open(sample);
        clip.start();

        System.out.println("Playing full audio-file");
        Thread.sleep(clip.getMicrosecondLength()/1000 + 1000);
        clip.close();

        float sampleRate = 16000.0f;
        AutocorrellatedVoiceActivityDetector voiceDetector = new AutocorrellatedVoiceActivityDetector();
        double[] sentence = AudioConverter.convertFileToDoubleArray(new File(args[0]), sampleRate);
        double[][] words = voiceDetector.splitBySilence(sentence, sampleRate);

        for (double[] word : words) {
            clip = AudioSystem.getClip();
            clip.open(FileHelper.writeAudioInputStream(word, sample.getFormat()));
            clip.start();
            System.out.println("Playing word");
            Thread.sleep(clip.getMicrosecondLength()/1000 + 1000);
            clip.close();
        }
    }

}
