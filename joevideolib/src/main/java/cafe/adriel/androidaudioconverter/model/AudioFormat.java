package cafe.adriel.androidaudioconverter.model;

import java.util.Locale;

public enum AudioFormat {
    AAC,
    MP3,
    M4A,
    WMA,
    WAV,
    FLAC;

    public String getFormat() {
        return name().toLowerCase(Locale.US);
    }
}