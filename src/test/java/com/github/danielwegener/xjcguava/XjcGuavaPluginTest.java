package com.github.danielwegener.xjcguava;

import com.google.common.base.Charsets;

import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JType;
import com.sun.codemodel.writer.SingleStreamCodeWriter;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class XjcGuavaPluginTest {

  /**
   * Enumeration defining functional behaviour for generating methods.
   */
  private enum TestMode {
    EQUALS {
      @Override
      public void apply(XjcGuavaPlugin plugin, JCodeModel model, JDefinedClass clazz) {
        plugin.generateEqualsMethod(model, clazz);
      }
    },
    HASH_CODE {
      @Override
      public void apply(XjcGuavaPlugin plugin, JCodeModel model, JDefinedClass clazz) {
        plugin.generateHashCodeMethod(model, clazz);
      }
    },
    TO_STRING {
      @Override
      public void apply(XjcGuavaPlugin plugin, JCodeModel model, JDefinedClass clazz) {
        plugin.generateToStringMethod(model, clazz);
      }
    };

    public abstract void apply(XjcGuavaPlugin plugin, JCodeModel model, JDefinedClass clazz);
  }

  private final XjcGuavaPlugin plugin = new XjcGuavaPlugin();
  private final JCodeModel aModel = new JCodeModel();
  private final JPackage aPackage;
  private final JDefinedClass aClass;

  private final JFieldVar aField;
  private final JFieldVar aStaticField;
  private final JDefinedClass aSuperClass;
  private final JFieldVar aSuperClassField;

  private final JDefinedClass anEmptyClass;
  private final JDefinedClass anEmptySuperClass;

  public XjcGuavaPluginTest() throws Exception {
    aPackage = aModel._package("test");
    aClass = aPackage._class("AClass");

    JMethod aSetter = aClass.method(JMod.PUBLIC, aModel.VOID, "setField");

    aField = aClass.field(JMod.PRIVATE, aModel.INT, "field");
    aClass.field(JMod.PRIVATE, aModel.BOOLEAN, "anotherField");
    aStaticField = aClass.field(JMod.STATIC | JMod.PUBLIC, aModel.SHORT, "staticField");
    JMethod aGetter = aClass.method(JMod.PUBLIC, aModel.INT, "getField");
    aGetter.body()._return(aField);
    aSetter.body().assign(aField, aSetter.param(aModel.INT, "field"));

    aSuperClass = aPackage._class("ASuperClass");
    aClass._extends(aSuperClass);
    aSuperClassField = aSuperClass.field(JMod.PRIVATE, aModel.DOUBLE, "superClassField");

    anEmptySuperClass = aPackage._class("AnEmptySuperClass");

    anEmptyClass = aPackage._class("AnEmptyClass");
    anEmptyClass._extends(anEmptySuperClass);
  }

  @Test
  public void testGetInstanceFields() {
    final Collection<JFieldVar> instanceFields = plugin.getInstanceFields(aClass.fields().values());
    assertThat(instanceFields, not(hasItem(aStaticField)));
    assertThat(instanceFields, not(empty()));
  }

  @Test
  public void testGetSuperclassFields() {
    assertThat(plugin.getSuperclassFields(aClass), equalTo(Arrays.asList(aSuperClassField)));
  }

  @Test
  public void testIsStatic() {
    assertThat(plugin.isStatic(aStaticField), equalTo(true));
    assertThat(plugin.isStatic(aField), equalTo(false));
  }

  @Test
  public void testGenerateToString() {
    plugin.generateToStringMethod(aModel, aClass);
    final JMethod generatedMethod = aClass.getMethod("toString", new JType[0]);
    assertThat(generatedMethod, not(nullValue()));
    assertThat(generatedMethod.type().fullName(), equalTo(String.class.getName()));
  }

  @Test
  public void testEquals() throws Exception {
    // true, true
    doTestEquals(aClass, "    @Override\n"
                         + "    public boolean equals(\n"
                         + "        @Nullable\n"
                         + "        Object other) {\n"
                         + "        if (this == other) {\n"
                         + "            return true;\n"
                         + "        }\n"
                         + "        if (other == null) {\n"
                         + "            return false;\n"
                         + "        }\n"
                         + "        if (getClass()!= other.getClass()) {\n"
                         + "            return false;\n"
                         + "        }\n"
                         + "        final AClass o = ((AClass) other);\n"
                         + "        return ((Objects.equals(this.superClassField, o.superClassField)"
                         + "&&Objects.equals(this.field, o.field))&&Objects.equals(this.anotherField, "
                         + "o.anotherField));\n"
                         + "    }");
  }

  @Test
  public void testEquals_emptySuperclass() throws Exception {
    JDefinedClass clazz = aPackage._class("WithEmptySuperClass");
    clazz.field(JMod.PRIVATE, aModel.INT, "field");
    clazz._extends(anEmptySuperClass);

    // true, false
    doTestEquals(clazz, "    @Override\n"
                        + "    public boolean equals(\n"
                        + "        @Nullable\n"
                        + "        Object other) {\n"
                        + "        if (this == other) {\n"
                        + "            return true;\n"
                        + "        }\n"
                        + "        if (other == null) {\n"
                        + "            return false;\n"
                        + "        }\n"
                        + "        if (getClass()!= other.getClass()) {\n"
                        + "            return false;\n"
                        + "        }\n"
                        + "        final WithEmptySuperClass o = ((WithEmptySuperClass) other);\n"
                        + "        return Objects.equals(this.field, o.field);\n"
                        + "    }");
  }

  @Test
  public void testEquals_noSuperClass() throws Exception {
    // true, false
    doTestEquals(aSuperClass, "    @Override\n"
                              + "    public boolean equals(\n"
                              + "        @Nullable\n"
                              + "        Object other) {\n"
                              + "        if (this == other) {\n"
                              + "            return true;\n"
                              + "        }\n"
                              + "        if (other == null) {\n"
                              + "            return false;\n"
                              + "        }\n"
                              + "        if (getClass()!= other.getClass()) {\n"
                              + "            return false;\n"
                              + "        }\n"
                              + "        final ASuperClass o = ((ASuperClass) other);\n"
                              + "        return Objects.equals(this.superClassField, o.superClassField);");
  }

  @Test
  public void testEquals_onlySuperClass() throws Exception {
    JDefinedClass clazz = aPackage._class("OnlySuperClass");
    clazz._extends(aSuperClass);

    // false, true
    doTestEquals(clazz, "    @Override\n"
                        + "    public boolean equals(\n"
                        + "        @Nullable\n"
                        + "        Object other) {\n"
                        + "        if (this == other) {\n"
                        + "            return true;\n"
                        + "        }\n"
                        + "        if (other == null) {\n"
                        + "            return false;\n"
                        + "        }\n"
                        + "        if (getClass()!= other.getClass()) {\n"
                        + "            return false;\n"
                        + "        }\n"
                        + "        final OnlySuperClass o = ((OnlySuperClass) other);\n"
                        + "        return Objects.equals(this.superClassField, o.superClassField);\n"
                        + "    }");
  }

  @Test
  public void testEquals_noFields() throws Exception {
    // false, false
    doTestEquals_noFields(anEmptyClass);
  }

  @Test
  public void testEquals_noFieldsSuperClass() throws Exception {
    // false, false
    doTestEquals_noFields(anEmptySuperClass);
  }

  @Test
  public void testHashCode() throws Exception {
    assertThat(definedClassToString(TestMode.HASH_CODE, aClass)).contains(
        "    @Override\n"
        + "    public int hashCode() {\n"
        + "        return Objects.hash(superClassField, field, anotherField);\n"
        + "    }"
    );
  }

  @Test
  public void testHashCode_noSuperClass() throws Exception {
    assertThat(definedClassToString(TestMode.HASH_CODE, aSuperClass)).contains(
        "    @Override\n"
        + "    public int hashCode() {\n"
        + "        return Objects.hash(superClassField);\n"
        + "    }"
    );
  }

  @Test
  public void testHashCode_onlySuperClass() throws Exception {
    JDefinedClass clazz = aPackage._class("OnlySuperClass");
    clazz._extends(aSuperClass);

    assertThat(definedClassToString(TestMode.HASH_CODE, clazz)).contains(
        "    @Override\n"
        + "    public int hashCode() {\n"
        + "        return Objects.hash(superClassField);\n"
        + "    }"
    );
  }

  @Test
  public void testHashCode_noFields() throws Exception {
    doTestHashCode_noFields(anEmptyClass);
  }

  @Test
  public void testHashCode_noFieldsSuperClass() throws Exception {
    doTestHashCode_noFields(anEmptySuperClass);
  }

  @Test
  public void testGetUsage() throws Exception {
    assertThat(plugin.getUsage()).isEqualTo(
        "  -Xguava\t:  enable generation of guava toString, equals and hashCode methods\n"
        + "    --Xguava:skipToString\t:  dont wrap collection parameters with Collections.unmodifiable...");
  }

  @Test
  public void testParseArgument_invalid() throws Exception {
    try {
      doTestParseArgument(0, 1, "-Xignored:parameter");
      failBecauseExceptionWasNotThrown(IndexOutOfBoundsException.class);
    } catch (IndexOutOfBoundsException e) {
      assertThat(e).hasMessage("index (1) must be less than size (1)");
    }
  }

  @Test
  public void testParseArgument_noSkipToString() throws Exception {
    doTestParseArgument(0, 0, "-Xignored:parameter");
    doTestParseArgument(0, 0, "-Xignored:parameter", "-Xguava:skipToString");
    doTestParseArgument(0, 1, "-Xguava:skipToString", "-Xignored:parameter");
  }

  @Test
  public void testParseArgument_skipToString() throws Exception {
    doTestParseArgument(1, 0, "-Xguava:skipToString");
    doTestParseArgument(1, 1, "-Xignored:parameter", "-Xguava:skipToString");
    doTestParseArgument(1, 0, "-Xguava:skipToString", "-Xignored:parameter");
  }

  @Ignore("to be implemented")
  @Test
  public void testRun() throws Exception {
    // todo(somebody): implement test of run method to provide 100% code coverage
    throw new UnsupportedOperationException("to be implemented");
  }

  @Test
  public void testToString() throws Exception {
    assertThat(definedClassToString(TestMode.TO_STRING, aClass))
        .contains("    @Override\n"
                  + "    public String toString() {\n"
                  + "        return MoreObjects.toStringHelper(this).add(\"superClassField\", superClassField).add"
                  + "(\"field\", field).add(\"anotherField\", anotherField).toString();\n"
                  + "    }");
  }

  @Test
  public void testToString_noSuperclass() throws Exception {
    JDefinedClass clazz = aPackage._class("OnlySuperClass");
    clazz._extends(aSuperClass);

    assertThat(definedClassToString(TestMode.TO_STRING, clazz))
        .contains("    @Override\n"
                  + "    public String toString() {\n"
                  + "        return MoreObjects.toStringHelper(this).add(\"superClassField\", superClassField)"
                  + ".toString();\n"
                  + "    }");
  }

  @Test
  public void testToString_noFields() throws Exception {
    assertThat(definedClassToString(TestMode.TO_STRING, anEmptyClass))
        .contains("    @Override\n"
                  + "    public String toString() {\n"
                  + "        return MoreObjects.toStringHelper(this).toString();\n"
                  + "    }");
  }

  @Test
  public void testToString_noFieldsSuperClass() throws Exception {
    assertThat(definedClassToString(TestMode.TO_STRING, anEmptySuperClass))
        .contains("    @Override\n"
                  + "    public String toString() {\n"
                  + "        return MoreObjects.toStringHelper(this).toString();\n"
                  + "    }");
  }

  @Test
  public void testGetOptionName() throws Exception {
    assertThat(plugin.getOptionName()).isEqualTo("Xguava");
  }

  private String definedClassToString(TestMode testMode, JDefinedClass clazz) throws IOException {
    testMode.apply(plugin, aModel, clazz);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    aModel.build(new SingleStreamCodeWriter(baos));
    return new String(baos.toByteArray(), Charsets.UTF_8);
  }

  private void doTestEquals(JDefinedClass clazz, String expectedEqualsMethod) throws IOException {
    assertThat(definedClassToString(TestMode.EQUALS, clazz)).contains(expectedEqualsMethod);
  }

  private void doTestEquals_noFields(JDefinedClass clazz) throws IOException {
    assertThat(definedClassToString(TestMode.HASH_CODE, clazz)).doesNotContain("public boolean equals(");
  }

  private void doTestHashCode_noFields(JDefinedClass clazz) throws IOException {
    assertThat(definedClassToString(TestMode.HASH_CODE, clazz)).doesNotContain("public int hashCode() {");
  }

  private void doTestParseArgument(int expected, int pos, String... arguments) throws Exception {
    assertEquals(expected, plugin.parseArgument(null, arguments, pos));
    assertEquals(expected == 1, plugin.isSkipToStringEnabled());
  }
}
