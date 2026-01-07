package com.brouken.player.utils;

import kotlin.Pair;
import androidx.media3.common.Metadata;
import androidx.media3.extractor.metadata.id3.TextInformationFrame;
import androidx.media3.extractor.metadata.id3.ChapterTocFrame;
import androidx.media3.extractor.metadata.id3.ChapterFrame;
import java.util.ArrayList;
import java.util.List;

public class ChapterScanner {

    public static List<Pair<Double, Double>> scanForIntro(Metadata metadata) {
        List<Pair<Double, Double>> introSegments = new ArrayList<>();
        if (metadata == null) return introSegments;

        for (int i = 0; i < metadata.length(); i++) {
            Metadata.Entry entry = metadata.get(i);
            
            if (entry instanceof ChapterFrame) {
                ChapterFrame chapter = (ChapterFrame) entry;
                if (isIntroChapter(chapter.chapterId)) {
                    // startTime and endTime are in milliseconds
                    double start = chapter.startTimeMs / 1000.0;
                    double end = chapter.endTimeMs / 1000.0;
                    DebugLogger.INSTANCE.log("ChapterScanner", "Found Intro Chapter: " + chapter.chapterId + " (" + start + "-" + end + ")");
                    introSegments.add(new Pair<>(start, end));
                }
            }
        }
        return introSegments;
    }

    private static boolean isIntroChapter(String title) {
        if (title == null) return false;
        String lower = title.toLowerCase();
        return lower.contains("intro") || 
               lower.contains("opening") || 
               lower.equals("op") ||
               lower.contains("prologue") ||
               lower.contains("theme") ||
               lower.contains("credit");
    }
}