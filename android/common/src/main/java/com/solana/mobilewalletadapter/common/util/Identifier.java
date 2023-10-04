package com.solana.mobilewalletadapter.common.util;

import androidx.annotation.NonNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Identifier {

    // matches "{namespace}:{reference}", no whitespace
    private static final Pattern namespacedIdentifierPattern = Pattern.compile("^\\S+:\\S+$");

    public static boolean isValidIdentifier(@NonNull String input) {
        Matcher matcher = namespacedIdentifierPattern.matcher(input);
        return matcher.find();
    }

    private Identifier() {}
}
