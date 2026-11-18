package com.expiryguard.server.ui;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Selenium UI tests. Requires the app running (docker compose up) at http://localhost:8080.
 * Run only these:  .\mvnw.cmd test -Dtest=ExpiryGuardUiTest -Dui=true
 * Adjust the By locators to match the real input names/ids in Login.tsx etc.
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "ui", matches = "true")
class ExpiryGuardUiTest {

    static final String BASE = System.getProperty("baseUrl", "http://localhost:8080");
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
        driver = new ChromeDriver(o);
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    @AfterEach
    void close() { if (driver != null) driver.quit(); }

    void login(String u, String p) {
        driver.get(BASE + "/login");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("input[type='text'], input[name='username']"))).sendKeys(u);
        driver.findElement(By.cssSelector("input[type='password']")).sendKeys(p);
        driver.findElement(By.cssSelector("button[type='submit']")).click();
    }

    @Test @DisplayName("UI-01 Valid admin login reaches dashboard")
    void validLogin() {
        login(ADMIN, ADMIN_PW);
        wait.until(ExpectedConditions.urlContains("/dashboard"));
        assertTrue(driver.getCurrentUrl().contains("/dashboard"));
    }

    @Test @DisplayName("UI-02 Wrong password shows an error and stays on login")
    void invalidLogin() {
        login(ADMIN, "wrong-password");
        wait.until(d -> d.getPageSource().toLowerCase().matches("(?s).*(invalid|incorrect|failed).*"));
        assertTrue(driver.getCurrentUrl().contains("/login"));
    }

    @Test @DisplayName("UI-03 Expiry alerts page lists alerts")
    void expiryAlerts() {
        login(ADMIN, ADMIN_PW);
        wait.until(ExpectedConditions.urlContains("/dashboard"));
        driver.get(BASE + "/expiry-alerts");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("main")));
        assertTrue(driver.getPageSource().toLowerCase().contains("expir"));
    }

    @Test @DisplayName("UI-04 Pharmacist cannot open Users (admin-only) page")
    void pharmacistBlocked() {
        login(PHARM, PHARM_PW);
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/login")));
        driver.get(BASE + "/users");
        wait.until(d -> !d.getCurrentUrl().endsWith("/users") || d.getPageSource().toLowerCase().contains("denied"));
        assertFalse(driver.getCurrentUrl().endsWith("/users") && !driver.getPageSource().toLowerCase().contains("denied"));
    }
}
