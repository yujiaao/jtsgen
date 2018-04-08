/*
 * Copyright 2016 Dragan Zuvic
 *
 * This file is part of jtsgen.
 *
 * jtsgen is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * jtsgen is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with jtsgen.  If not, see http://www.gnu.org/licenses/
 *
 */

package dz.jtsgen.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dz.jtsgen.processor.helper.CompileHelper;
import dz.jtsgen.processor.helper.ReferenceHelper;
import dz.jtsgen.processor.helper.StringConstForTest;
import org.junit.Ignore;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static com.google.testing.compile.Compiler.javac;
import static dz.jtsgen.processor.helper.OutputHelper.countPatterns;
import static dz.jtsgen.processor.helper.OutputHelper.findSourceLine;
import static dz.jtsgen.processor.helper.StringConstForTest.*;
import static org.junit.jupiter.api.Assertions.*;


class TsGenProcessorTest {

    private final boolean DUMP_FILES = false;

    @Test
    void check_simple_interface_Full_Logging() {
        Compilation c = CompileHelper.compileJtsDev(true, 0, "InterFaceEmpty.java");
        // check that any logging is disabled is disabled
        assertEquals(
                Logger.getLogger(TsGenProcessor.class.getPackage().getName()).getLevel(), Level.FINEST);
    }

    @Test
    void check_simple_interface_No_Logging() {
        JavaFileObject[] files = {JavaFileObjects.forResource("jts/dev/InterFaceTest.java")};
        Compilation c = javac()
                .withProcessors(new TsGenProcessor())
                .withOptions("-Agone=nowhere", "-AjtsgenLogLevel=DUMMY")
                .compile(files);

        // check that any logging is disabled is disabled
        assertEquals(
                Logger.getLogger(TsGenProcessor.class.getPackage().getName()).getLevel(), Level.OFF);
    }

    @Test
    void check_simple_ts_module_error() {
        JavaFileObject[] files = {
                JavaFileObjects.forResource("jts/modules/tsmodule_error/InterFaceTestTsModuleError.java"),
                JavaFileObjects.forResource("jts/modules/tsmodule_error/package-info.java")
        };
        Compilation c = javac()
                .withProcessors(new TsGenProcessor())
                .withOptions("-AjtsgenLogLevel=none")
                .compile(files);

        assertTrue(c.errors().size() > 0);
        boolean v1 = c.errors().stream().anyMatch(x -> x.getMessage(Locale.ENGLISH).contains("param not valid. Expecting a name space"));
        boolean v2 = c.errors().stream().anyMatch(x -> x.getMessage(Locale.ENGLISH).contains("param not valid. Expecting origin and target"));
        boolean v3 = c.errors().stream().anyMatch(x -> x.getMessage(Locale.ENGLISH).contains("param not valid. Expecting a regular Expression."));

        // at least one of the error must exists
        assertTrue(v1 || v2 || v3);
    }

    @Test
    void test_simple_interface_1() throws IOException {
        JavaFileObject[] files = {JavaFileObjects.forResource("jts/dev/InterFaceTest.java")};
        Compilation c = javac()
                .withProcessors(new TsGenProcessor())
                .withOptions("-AjtsgenModuleName=MyModule")
                .compile(files);

        assertEquals(
                0, c.errors().size());

        // check debug is disabled
        assertEquals(
                Logger.getLogger(TsGenProcessor.class.getPackage().getName()).getLevel(), Level.OFF);

        assertTrue(c.generatedFile(StandardLocation.SOURCE_OUTPUT, StringConstForTest.JTSGEN_MYMODULE, PACKAGE_JSON).isPresent());
        assertTrue(c.generatedFile(StandardLocation.SOURCE_OUTPUT, StringConstForTest.JTSGEN_MYMODULE, StringConstForTest.MY_MODULE_D_TS).isPresent());

        JavaFileObject testee2 = c.generatedFile(StandardLocation.SOURCE_OUTPUT, StringConstForTest.JTSGEN_MYMODULE, StringConstForTest.MY_MODULE_D_TS).get();

        ReferenceHelper.assertEquals(

                c.generatedFile(StandardLocation.SOURCE_OUTPUT, StringConstForTest.JTSGEN_MYMODULE, PACKAGE_JSON).get()
                , "simple_interface_1.package.json");

        ReferenceHelper.assertEquals(

                c.generatedFile(StandardLocation.SOURCE_OUTPUT, StringConstForTest.JTSGEN_MYMODULE, StringConstForTest.MY_MODULE_D_TS).get()
                , "simple_interface_1.my-module.d.ts");
    }

    @Test
    void test_simple_interface_no_package() {
        JavaFileObject[] files = {JavaFileObjects.forResource("InterFaceTestNoPackage.java")};
        Compilation c = javac()
                .withProcessors(new TsGenProcessor())
                .compile(files);

        assertEquals(
                0, c.errors().size());

        // using default packages issues a warning
        assertEquals(
                c.diagnostics().asList().stream().filter(x -> x.getKind().equals(Diagnostic.Kind.WARNING)).count(), 1);

        // module name has to be is unknown
        assertTrue(c.generatedFile(StandardLocation.SOURCE_OUTPUT, JTSGEN_UNKNOWN, PACKAGE_JSON).isPresent());
        assertTrue(c.generatedFile(StandardLocation.SOURCE_OUTPUT, JTSGEN_UNKNOWN, "unknown.d.ts").isPresent());
    }

    @Test
    void test_two_simple_interface_with_one_ignored() throws IOException {
        Compilation c = CompileHelper.compileJtsDev(DUMP_FILES, 0, "InterFaceTest.java", "InterFaceTestIgnored.java");
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS, Pattern.compile("^\\s+export\\s+interface\\s+InterFaceTest\\s+\\{")).size(),
                "must have Type InterFaceTest"
        );
        assertEquals(
                0,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS, Pattern.compile("^\\s+export\\s+interface\\s+InterFaceTestIgnored\\s*\\{")).size(),
                "must not have Type InterFaceTestIgnored"
        );
    }

    @Test
    void test_simple_interface_with_inheritance() throws IOException {
        Compilation c = CompileHelper.compileJtsDev(DUMP_FILES, 0, "InterFaceInheritance1Test.java");
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS, Pattern.compile("^\\s+export\\s+interface\\s+InterFaceInheritance1Test\\s+extends\\s+InterFaceInheritance1TestParent\\s+\\{")).size(),
                "must have Type InterFaceInheritance1Test extends InterFaceInheritance1TestParent"
        );
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS, Pattern.compile("^\\s+export\\s+interface\\s+InterFaceInheritance1TestParent\\s+\\{")).size(),
                "must have Type InterFaceInheritance1TestParent"
        );
    }


    @Test
    @DisplayName("Generate type parameters in return type")
    void test_simple_interface_with_generics() throws IOException {
        Compilation c = CompileHelper.compileJtsDev(DUMP_FILES, 0, "InterFaceTestGenericsConsumer.java");
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS, Pattern.compile("^\\s+export\\s+interface\\s+Consumer<T>")).size(),
                "Consumer<T> must be defined"
        );
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS, Pattern.compile("^\\s+export\\s+interface\\s+InterFaceTestGenericsConsumer\\s*\\{")).size(),
                "InterFaceTestGenericsConsumer must be defined"
        );
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS, Pattern.compile("^\\s+consumerOfString:\\s*Consumer<string>\\s*;")).size(),
                "Variable consumerOfString must be of type Consumer<string>"
        );
    }

    @Test
    @DisplayName("Generate type parameters for generic classes")
    void test_own_interface_with_generics() throws IOException {
        Compilation c = CompileHelper.compileJtsDev(DUMP_FILES, 3, "InterFaceTestGenerics.java");
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS, Pattern.compile("^\\s+export\\s+interface\\s+MyPair<U,\\s*V>")).size(),
                "Pair class must have type parameters"
        );
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS, Pattern.compile("^\\s+u:\\s*U\\s*;")).size(),
                "Variable u must be of generic type U"
        );
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS, Pattern.compile("^\\s+v:\\s*V\\s*;")).size(),
                "Variable v must be of generic type V"
        );

        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS, Pattern.compile("^\\s+export\\s+interface\\s+InterFaceTestGenerics<T>")).size(),
                "InterFaceTestGenerics must have type parameter T"
        );

        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS, Pattern.compile("^\\s+myGeneric:\\s*T\\s*;")).size(),
                "Variable myGeneric must be of generic type T"
        );

        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS, Pattern.compile("^\\s+myPair:\\s*MyPair<string,string>\\s*;")).size(),
                "Variable myPair must be parameterized with <String,String>"
        );
    }

    @Test
    @DisplayName("Generate type parameters for generic classes with one bound")
    void test_simple_interface_with_upperBound() throws IOException {
        Compilation c = CompileHelper.compileJtsDev(DUMP_FILES, 1, "InterFaceTestGenericsOneBound.java");
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS, Pattern.compile("^\\s+export\\s+interface\\s+Upper\\s+")).size(),
                "Upper Type must be defined"
        );

        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+export\\s+interface\\s+InterFaceTestGenericsOneBound\\s*<T\\s+extends\\s+Upper>\\s*\\{")).size(),
                "InterFaceTestGenericsOneBound must be generic with upper bound to type Upper"
        );

        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS, Pattern.compile("^\\s+upperBound:\\s*T\\s*;")).size(),
                "Variable upperBound must be of generic type T"
        );

    }

    @Test
    @DisplayName("Generate type parameters for generic classes with two bound")
    void test_simple_interface_with_multiple_upperBound() throws IOException {
        Compilation c = CompileHelper.compileJtsDev(DUMP_FILES, 1, "InterFaceTestGenericsMultipleBound.java");
        assertEquals(
                2,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS, Pattern.compile("^\\s+export\\s+interface\\s+Upper(One|Two)\\s+")).size(),
                "Upper Type must be defined"
        );

        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+export\\s+interface\\s+InterFaceTestGenericsMultipleBound\\s*<T\\s+extends\\s+UpperOne\\s+&\\s+UpperTwo\\s*>\\s*\\{")).size(),
                "InterFaceTestGenericsOneBound must be generic with upper bound to type Upper"
        );

        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS, Pattern.compile("^\\s+upperBound:\\s*T\\s*;")).size(),
                "Variable upperBound must be of generic type T"
        );

    }



    @Test
    void test_multi_interface_with_inheritance_and_namespaces() throws IOException {
        final String folderName = "inherit1_test";
        final String tdsFilename = "inherit1_test.d.ts";
        Compilation c = CompileHelper.compileForNoModule("jts/modules/inherit1", folderName, tdsFilename, DUMP_FILES, 0, "TheParentType.java", "package-info.java", "sub1/Inherit1FirstClass.java", "sub2/Inherit1SecondChild.java");

        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+interface\\s+TheParentType\\s+\\{")).size(),
                "must have Type TheParentType"
        );
        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+interface\\s+Inherit1FirstClass\\s+extends\\s+TheParentType\\s+\\{")).size(),
                "must have Type Inherit1FirstClass"
        );
        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+interface\\s+Inherit1SecondChild\\s+extends\\s+Inherit1FirstClass\\s+\\{")).size(),
                "must have Type Inherit1SecondChild"
        );

        assertEquals(0,
                findSourceLine(c, folderName, tdsFilename, Pattern.compile("^\\s+export\\s+namespace")).size(),
                "must have not have namespaces");

        assertTrue(
                findSourceLine(c, folderName, tdsFilename, Pattern.compile("fromTheParent:\\s+string;")).size() >= 2,
                "must have fromTheParent: string"
        );
        assertTrue(
                findSourceLine(c, folderName, tdsFilename, Pattern.compile("inherit1FirstClass:\\s+string;")).size() >= 1,
                "must have inherit1FirstClass: string"
        );
        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("aNumber:\\s+number;")).size(),
                "must have a number"
        );
        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("someFinalString:\\s+string;")).size(),
                "must have a someFinalString: string"
        );

    }

    @Test
    void test_two_simple_interface_with_one_ignored_partially() throws IOException {
        Compilation c = CompileHelper.compileJtsDev(DUMP_FILES, 0, "InterFaceTest.java", "InterFaceTestWithOneIgnoredMethod.java");
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+export\\s+interface\\s+InterFaceTest\\s+\\{")).size(),
                "must have Type InterFaceTest"
        );
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+export\\s+interface\\s+InterFaceTestWithOneIgnoredMethod\\s*\\{")).size(),
                "must have Type InterFaceTestWithOneIgnoredMethod"
        );
        assertEquals(
                0,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("otherIntIgnored")).size(),
                "the member otherIntIgnored must not be included"
        );
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("otherStringNotIgnored:\\s+string;")).size(),
                "the member otherStringNotIgnored must be included"
        );
    }

    @Test
    void two_types() throws IOException {
        Compilation c = CompileHelper.compileJtsDev(DUMP_FILES, 0, "InterFaceTest.java", "MemberTestObject.java");
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+export\\s+interface\\s+MemberTestObject\\s*\\{")).size(),
                "must have Type MemberTestObject"
        );
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+export\\s+interface\\s+InterFaceTest\\s+\\{")).size(),
                "must have Type InterFaceTest"
        );
    }

    @Test
    void getter_with_array() throws IOException {
        Compilation c = CompileHelper.compileJtsDev(DUMP_FILES, 0, "MemberWithArrayGetter.java");
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+export\\s+interface\\s+MemberWithArrayGetter\\s*\\{")).size(),
                "must have Type MemberWithArrayGetter"
        );
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+returnTypeIsIntArray:\\s+number\\[];")).size(),
                "must have member number[]"
        );
    }

    @Test
    void test_container_types() throws IOException {
        Compilation c = CompileHelper.compileJtsDev(DUMP_FILES, 0, "MemberContainerTest.java");
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+export\\s+interface\\s+MemberContainerTest\\s*\\{")).size(),
                "must have Type MemberContainerTest"
        );
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("map_of_string_to_list_of_string:\\s+\\{\\s*\\[key:\\s*string]:\\s*string\\[];\\s*};")).size(),
                "java.util.Map must be mapped to { [key: string]: string[]; }"
        );
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("set_Of_Int:\\s+number\\[];")).size(),
                "set must be mapped to number[]"
        );
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("list_Of_String:\\s+string\\[];")).size(),
                "list be mapped to []"
        );
    }


    @Test
    void test_simple_class() throws IOException {
        Compilation c = CompileHelper.compileJtsDev(DUMP_FILES, 0, "MemberTestObject.java");

        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+readonly\\s+x_with_getter_only:\\s+number")).size(),
                "must be readonly"
        );
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS, Pattern.compile("^\\s+x_with_getter_setter:\\s+number")).size(),
                "the setter / getter is not readonly"
        );
        assertEquals(
                0,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("x_with_setter_only:\\s+number")).size(),
                "the setter must not be included"
        );
        assertEquals(
                0,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("member_private:\\s+number")).size(),
                "don't include the non public members"
        );
        assertEquals(
                0,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("member_protected:\\s+number")).size(),
                "don't include the non public members"
        );
        assertEquals(
                0,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("member_package_protected:\\s+number")).size(),
                "don't include the non public members"
        );
    }

    @Test
    void test_simple_enum() throws IOException {
        Compilation c = CompileHelper.compileJtsDev(DUMP_FILES, 0, "InterfaceWithEnum.java", "SomeEnum.java");

        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+export\\s+enum\\s+SomeEnum\\s*\\{")).size(),
                "must have Type SomeEnum"
        );
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+export\\s+interface\\s+InterfaceWithEnum\\s*\\{")).size(),
                "must have Type InterfaceWithEnum"
        );

        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+A, B, C\\s*$")).size(),
                "must include enum values"
        );
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+someEnum:\\s+SomeEnum;")).size(),
                "must have the member someEnum: SomeEnum"
        );

    }

    @Test
    void person_examplApi_test() throws Exception {
        final String folderName = "exampleapi";
        final String tdsFilename = "example-api.d.ts";
        Compilation c = CompileHelper.compileForNoModule("jts/modules/person", folderName, tdsFilename, DUMP_FILES, 0, "Item.java", "Order.java", "package-info.java", "Person.java", "Sex.java");

        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+enum\\s+Sex\\s*\\{")).size(),
                "must have Type Sex"
        );
        assertEquals(
                1
                , findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+interface\\s+Item\\s*\\{")).size(),
                "must have Type Item"
        );
        assertEquals(
                1
                , findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+interface\\s+Order\\s*\\{")).size(),
                "must have Type Order"
        );
        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+interface\\s+Person\\s*\\{")).size(),
                "must have Type Person"
        );

        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+price:\\s+number;")).size(),
                "must be mapped to number"
        );
        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+items:\\s+Item\\[];")).size(),
                "must be mapped to Item[]"
        );
        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+customer:\\s+Person;")).size(),
                "must be mapped to Person"
        );
        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+birthdate:\\s+string;")).size(),
                "must be mapped to string"
        );
        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+sex:\\s+Sex;")).size(),
                "must be mapped to Sex"
        );
    }

    @Test
    @DisplayName("checking inclusion of additional types")
    void test_default_additionalTypes() throws IOException {
        InetAddress a;
        final String folderName = "additional_test";
        final String tdsFilename = "additional_test.d.ts";
        Compilation c = CompileHelper.compileForModule("jts/modules/additional", folderName, tdsFilename, DUMP_FILES, 0,  "package-info.java");

        assertEquals(
                1,
                countPatterns(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+interface\\s+InetAddress\\s*\\{")),
                "must have Type java.net.InternetAdress"
        );

        assertEquals(
                1,
                countPatterns(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+interface")),
                "must converted only one type."
        );
    }

    @Test
    @DisplayName("checking inclusion of additional types besides regular TypeScript annotation")
    void test_default_additionalTypes_Regular() throws IOException {
        InetAddress a;
        final String folderName = "additional2_test";
        final String tdsFilename = "additional2_test.d.ts";
        Compilation c = CompileHelper.compileForModule("jts/modules/additional2", folderName, tdsFilename, DUMP_FILES, 0,  "package-info.java", "InterFaceTestAdditional2.java");

        assertEquals(
                1,
                countPatterns(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+interface\\s+InetAddress\\s*\\{")),
                "must have Type java.net.InetAddress"
        );

        assertEquals(
                1,
                countPatterns(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+interface\\s+InterFaceTestAdditional2\\s*\\{")),
                "must have Type InterFaceTestAdditional2"
        );
    }

    @Test
    void test_default_exclusion() throws IOException {
        Compilation c = CompileHelper.compileJtsDev(DUMP_FILES, 1, "InterFaceTestWithSunInternal.java");

        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+export\\s+interface\\s+InterFaceTestWithSunInternal\\s*\\{")).size(),
                "must have Type InterfaceWithEnum"
        );
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+\\s+mustBeExcluded:\\s+any;")).size(),
                "must be mapped to any"
        );

        assertEquals(
                0,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+export\\s+interface\\s+Version\\s*\\{")).size(),
                "must not have the type Version included"
        );
        assertEquals(
                0,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("jdkSpecialVersion:\\s+string;")).size(),
                "must not have member from Version"
        );
    }

    // Tests no generation because of exclusion
    @Test
    void test_custom_exclusion() throws IOException {
        final String folderName = "exclusion_test";
        final String tdsFilename = "exclusion_test.d.ts";
        Compilation c = CompileHelper.compileForModule("jts/modules/exclude", folderName, tdsFilename, DUMP_FILES, 0, "InterFaceTestSelfExclusion.java", "package-info.java");

        assertEquals(
                0,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+interface\\s+InterFaceTestSelfExclusion\\s*\\{")).size(),
                "must have Type InterfaceWithEnum"
        );
    }

    // Tests no generation because of exclusion
    @Test
    void test_nsmap_manual() throws IOException {
        final String folderName = "nsmanual";
        final String tdsFilename = "nsmanual.d.ts";
        Compilation c = CompileHelper.compileForModule("jts/modules/nsmanual", folderName, tdsFilename, DUMP_FILES, 0, "InterFaceNSManual.java", "package-info.java");

        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+interface\\s+InterFaceNSManual\\s*\\{")).size(),
                "must have Type InterFaceNSManual"
        );
        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+namespace\\s+jts\\s*\\{")).size(),
                "must have namespace jts"
        );
        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+namespace\\s+modules\\s*\\{")).size(),
                "must have namespace modules"
        );
        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+namespace\\s+nsmanual\\s*\\{")).size(),
                "must have namespace manual"
        );
    }


    @Test
    void test_custom_namespace_map() throws IOException {
        final String folderName = "namespace_test";
        final String tdsFilename = "namespace_test.d.ts";
        Compilation c = CompileHelper.compileForModule("jts/modules/nsmap", folderName, tdsFilename, DUMP_FILES, 0, "InterFaceTestNameSpaceMapped.java", "package-info.java");

        assertEquals(
                1,
                countPatterns(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+interface\\s+InterFaceTestNameSpaceMapped\\s*\\{")),
                "must have Type InterFaceTestNameSpaceMapped"
        );
        assertEquals(
                1,
                countPatterns(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+namespace\\s+easy")),
                "must not have name space easy"
        );
    }

    @Test
    @DisplayName("prefix_get_bool: Custom member prefix with getX and hasX")
    void test_memberPrefixFilter() throws IOException {
        final String folderName = "prefix_get_bool";
        final String tdsFilename = "prefix_get_bool.d.ts";
        Compilation c = CompileHelper.compileForModule("jts/modules/prefix_get_bool",
                folderName, tdsFilename, true, 0,
                "package-info.java", "InterfacePrefixGetBool.java");

        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+interface\\s+InterfacePrefixGetBool\\s*\\{")).size(),
                "must have Type InterfacePrefixGetBool"
        );

        assertEquals(
                0,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s*isBoolean:\\s*boolean\\s*;")).size(),
                "must not have isBoolean, because of getterPrefix"
        );

        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s*getBoolean:\\s*boolean\\s*;")).size(),
                "must have getBoolean, because of getterPrefix"
        );

        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s*hasBoolean:\\s*boolean\\s*;")).size(),
                "must have hasBoolean, because of getterPrefix"
        );


    }

    @Test
    void test_namespace_clash() throws IOException {
        final String folderName = "unknown";
        final String tdsFilename = "unknown.d.ts";
        Compilation c = CompileHelper.compileForModule("jts/modules/nsmap_clash", folderName, tdsFilename, DUMP_FILES, 1, "a/InterFaceClash.java", "b/InterFaceClash.java");

        assertEquals(
                2,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+interface\\s+InterFaceClash\\s*\\{")).size(),
                "must have Type InterFaceClash twice"
        );
        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+namespace\\s+a\\s*\\{")).size(),
                "must have namespace a"
        );
        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+namespace\\s+b\\s*\\{")).size(),
                "must have namespace b"
        );
    }

    @Test
    void test_MemberDefaultMappingTest() throws IOException {
        Compilation c = CompileHelper.compileJtsDev(DUMP_FILES, 0, "MemberDefaultMappingTest.java");

        ReferenceHelper.assertEquals(

                c.generatedFile(StandardLocation.SOURCE_OUTPUT, JTS_DEV, JTS_DEV_D_TS).orElseThrow(FileNotFoundException::new)
                , "default_type_mappings.d.ts");

    }


    /**
     * Two types in different packages
     * - has NS mapped
     * - module name is unknown: two top level packages
     */
    @Test
    void test_namespace_noclash() throws IOException {
        final String folderName = "unknown";
        final String tdsFilename = "unknown.d.ts";
        Compilation c = CompileHelper.compileForModule("jts/modules/nsmap_noclash", folderName, tdsFilename, DUMP_FILES, 1, "a/InterFaceNoClashA.java", "b/InterFaceNoClashB.java");

        assertEquals(
                1,
                countPatterns(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+interface\\s+InterFaceNoClashA\\s*\\{")),
                "must have Type InterFaceNoClashA"
        );
        assertEquals(
                1,
                countPatterns(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+interface\\s+InterFaceNoClashB\\s*\\{")),
                "must have Type InterFaceNoClashB"
        );

        assertEquals(
                0,
                countPatterns(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+namespace")),
                "must not have namespace"
        );
    }

    @Test
    void test_simple_date() throws IOException {
        Compilation c = CompileHelper.compileJtsDev(DUMP_FILES, 0, "InterfaceWithDate.java");
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+export\\s+interface\\s+InterfaceWithDate\\s*\\{")).size(),
                "must have Type InterfaceWithDate"
        );
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+export\\s+interface\\s+Date\\s*\\{")).size(),
                "must have Type Date"
        );

        // namespave java.util
        assertEquals(
                0,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+export\\s+namespace\\s+java\\s*\\{")).size(),
                "must not have namespace java"
        );
        assertEquals(
                0,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+export\\s+namespace\\s+util\\s*\\{")).size(),
                "must not have namespace util"
        );

        // must have some Date attributes
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+hours:\\s*number;")).size(),
                "must attribute hours: number;"
        );
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+seconds:\\s*number;")).size(),
                "must attribute seconds: number;"
        );
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+month:\\s*number;")).size(),
                "must attribute month: number;"
        );
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+year:\\s*number;")).size(),
                "must attribute year: number;"
        );
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+minutes:\\s*number;")).size(),
                "must attribute minutes: number;"
        );

        // must be mapped to java.util.Date
        assertEquals(
                1,
                findSourceLine(c, JTS_DEV, JTS_DEV_D_TS,
                        Pattern.compile("^\\s+someDate:\\s+Date;")).size(),
                "must be mapped to Date"
        );
    }

    @Test
    void test_output_type_d_ts_only() throws IOException {
        final String folderName = "no_module";
        final String tdsFilename = "no_module.d.ts";
        Compilation c = CompileHelper.compileForNoModule("jts/modules/outputNoModule", folderName, tdsFilename, DUMP_FILES, 0, "InterFaceTestNameSpaceMapped.java", "package-info.java");
        assertFalse(c.generatedFile(StandardLocation.SOURCE_OUTPUT, folderName, PACKAGE_JSON).isPresent(),
                     "must not contain package.json");
        assertEquals(
                1,
                countPatterns(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+interface\\s+InterFaceTestNameSpaceMapped\\s*\\{")),
                "must have Type InterFaceTestNameSpaceMapped"
        );
    }

    @Test
    void members_with_module_definitions() throws IOException {
        final String folderName = "testm1";
        final String tdsFilename = "test-m1.d.ts";
        Compilation c = CompileHelper.compileForModule("jts/modules/testM1", folderName, tdsFilename, DUMP_FILES, 0, "MemberWithModuleDef.java", "package-info.java", "m2/InterFaceTestModuleM2MustBeIn.java");
        assertEquals(
                1,
                findSourceLine(c, folderName, PACKAGE_JSON,
                        Pattern.compile("^\\s+\"author\":\\s+\"Me Myself And I\"")).size(),
                "must have author Me Myself And I"
        );
        assertEquals(
                1,
                findSourceLine(c, folderName, PACKAGE_JSON,
                        Pattern.compile("^\\s+\"authorUrl\":\\s+\"SomeAuthorUrl\"")).size(),
                "must have authorUrl some authorUrl"
        );
        assertEquals(
                1,
                findSourceLine(c, folderName, PACKAGE_JSON,
                        Pattern.compile("^\\s+\"description\":\\s+\"some description\"")).size(),
                "must have description some description"
        );
        assertEquals(
                1,
                findSourceLine(c, folderName, PACKAGE_JSON,
                        Pattern.compile("^\\s+\"license\":\\s+\"some license\"")).size(),
                "must have license some license"
        );

        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+interface\\s+MemberWithModuleDef\\s*\\{")).size(),
                "must have Type MemberWithModuleDef"
        );
        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+date_string:\\s+string;")).size(),
                "java.util.Date must be converted to string"
        );
        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+mustBeIn:\\s+m2.InterFaceTestModuleM2MustBeIn;")).size(),
                "must have m2 interface"
        );

        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+namespace\\s+m2\\s*\\{")).size(),
                "must have namespace m2"
        );
    }

    @Test
    void test_output_type_no_module() throws IOException {
        final String folderName = "simple_file";
        final String tdsFilename = "simple_file.ts";
        Compilation c = CompileHelper.compileForNoModule("jts/modules/outputSimpleFile", folderName, tdsFilename, DUMP_FILES, 0, "InterFaceTestSimpleFile.java", "package-info.java");

        ReferenceHelper.assertEquals(

                c.generatedFile(StandardLocation.SOURCE_OUTPUT, folderName, tdsFilename).orElseThrow(FileNotFoundException::new)
                , "no_module.simple_file.ts");
    }

    @Test
    void test_type_guard1_no_module() throws IOException {
        final String folderName = "type_guards1";
        final String tdsFilename = "type_guards1.ts";
        Compilation c = CompileHelper.compileForNoModule("jts/modules/type_guards1", folderName, tdsFilename, DUMP_FILES, 0, "InterFaceTestTypeGuard1.java", "package-info.java");

        assertEquals(
                1,
                countPatterns(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+interface\\s+InterFaceTestTypeGuard1\\s*\\{")),
                "must have Type InterFaceTestTypeGuard1"
        );
        assertEquals(
                1,
                countPatterns(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+function\\s+instanceOfInterFaceTestTypeGuard1.*x\\s+is\\s+InterFaceTestTypeGuard1")),
                "must have a typeguard function"
        );
    }

    @Test
    // Test Typeguards in inheritance
    void test_type_guard2_no_module() throws IOException {
        final String folderName = "type_guards2";
        final String tdsFilename = "type_guards2.ts";
        Compilation c = CompileHelper.compileForNoModule("jts/modules/type_guards2", folderName, tdsFilename, DUMP_FILES, 0, "InterFaceTestTypeGuard2.java", "package-info.java");

        assertEquals(
                1,
                countPatterns(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+interface\\s+InterFaceTestTypeGuard2\\s+extends\\s+SomeParentForTypeGuardTest2\\s*\\{")),
                "must have Type InterFaceTestTypeGuard2"
        );
        assertEquals(
                1,
                countPatterns(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+interface\\s+SomeParentForTypeGuardTest2\\s*\\{")),
                "must have Type SomeParentForTypeGuardTest2"
        );
        assertEquals(
                1,
                countPatterns(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+function\\s+instanceOfInterFaceTestTypeGuard2.*x\\s+is\\s+InterFaceTestTypeGuard2")),
                "must have a typeguard functionfor first Type"
        );
        assertEquals(
                1,
                countPatterns(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+export\\s+function\\s+instanceOfSomeParentForTypeGuardTest2.*x\\s+is\\s+SomeParentForTypeGuardTest2")),
                "must have a typeguard function for second type"
        );

        assertEquals(
                1,
                countPatterns(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+arrayFromParent:\\s+number\\[]")),
                "must contain arrayFromParent: number[]"
        );

        //this addional check ust be included in the typeguard of InterFaceTestTypeGuard2
        assertEquals(
                1,
                countPatterns(c, folderName, tdsFilename,
                        Pattern.compile("^\\s+instanceOfSomeParentForTypeGuardTest2\\(x\\)")),
                "typeguard must call other typeguard ue to inheritance"
        );
    }

    @Test
    void test_output_type_external_module() throws IOException {
        final String folderName = "external_module";
        final String tdsFilename = "external_module.ts";
        Compilation c = CompileHelper.compileForModule("jts/modules/outputExternalModule", folderName, tdsFilename, DUMP_FILES, 0, "InterFaceTestExportModule.java", "package-info.java");

        assertEquals(
                1,
                findSourceLine(c, folderName, PACKAGE_JSON,
                        Pattern.compile("^\\s+\"main\":\\s+\"external_module.ts\"")).size(),
                "must have module defined in main"
        );
        assertEquals(
                1,
                findSourceLine(c, folderName, PACKAGE_JSON,
                        Pattern.compile("^\\s+\"typings\":\\s+\"\"")).size(),
                "must not have module defined as ambient type"
        );
        assertEquals(
                1,
                findSourceLine(c, folderName, tdsFilename,
                        Pattern.compile("^\\s*declare\\s+module\\s+\"external_module\"\\s+\\{")).size(),
                "must be a module"
        );
    }

}