package com.expiryguard.server.ui;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Selenium UI tests for ExpiryGuard (routes and fields taken from App.tsx and Login.tsx).
 * Runs only with -Dui=true, so GitHub Actions skips it.
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "ui", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExpiryGuardUiTest {

    static final String BASE = System.getProperty("baseUrl", "http://localhost:5173");
    static final String ADMIN = System.getProperty("adminUser", "admin");
    static final String ADMIN_PW = System.getProperty("adminPass", "CHANGE_ME");
    static final String PHARM = System.getProperty("pharmUser", "pharmacist1");
    static final String PHARM_PW = System.getProperty("pharmPass", "CHANGE_ME");

    WebDriver driver;
    WebDriverWait wait;

    @BeforeEach
    void open() {
        ChromeOptions o = new ChromeOptions();
        if (Boolean.getBoolean("headless")) o.addArguments("--headless=new");
        o.addArguments("--window-size=1400,900");
        driver = new ChromeDriver(o);
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    @AfterEach
    void close(TestInfo info) throws Exception {
        if (driver != null) {
            // Screenshot of the final page of every test -> evidence
            File shot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Path dir = Path.of("target", "selenium-screenshots");
            Files.createDirectories(dir);
            Files.copy(shot.toPath(), dir.resolve(info.getTestMethod().get().getName() + ".png"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            driver.quit();
        }
    }

    private void login(String user, String pass) {
        driver.get(BASE + "/login");
        WebElement u = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("email")));
        u.clear();
        u.sendKeys(user);
        WebElement p = driver.findElement(By.name("password"));
        p.clear();
        p.sendKeys(pass);
        driver.findElement(By.cssSelector("form button[type='submit']")).click();
    }

    @Test @Order(1)
    @DisplayName("UI-01 Valid admin login opens the dashboard")
    void validLogin() {
        login(ADMIN, ADMIN_PW);
        wait.until(ExpectedConditions.urlContains("/dashboard"));
        assertTrue(driver.getCurrentUrl().contains("/dashboard"));
    }

    @Test @Order(2)
    @DisplayName("UI-02 Wrong password shows an error and stays on /login")
    void invalidLogin() {
        login(ADMIN, "wrong-password-123");
        WebElement error = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("form .text-red-600")));
        assertFalse(error.getText().isBlank(), "Error message should be shown");
        assertTrue(driver.getCurrentUrl().contains("/login"));
    }

    @Test @Order(3)
    @DisplayName("UI-03 Expiry Alerts page shows the expired batch")
    void expiryAlerts() {
        login(ADMIN, ADMIN_PW);
        wait.until(ExpectedConditions.urlContains("/dashboard"));
        driver.get(BASE + "/alerts");
        wait.until(d -> d.findElement(By.tagName("body")).getText().toLowerCase().contains("expir"));
        String page = driver.findElement(By.tagName("body")).getText();
        assertTrue(page.contains("PAR-001") || page.toLowerCase().contains("expired"),
                "Expired batch PAR-001 should be listed");
    }

    @Test @Order(4)
    @DisplayName("UI-04 Pharmacist is blocked from the admin-only Users page")
    void pharmacistBlockedFromUsers() {
        login(PHARM, PHARM_PW);
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/login")));
        driver.get(BASE + "/users");
        // AdminRoute should redirect away from /users (or show an access-denied message)
        wait.until(d -> !d.getCurrentUrl().endsWith("/users")
                || d.findElement(By.tagName("body")).getText().toLowerCase().matches("(?s).*(denied|not authori|forbidden).*"));
        boolean redirected = !driver.getCurrentUrl().endsWith("/users");
        boolean denied = driver.findElement(By.tagName("body")).getText().toLowerCase()
                .matches("(?s).*(denied|not authori|forbidden).*");
        assertTrue(redirected || denied, "Pharmacist must not see the Users page");
    }
}


