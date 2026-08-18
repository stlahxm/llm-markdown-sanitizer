package io.github.stlahxm.markdownsanitizer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Structural spacing fixes: a missing space after {@code #} in an ATX
 * heading, and a missing blank line before a list/heading that immediately
 * follows a paragraph -- both cause CommonMark-strict renderers to treat
 * the line as plain paragraph text instead of the structural element it's
 * meant to be. Reported as one of the most common causes of "broken" LLM
 * markdown output in community troubleshooting threads (missing blank
 * line) and general markdown guides (missing heading space).
 */
final class SpacingFixer {

    // Only triggers when a letter (Latin or Korean) immediately follows the
    // `#` run -- not a digit or symbol -- to avoid misreading something
    // like "#1 priority" or a hashtag-style "#tag" as an intended heading
    // that's just missing its space.
    private static final Pattern HEADING_NO_SPACE = Pattern.compile("^([ \\t]{0,3}#{1,6})([A-Za-z가-힣].*)$");
    private static final Pattern HEADING = Pattern.compile("^[ \\t]{0,3}#{1,6}(?:\\s|$)");
    private static final Pattern LIST_START = Pattern.compile("^[ \\t]*(?:[-*+]|\\d+[.)])\\s+");

    private SpacingFixer() {
    }

    /** {@code #Heading} -&gt; {@code # Heading}. */
    static String fixHeadingMissingSpace(String line) {
        Matcher matcher = HEADING_NO_SPACE.matcher(line);
        if (!matcher.matches()) {
            return line;
        }
        return matcher.group(1) + " " + matcher.group(2);
    }

    static boolean isHeading(String line) {
        return HEADING.matcher(line).find();
    }

    static boolean isListStart(String line) {
        return LIST_START.matcher(line).find();
    }

    /**
     * A list or heading immediately following a non-blank paragraph line
     * (one that isn't itself a list item, a heading, or a table row) needs
     * a blank line inserted before it -- otherwise CommonMark-strict
     * renderers treat it as a continuation of that paragraph rather than a
     * new structural element, and it renders as plain text.
     */
    static boolean needsBlankLineBefore(String line, String previousLine) {
        if (!(isHeading(line) || isListStart(line))) {
            return false;
        }
        if (previousLine == null || previousLine.strip().isEmpty()) {
            return false;
        }
        if (isHeading(previousLine) || isListStart(previousLine)) {
            return false;
        }
        return !previousLine.stripLeading().startsWith("|");
    }
}
