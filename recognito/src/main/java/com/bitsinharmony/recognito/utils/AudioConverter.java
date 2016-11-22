package com.bitsinharmony.recognito.utils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;

public class AudioConverter {

    /**
     * Converts the given audio file to an array of doubles with values between -1.0 and 1.0
     * @param voiceSampleFile the file to convert
     * @param sampleRate The sample rate of the audio in the file
     * @return an array of doubles
     * @throws UnsupportedAudioFileException when the JVM does not support the file format
     * @throws IOException when an I/O exception occurs
     */
    public static double[] convertFileToDoubleArray(File voiceSampleFile, float sampleRate)
            throws UnsupportedAudioFileException, IOException {
        AudioInputStream sample = AudioSystem.getAudioInputStream(voiceSampleFile);
        AudioFormat format = sample.getFormat();
        float diff = Math.abs(format.getSampleRate() - sampleRate);
        if(diff > 5 * Math.ulp(0.0f)) {
            throw new IllegalArgumentException("The sample rate for this file is different than Recognito's " +
                    "defined sample rate : [" + format.getSampleRate() + "]");
        }
        return FileHelper.readAudioInputStream(sample);
    }

}
