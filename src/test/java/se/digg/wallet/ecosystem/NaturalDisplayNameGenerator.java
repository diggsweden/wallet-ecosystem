// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayNameGenerator;



public class NaturalDisplayNameGenerator implements DisplayNameGenerator {
  private static final Pattern WORD_BOUNDARY_PATTERN =
      Pattern.compile("([A-Z](?=[^A-Z]))|([A-Z]+(?=[A-Z]))|[0-9]+|_");

  @Override
  @NonNull
  public String generateDisplayNameForClass(@NonNull Class<?> testClass) {
    return "The " + toSpaceSeparatedWords(extractSubject(testClass.getSimpleName()));
  }

  @Override
  @NonNull
  public String generateDisplayNameForNestedClass(
      @NonNull List<Class<?>> enclosingInstanceTypes, Class<?> nestedClass) {
    return toSpaceSeparatedWords(nestedClass.getSimpleName());
  }

  @Override
  @NonNull
  public String generateDisplayNameForMethod(@NonNull List<Class<?>> enclosingInstanceTypes,
      @NonNull Class<?> testClass, Method testMethod) {
    return toSpaceSeparatedWords(testMethod.getName());
  }

  private static @NonNull String extractSubject(String text) {
    if (!text.endsWith("Test")) {
      return text;
    } else {
      return text.substring(0, text.lastIndexOf("Test"));
    }
  }

  private static @NonNull String toSpaceSeparatedWords(String text) {
    return WORD_BOUNDARY_PATTERN.matcher(text)
        .replaceAll(match -> {
          if (match.group().equals("_")) {
            return " ";
          } else if (match.group().length() == 1) {
            return " " + match.group().toLowerCase();
          } else {
            return " " + match.group();
          }
        }).stripLeading();
  }
}
