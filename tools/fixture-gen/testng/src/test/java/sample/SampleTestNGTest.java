package sample;

import org.testng.Assert;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SampleTestNGTest {

    private static int flakyCount = 0;

    public static class FlakyRetry implements IRetryAnalyzer {
        private int count = 0;
        @Override
        public boolean retry(ITestResult result) {
            return count++ < 1;
        }
    }

    @Test
    public void testPassing() {
        Assert.assertEquals(1 + 1, 2);
    }

    @Test
    public void testFailing() {
        Assert.fail("Assertion failure: expected true but was false");
    }

    @Test
    public void testErroring() {
        throw new RuntimeException("Unexpected runtime error");
    }

    @Test(enabled = false)
    public void testSkipped() {
        // Skipped
    }

    @Test(retryAnalyzer = FlakyRetry.class)
    public void testFlaky() {
        if (flakyCount++ == 0) {
            Assert.fail("Flaky failure on first attempt");
        }
    }

    @DataProvider(name = "params")
    public Object[][] provideParams() {
        return new Object[][] {
            {"alpha", true},
            {"beta", false}
        };
    }

    @Test(dataProvider = "params")
    public void testParameterized(String name, boolean pass) {
        if (!pass) {
            Assert.fail("Parameterized failure on " + name);
        }
    }
}
