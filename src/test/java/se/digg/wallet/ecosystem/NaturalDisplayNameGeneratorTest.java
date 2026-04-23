// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NaturalDisplayNameGeneratorTest {

  private static class FooTest {
    void acceptsValidInput() {}

    void rejectsInvalidInput() {}

    void understandsNumbersLikeA5OutOf100() {}

    void understands_snake_case() {}
  }

  private static class BarTest {
  }

  private static class ClassWithMultipleWordsTest {
  }

  private static class ClassWithTestInTheMiddle {
  }

  private static class TestTest {
  }

  private static class Foo {
  }

  private static class FooBar {
  }

  private static class AcronymABCTest {
  }

  private static class AcronymABCWithMoreWordsTest {
  }

  private static class NestedTest {
    private static class NestedAtLevelOne {
      private static class NestedAtLevelTwo {
        void aNestedTestMethod() {}
      }
    }
  }

  private final NaturalDisplayNameGenerator subject = new NaturalDisplayNameGenerator();

  @ParameterizedTest
  @CsvSource({
      "FooTest, The foo",
      "BarTest, The bar",
      "ClassWithMultipleWordsTest, The class with multiple words",
      "ClassWithTestInTheMiddle, The class with test in the middle",
      "TestTest, The test",
      "AcronymABCTest, The acronym ABC",
      "AcronymABCWithMoreWordsTest, The acronym ABC with more words",
      "Foo, The foo",
      "FooBar, The foo bar"
  })
  void producesNameForClassByTokenizingSimpleName(String className, String expectedName)
      throws ClassNotFoundException {
    assertEquals(expectedName, subject.generateDisplayNameForClass(
        Class.forName(NaturalDisplayNameGeneratorTest.class.getName() + "$" + className)));
  }

  @ParameterizedTest
  @CsvSource({
      "FooTest, acceptsValidInput, accepts valid input",
      "FooTest, rejectsInvalidInput, rejects invalid input",
      "FooTest, understandsNumbersLikeA5OutOf100, understands numbers like a 5 out of 100",
      "FooTest, understands_snake_case, understands snake case",
  })
  void producesNameForMethodByTokenizingMethodName(
      String className, String methodName, String expectedName)
      throws NoSuchMethodException, ClassNotFoundException {

    Class<?> theClass =
        Class.forName(NaturalDisplayNameGeneratorTest.class.getName() + "$" + className);

    assertEquals(expectedName,
        subject.generateDisplayNameForMethod(List.of(), theClass,
            theClass.getDeclaredMethod(methodName)));
  }

  @Test
  void producesNameForNestedClassByTokenizingSimpleName() {
    assertEquals("nested at level one",
        subject.generateDisplayNameForNestedClass(List.of(), NestedTest.NestedAtLevelOne.class));
  }
}
