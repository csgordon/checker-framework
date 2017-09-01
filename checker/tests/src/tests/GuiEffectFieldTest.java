package tests;

import java.io.File;
import java.util.List;
import org.checkerframework.framework.test.CheckerFrameworkPerDirectoryTest;
import org.junit.runners.Parameterized.Parameters;

public class GuiEffectFieldTest extends CheckerFrameworkPerDirectoryTest {

    public GuiEffectFieldTest(List<File> testFiles) {
        super(
                testFiles,
                org.checkerframework.checker.guieffect.GuiEffectChecker.class,
                "guieffect",
                "-AflagUIfields",
                "-Anomsgtext");
        //, "-Alint=debugSpew");
    }

    @Parameters
    public static String[] getTestDirs() {
        return new String[] {"guieffect-fields"}; // TODO: re-enable, "guieffect", "all-systems"};
    }
}
