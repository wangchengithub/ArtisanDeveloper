package com.artisan.developer.monitor.utils;

public class StringUtils {

    public static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    public static void checkNotEmpty(String s) {
        if (isEmpty(s)) {
            throw new IllegalArgumentException();
        }
    }

}
